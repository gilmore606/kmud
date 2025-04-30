package com.dlfsystems.server

import com.dlfsystems.app.Log
import com.dlfsystems.value.*
import com.dlfsystems.world.World
import com.dlfsystems.world.Obj
import io.viascom.nanoid.NanoId
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

object Yegg {

    var worldName = "Minimal"
    var logLevel = Log.Level.DEBUG
    private val JSON = Json { allowStructuredMapKeys = true }

    private const val CONNECT_MSG = "** Connected **"
    private const val DISCONNECT_MSG = "** Disconnected **"
    const val HUH_MSG = "I don't understand that."

    val vTrue = VBool(true)
    val vFalse = VBool(false)
    val vNullObj = VObj(null)
    val vNullTrait = VTrait(null)
    val vZero = VInt(0)
    val vNullStr = VString("")

    lateinit var world: World

    private val connections = mutableSetOf<Connection>()
    val connectedUsers = mutableMapOf<Obj, Connection>()

    var serializeWithCode = true

    fun newID() = NanoId.generateOptimized(8, "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 61, 16)

    fun start() {
        val file = File("$worldName.yegg")
        if (file.exists()) {
            Log.i("Loading database from ${file.path}...")
            try {
                // Deserialize the world
                world = JSON.decodeFromString<World>(file.readText())
                Log.i("Loaded ${world.name}.")
            } catch (e: Exception) {
                Log.e("FATAL: Failed to load from ${file.path} !")
                exitProcess(1)
            }
        } else {
            Log.i("No database $worldName found, initializing new world.")
            world = World(worldName).apply {
                addTrait("sys").apply {
                    props["tickLimit"] = VInt(100000)
                    props["stackLimit"] = VInt(100)
                    props["callLimit"] = VInt(50)
                }
                addTrait("user").apply {
                    props["username"] = VString("")
                    props["password"] = VString("")
                }
            }
        }
    }

    fun addConnection(conn: Connection) {
        connections.add(conn)
        conn.sendText(world.getSysValue("loginBanner").asString())
    }

    fun connectUser(user: Obj, conn: Connection) {
        conn.user = user
        connectedUsers[user] = conn
        conn.sendText(CONNECT_MSG)
        Log.i("User $user connected")
    }

    fun removeConnection(conn: Connection) {
        conn.sendText(DISCONNECT_MSG)
        connections.remove(conn)
        connectedUsers.remove(conn.user)
        Log.i("User ${conn.user} disconnected")
    }

    fun notifyUser(user: Obj?, text: String) {
        connectedUsers[user]?.sendText(text)
    }

    fun notifyConn(connID: String, text: String) {
        connections.firstOrNull { it.id.id == connID }?.sendText(text)
    }

    fun dumpDatabase(withCode: Boolean = true): String {
        serializeWithCode = withCode
        val file = File("${world.name}.yegg")
        Log.i(if (withCode) "Dumping database..." else "Dumping database (without compiled code)...")
        try {
            file.writeText(JSON.encodeToString(world))
        } catch (e: Exception) {
            return "ERROR: ${e.message}"
        }
        return ""
    }

    fun shutdownServer() {
        exitProcess(0)
    }
}
