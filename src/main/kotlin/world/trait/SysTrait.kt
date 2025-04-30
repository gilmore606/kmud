package com.dlfsystems.world.trait

import com.dlfsystems.app.Log
import com.dlfsystems.server.Command
import com.dlfsystems.server.Yegg
import com.dlfsystems.value.*
import com.dlfsystems.vm.Context
import com.dlfsystems.world.Obj
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// A special trait which exists in every world.
// Provides environment properties, server control, and primitive functions.

@Serializable
@SerialName("SysTrait")
class SysTrait : Trait("sys") {

    override fun getProp(obj: Obj?, propName: String): Value? {
        when (propName) {
            "time" -> return propTime()
            "connectedUsers" -> return propConnectedUsers()
        }
        return super.getProp(obj, propName)
    }

    override fun callVerb(c: Context, verbName: String, args: List<Value>): Value? {
        when (verbName) {
            "connectUser" -> return verbConnectUser(c, args)
            "disconnectUser" -> return verbDisconnectUser(c, args)
            "notify" -> return verbNotify(args)
            "notifyConn" -> return verbNotifyConn(c, args)
            "addTrait" -> return verbAddTrait(args)
            "create" -> return verbCreate(args)
            "destroy" -> return verbDestroy(args)
            "move" -> return verbMove(args)
            "setCommand" -> return verbSetCommand(args)
            "removeCommand" -> return verbRemoveCommand(args)
            "getCommands" -> return verbGetCommands(args)
            "removeVerb" -> return verbRemoveVerb(args)
            "dumpDatabase" -> return verbDumpDatabase(args)
            "shutdownServer" -> return verbShutdownServer(args)
        }
        return super.callVerb(c, verbName, args)
    }

    // $sys.time -> n
    private fun propTime() = VInt((System.currentTimeMillis() / 1000L).toInt())

    // $sys.connectedUsers -> [#obj, #obj...]
    private fun propConnectedUsers() = VList(Yegg.connectedUsers.keys.map { it.vThis }.toMutableList())

    // $sys.connectUser("username", "password") -> true if connected
    private fun verbConnectUser(c: Context, args: List<Value>): VBool {
        if (args.size != 2 || args[0] !is VString || args[1] !is VString) throw IllegalArgumentException("Bad args for connectUser")
        Yegg.world.getUserLogin((args[0] as VString).v, (args[1] as VString).v)?.also { user ->
            c.connection?.also {
                Yegg.connectUser(user, it)
                c.vUser = user.vThis
            }
            return Yegg.vTrue
        }
        return Yegg.vFalse
    }

    // $sys.disconnectUser()
    private fun verbDisconnectUser(c: Context, args: List<Value>): VVoid {
        if (args.isNotEmpty()) throw IllegalArgumentException("Bad args for disconnectUser")
        c.connection?.quitRequested = true
        return VVoid
    }

    // $sys.notify(#obj, "text")
    private fun verbNotify(args: List<Value>): VVoid {
        if (args.size != 2 || args[0] !is VObj || args[1] !is VString) throw IllegalArgumentException("Bad args for notify")
        Yegg.notifyUser(Yegg.world.getObj((args[0] as VObj).v), (args[1] as VString).v)
        return VVoid
    }

    // $sys.notifyConn("text")
    private fun verbNotifyConn(c: Context, args: List<Value>): VVoid {
        if (args.size != 1 || args[0] !is VString) throw IllegalArgumentException("Bad args for notifyConn")
        c.connection?.id?.id?.also { Yegg.notifyConn(it, (args[0] as VString).v) }
        return VVoid
    }

    // $sys.addTrait("newTrait")
    private fun verbAddTrait(args: List<Value>): VVoid {
        if (args.size != 1 || args[0] !is VString) throw IllegalArgumentException("Bad args for addTrait")
        Yegg.world.addTrait(args[0].asString())
        return VVoid
    }

