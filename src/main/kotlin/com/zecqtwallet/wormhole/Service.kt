package com.zecqtwallet.wormhole

import io.javalin.Javalin


fun main(args : Array<String>) {

    Javalin.create().apply {
        ws("/") { ws ->
            ws.onConnect { session ->
                println("Connected Session")
            }
            ws.onClose { session, status, message ->
                println("Closed session")
            }
            ws.onMessage { session, message ->
                println("message")
                session.send("Reply to $message")
            }
        }
    }.start(7070)

}
