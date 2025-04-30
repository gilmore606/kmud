package com.dlfsystems.vm

import com.dlfsystems.server.Connection
import com.dlfsystems.server.ConnectionID
import com.dlfsystems.server.Yegg
import com.dlfsystems.value.*

// Properties of a single command invocation as it passes from verb to verb.

class Context(val connection: Connection? = null) {
    class Call(
        val vThis: VObj,
        val vTrait: VTrait,
        val verb: String,
        val args: List<Value>,
        val vm: VM,
    ) {
        override fun toString() = "$vThis $vTrait.$verb(${args.joinToString(",")})  (line ${vm.lineNum})"
    }

    var vThis: VObj = Yegg.vNullObj
    var vUser: VObj = connection?.user?.vThis ?: Yegg.vNullObj

    var ticksLeft: Int = Yegg.world.getSysInt("tickLimit")
    var callsLeft: Int = Yegg.world.getSysInt("callLimit")
    val callStack = ArrayDeque<Call>()

    // Push or pop the callstack.
    fun push(vThis: VObj, vTrait: VTrait, verb: String, args: List<Value>, vm: VM) =
        callStack.addFirst(Call(vThis, vTrait, verb, args, vm))
    fun pop(): Call =
        callStack.removeFirst()

    fun stackDump() = callStack.joinToString(prefix = "...", separator = "\n...", postfix = "\n")
}
