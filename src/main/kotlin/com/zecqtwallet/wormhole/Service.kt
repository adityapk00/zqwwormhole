package com.zecqtwallet.wormhole

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.beust.klaxon.json
import io.javalin.Javalin
import io.javalin.websocket.WsSession
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap


private val usermap = ConcurrentHashMap<WsSession, String>()

// Allow maps to be bidirectional
fun <K, V> Map<K, V>.getKeys(value: V) : List<K> =
    entries.filter { it.value == value } .map { it.key }

val LOG = LoggerFactory.getLogger("Websocket")

fun main(args : Array<String>) {

    Javalin.create().apply {
        ws("/") { ws ->
            ws.onConnect { session ->
                LOG.info("Connected Session")
                session.idleTimeout = 5 * 60 * 1000 // 5 minutes
            }

            ws.onClose { session, _, _ ->
                LOG.info("Closed session ${usermap[session]}")
                usermap.remove(session)
            }

            ws.onMessage { session, message ->
                // Limit message size to 50kb of hex encoded text
                if (message.length > 2 * 50 * 1024) {
                    sendError(session, "Message too big")
                    return@onMessage
                }

                //println("Recieved $message")

                // Parse the message as json
                try {
                    val j = Parser.default().parse(StringBuilder(message)) as JsonObject

                    if (j.contains("ping")) {
                        // Ignore, this is a keep-alive ping
                        // Just send the ping back
                        session.send(message)
                        return@onMessage
                    }

                    if (j.contains("register")) {
                        logInfo("Register ${j["register"].toString()}", j)
                        doRegister(session, j["register"].toString())
                        return@onMessage
                    }

                    if (j.contains("to")) {
                        val s = usermap.getKeys(j["to"].toString()).filter { it.id != session.id }
                        if (s.isEmpty()) {
                            // Not connected
                            logInfo("Error: Peer is not connected", j)
                            sendError(session, "Peer is not connected")
                            return@onMessage
                        }

                        if (s.size > 2) {
                            LOG.warn("Warning, multiple sessions matched for ${j["to"].toString()}")
                        }

                        logInfo("Routed message for ${j["to"].toString()}", j)
                        s[0].send(message)
                        return@onMessage
                    } else {
                        LOG.warn("There was no 'to' in the message: $message")
                        sendError(session,"Missing 'to' field")
                        return@onMessage
                    }
                } catch (e: Throwable) {
                    LOG.error("Exception: ${e.localizedMessage}, Message was: $message")
                    session.close(1000, "Invalid json")
                }
            }
            
            ws.onError { session, t ->
                LOG.error("Something went wrong with session ${t.toString()}")
                usermap.remove(session)
            }
        }
    }.start(7070)

}

fun doRegister(session: WsSession, id: String) {
    if (usermap.containsKey(session)) {
        LOG.warn("Already registered a session $id")
        return
    }

    usermap[session] = id
}

fun sendError(session: WsSession, err: String) {
    if (session.isOpen) {
        session.send(json { obj("error" to err) }.toJsonString())
    }
}

fun logInfo(t: String, j: JsonObject) {
    val l = j.map {
        val s = it.value.toString()
        it.key to if (s.length > 10) s.take(10) + "...{" + s.length + "}" else s
    }.toString().replace("\n", "").trim()

    LOG.info(t + l)
}