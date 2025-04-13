package com.dlfsystems.server

import com.dlfsystems.app.Log
import com.dlfsystems.world.World
import com.dlfsystems.value.VObj
import com.dlfsystems.value.VTrait
import com.dlfsystems.world.Obj
import io.viascom.nanoid.NanoId
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

object Yegg {

    var logLevel = Log.Level.DEBUG
    var worldName = "world"

    private const val CONNECT_MSG = "** Connected **"
    private const val DISCONNECT_MSG = "** Disconnected **"
    const val HUH_MSG = "I don't understand that."

    val vNullObj = VObj(null)
    val vNullTrait = VTrait(null)

    lateinit var world: World

    private val connections = mutableSetOf<Connection>()
    val connectedUsers = mutableMapOf<Obj, Connection>()

    fun newID() = NanoId.generateOptimized(8, "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 61, 16)

    fun start() {
        val file = File("$worldName.yegg")
        if (file.exists()) {
            Log.i("Loading database from ${file.path}...")
            try {
                // Deserialize the world
                world = Json.decodeFromString<World>(file.readText())
                Log.i("Loaded ${world.name} with ${world.traits.size} traits and ${world.objs.size} objs.")
            } catch (e: Exception) {
                Log.e("FATAL: Failed to load from ${file.path} !")
                exitProcess(1)
            }
        } else {
            Log.i("No database $worldName found, initializing new world.")
            world = World(worldName).apply {
                addTrait("sys")
                addTrait("user")
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

    fun dumpDatabase(): String? {
        val file = File("${world.name}.yegg")
        Log.i("Dumping database...")
        try {
            file.writeText(Json.encodeToString(world))
        } catch (e: Exception) {
            return "ERROR: ${e.message}"
        }
        return null
    }

}
