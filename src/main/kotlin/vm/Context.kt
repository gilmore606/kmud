package com.dlfsystems.vm

import com.dlfsystems.Yegg
import com.dlfsystems.world.World
import com.dlfsystems.value.*
import ulid.ULID

// Variables from the world which a VM uses to execute a func.
// A persistent VM will own a context whose values are updated from outside it.

class Context(
    val world: World = World()
) {
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

    var ticksLeft: Int = (world.getSysValue(this, "tickLimit") as VInt).v
    val callLimit: Int = (world.getSysValue(this, "callLimit") as VInt).v
    val callStack = ArrayDeque<Call>()

    // Push or pop the callstack.
    fun push(vThis: VObj, vTrait: VTrait, verb: String, args: List<Value>) =
        callStack.addFirst(Call(vThis, vTrait, verb, args))
    fun pop(): Call =
        callStack.removeFirst()

    fun getTrait(name: String) = world.getTrait(name)
    fun getTrait(id: ULID?) = id?.let { world.getTrait(id) }
    fun getObj(id: ULID?) = id?.let { world.getObj(id) }

    fun stackDump() = callStack.joinToString(separator = "\n  ", postfix = "\n")
}
