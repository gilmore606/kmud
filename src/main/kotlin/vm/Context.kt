package com.dlfsystems.vm

import com.dlfsystems.server.Connection
import com.dlfsystems.server.Yegg
import com.dlfsystems.world.World
import com.dlfsystems.value.*
import com.dlfsystems.world.ObjID
import com.dlfsystems.world.trait.TraitID

// Properties of a single command invocation as it passes from verb to verb.

class Context(val connection: Connection? = null) {
    class Call(
        val vThis: VObj,
        val vTrait: VTrait,
        val verb: String,
        val args: List<Value>,
    ) {
        override fun toString() = "$vThis $vTrait.$verb(${args.joinToString(",")})"
    }

    var vThis: VObj = Yegg.vNullObj
    var vUser: VObj = Yegg.vNullObj

    var ticksLeft: Int = (Yegg.world.getSysValue("tickLimit") as VInt).v
    var callsLeft: Int = (Yegg.world.getSysValue("callLimit") as VInt).v
    val callStack = ArrayDeque<Call>()

    // Push or pop the callstack.
    fun push(vThis: VObj, vTrait: VTrait, verb: String, args: List<Value>) =
        callStack.addFirst(Call(vThis, vTrait, verb, args))
    fun pop(): Call =
        callStack.removeFirst()

    fun stackDump() = callStack.joinToString(separator = "\n...", postfix = "\n")
}
