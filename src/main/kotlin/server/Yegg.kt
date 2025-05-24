package com.dlfsystems.server

import com.dlfsystems.server.mcp.MCP
import com.dlfsystems.server.parser.Connection
import com.dlfsystems.value.*
import com.dlfsystems.world.World
import com.dlfsystems.world.Obj
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

object Yegg {

    @Serializable
    class Conf(
        val worldName: String,
        val serverAddress: String,
        val serverPort: Int,
        val logLevel: Log.Level,
        val logToConsole: Boolean,
        val optimizeCompiler: Boolean,
    )
    private const val CONFIG_PATH = "yegg.json"

    private const val CONNECT_MSG = "** Connected **"
    private const val DISCONNECT_MSG = "** Disconnected **"
    const val HUH_MSG = "I don't understand that."

    private val JSON = Json { allowStructuredMapKeys = true }

    val vTrue = VBool(true)
    val vFalse = VBool(false)
    val vNullObj = VObj(null)
    val vNullTrait = VTrait(null)
    val vZero = VInt(0)
    val vEmptyStr = VString("")
    val vEmptyList = VList.make(emptyList())
    val vEmptyMap = VMap.make(emptyMap())

    lateinit var conf: Conf
    lateinit var world: World

    private val connections = mutableSetOf<Connection>()
    val connectedUsers = mutableMapOf<Obj, Connection>()

    private val coroutineScope = CoroutineScope(
        SupervisorJob() +
                Dispatchers.Default.limitedParallelism(1) +
                CoroutineName("Yegg")
    )

    fun launch(block: suspend CoroutineScope.() -> Unit) = coroutineScope.launch(block = block)

    suspend fun onThread(block: suspend CoroutineScope.() -> Unit) =
        withContext(coroutineScope.coroutineContext, block)


    // Start the server.
    fun start() {
        println("Loading from ${System.getProperty("user.dir")}")
        launch {
            conf = JSON.decodeFromString(File(CONFIG_PATH).readText(Charsets.UTF_8))
            Log.start(conf.worldName)
            loadWorld()
            MCP.start()
            Telnet.start()
            Log.i("Server started.")
        }
    }

    // Shut down the server.
    fun shutdownServer() {
        Log.i("Server shutting down.")
        Telnet.stop()
        MCP.stop()
        Log.stop()
        exitProcess(0)
    }

    private fun loadWorld() {
        val worldName = conf.worldName
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
                    addProp("tickLimit", VInt(100000))
                    addProp("stackLimit", VInt(100))
                    addProp("callLimit", VInt(50))
                }
                addTrait("user").apply {
                    addProp("username", VString(""))
                    addProp("password", VString(""))
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

    fun dumpDatabase(): String {
        val file = File("${world.name}.yegg")
        Log.i("Dumping database...")
        try {
            file.writeText(JSON.encodeToString(world))
        } catch (e: Exception) {
            return "ERROR: ${e.message}"
        }
        return ""
    }

}

// Switch execution to the Yegg server thread.
// Use this to modify the World state from other threads.
suspend fun onYeggThread(block: suspend CoroutineScope.() -> Unit) = Yegg.onThread(block)