    // $sys.create($trait1, $trait2...) -> #obj
    private fun verbCreate(args: List<Value>): VObj {
        val obj = Yegg.world.createObj()
        try {
            args.forEach {
                if (it !is VTrait) throw IllegalArgumentException("Non-trait passed to create")
                Yegg.world.applyTrait(it.v!!, obj.id)
            }
            return obj.vThis
        } catch (e: Exception) {
            Yegg.world.destroyObj(obj)
            throw e
        }
    }

    // $sys.destroy(#obj)
    private fun verbDestroy(args: List<Value>): VVoid {
        if (args.size != 1 || args[0] !is VObj) throw IllegalArgumentException("Bad args for destroy")
        Yegg.world.getObj((args[0] as VObj).v)?.also { subject ->
            Yegg.world.destroyObj(subject)
        } ?: throw IllegalArgumentException("invalid obj")
        return VVoid
    }

    // $sys.move(#obj, #loc)
    private fun verbMove(args: List<Value>): VVoid {
        if (args.size != 2 || args[0] !is VObj || args[1] !is VObj) throw IllegalArgumentException("Bad args for move")
        Yegg.world.getObj((args[0] as VObj).v)?.also { subject ->
            Yegg.world.moveObj(subject, args[1] as VObj)
        } ?: throw IllegalArgumentException("invalid obj")
        return VVoid
    }

    // $sys.setCommand($trait, "co*mmand/cmd arg prep arg = cmdVerb") -> "cmdVerb"
    private fun verbSetCommand(args: List<Value>): VVoid {
        if (args.size != 2 || args[0] !is VTrait || args[1] !is VString) throw IllegalArgumentException("Bad args for setCommand")
        Yegg.world.getTrait((args[0] as VTrait).v)?.also { trait ->
            Command.fromString((args[1] as VString).v)?.also { command ->
                trait.setCommand(command)
                Log.d("setCommand($trait, $command)")
                return VVoid
            } ?: throw IllegalArgumentException("invalid command pattern")
        }
        throw IllegalArgumentException("invalid trait")
    }

    // $sys.removeCommand($trait, "co*mmand/cmd arg prep arg")
    private fun verbRemoveCommand(args: List<Value>): VVoid {
        if (args.size != 2 || args[0] !is VTrait || args[1] !is VString) throw IllegalArgumentException("Bad args for removeCommand")
        Yegg.world.getTrait((args[0] as VTrait).v)?.also { trait ->
            trait.removeCommand((args[1] as VString).v)
            Log.d("removeCommand($trait, ${args[1]}")
            return VVoid
        }
        throw IllegalArgumentException("invalid trait")
    }

    // $sys.getCommands($trait) -> ["co*mmand arg...", ...]
    private fun verbGetCommands(args: List<Value>): VList {
        if (args.size != 1 || args[0] !is VTrait) throw IllegalArgumentException("Bad args for getCommands")
        Yegg.world.getTrait((args[0] as VTrait).v)?.also { trait ->
            return VList(trait.commands.map { VString(it.toString()) }.toMutableList())
        }
        throw IllegalArgumentException("invalid trait")
    }

    // $sys.removeVerb($trait, "verb")
    private fun verbRemoveVerb(args: List<Value>): VVoid {
        if (args.size != 2 || args[0] !is VTrait || args[1] !is VString) throw IllegalArgumentException("Bad args for removeVerb")
        Yegg.world.getTrait((args[0] as VTrait).v)?.also { trait ->
            trait.removeVerb((args[1] as VString).v)
            Log.d("removeVerb($trait, ${args[1]}")
            return VVoid
        }
        throw IllegalArgumentException("invalid trait")
    }

    // $sys.dumpDatabase() -> "err"
    private fun verbDumpDatabase(args: List<Value>): VString {
        if (args.size > 1 || (args.size == 1 && args[0] !is VBool)) throw IllegalArgumentException("Bad args for dumpDatabase")
        val withCode = args.isEmpty() || args[0].isTrue()
        return VString(Yegg.dumpDatabase(withCode))
    }

    // $sys.shutdownServer()
    private fun verbShutdownServer(args: List<Value>): VVoid {
        Log.i("Shutdown requested: $args")
        Yegg.shutdownServer()
        return VVoid
    }

}
