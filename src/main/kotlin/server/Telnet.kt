package com.dlfsystems.server

import com.dlfsystems.app.Log
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

object Telnet {

    private var job: Job? = null

    // Separate thread, to avoid blocking MCP on client buffer flushes (and vice versa)
    private val scope = CoroutineScope(
        SupervisorJob() +
                Dispatchers.IO.limitedParallelism(1) +
                CoroutineName("Yegg telnet")
    )

    fun start() {
        if (job?.isActive == true) throw IllegalStateException("Already started")
        job = Yegg.launch { server() }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun server() {
        val serverSocket = aSocket(SelectorManager(Dispatchers.IO))
            .tcp().bind(Yegg.serverAddress, Yegg.serverPort)
        Log.i("Server listening at ${serverSocket.localAddress}:${serverSocket.port}")

        while (true) {

            val client = serverSocket.accept()
            Log.i("Accepted client socket: $client")

            scope.launch {
                val receive = client.openReadChannel()
                val send = client.openWriteChannel(autoFlush = true)

                val conn = Connection {
                    // TODO: Do better, don't launch for every send.  This is inefficient and doesn't(?) guarantee delivery order.
                    scope.launch {
                        send.writeStringUtf8("${it.replace("\n", "\r\n")}\r\n")
                    }
                }
                Yegg.addConnection(conn)

                try {
                    while (true) {
                        val input = receive.readUTF8Line() ?: break
                        conn.receiveText(input)
                    }
                    Log.i("Closing client socket: $client")
                } catch (e: Throwable) {
                    Log.i("Connection error on $client: $e")
                }

                client.close()
                Yegg.removeConnection(conn)
            }

        }
    }

}
