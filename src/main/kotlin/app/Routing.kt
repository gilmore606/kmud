package com.dlfsystems.app

import com.dlfsystems.server.Yegg
import com.dlfsystems.server.Connection
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

fun Application.configureRouting() {

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {

        webSocket("/ws") {
            val conn = Connection { launch { outgoing.send(Frame.Text(it)) } }
            Yegg.addConnection(conn)
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    conn.receiveText(text)
                    if (conn.quitRequested) {
                        Yegg.removeConnection(conn)
                        close(CloseReason(CloseReason.Codes.NORMAL, "Client requested close"))
                    }
                }
            }
            Yegg.removeConnection(conn)
        }

        post("/program/{traitName}/{verbName}")  {
            val code = call.receiveText()
            var result = ""
            call.parameters["traitName"]?.also { traitName ->
                call.parameters["verbName"]?.also { verbName ->
                    result = Yegg.world.programVerb(traitName, verbName, code)
                }
            }
            call.respondText(result)
        }

    }
}
