package com.dlfsystems.value

import com.dlfsystems.vm.Context
import java.util.*

data class VTrait(val v: UUID?): Value() {

    fun getTrait(c: Context?) = v?.let { c?.world?.getTrait(it) }

    override val type = Type.TRAIT

    override fun toString() = "\$$v"
    override fun asString() = "\$TRAIT" // TODO: get from context
    override fun asMapKey() = "$v TRAIT"

    override fun isTrue() = v != null

    override fun cmpEq(a2: Value) = (a2 is VTrait) && (v == a2.v)

    override fun getProp(c: Context, name: String): Value? {
        val trait = getTrait(c)
        when (name) {
            "asString" -> v?.also { v ->
                return VString("$" + c.world?.getTrait(v)?.name)
            } ?: return VString(toString())
        }
        return trait?.getProp(c, name)
    }

    override fun setProp(c: Context, name: String, value: Value): Boolean {
        val trait = getTrait(c)
        return trait?.setProp(c, name, value) ?: false
    }

}
