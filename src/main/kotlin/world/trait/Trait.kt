package com.dlfsystems.world.trait

import com.dlfsystems.value.Value
import com.dlfsystems.vm.Context
import com.dlfsystems.vm.VMWord
import java.util.*

// A collection of funcs and props, which can apply to an Obj.

open class Trait(val name: String) {

    val id: UUID = UUID.randomUUID()

    val funcs: MutableMap<String, Func> = mutableMapOf()
    open val props: MutableMap<String, Value> = mutableMapOf()

    fun programFunc(name: String, code: List<VMWord>) {
        funcs[name]?.also {
            it.program(code)
        } ?: {
            funcs[name] = Func(name).apply { program(code) }
        }
    }

    open fun getProp(c: Context, name: String): Value? = props.getOrDefault(name, null)
    open fun setProp(c: Context, name: String, value: Value): Boolean {
        props[name] = value
        return true
    }

    open fun callFunc(c: Context, name: String, args: List<Value>): Value? {
        funcs[name]?.also {
            // TODO execute func w args
        }
        return null
    }

}
