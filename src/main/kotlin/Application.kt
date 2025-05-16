package com.dlfsystems

import com.dlfsystems.app.configureRouting
import com.dlfsystems.server.Yegg
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {

    Yegg.start()
    configureRouting()

}
