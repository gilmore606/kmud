package com.dlfsystems.app

import com.dlfsystems.server.Yegg
import java.io.BufferedWriter
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Log {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    private val timestamp: String
        get() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))

    private var writer: BufferedWriter? = null

    fun d(m: String) { log(Level.DEBUG, m) }
    fun i(m: String) { log(Level.INFO, m) }
    fun w(m: String) { log(Level.WARN, m) }
    fun e(m: String) { log(Level.ERROR, m) }

    fun start(filename: String) {
        File("$filename.log.old").delete()
        File("$filename.log").renameTo(File("$filename.log.old"))
        writer = File("$filename.log").bufferedWriter()
    }

    fun stop() {
        writer?.close()
    }

    private fun log(level: Level, m: String) {
        if (level >= Yegg.logLevel) {
            if (Yegg.logToConsole) println("$level: $m")
            writer?.write("$timestamp $level: $m\n")
        }
    }

}
