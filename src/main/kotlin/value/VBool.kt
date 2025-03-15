package com.dlfsystems.value

import com.dlfsystems.vm.Context

data class VBool(val v: Boolean): Value() {

    override val type = Type.BOOL

    override fun toString() = v.toString()
    override fun asString() = if (v) "true" else "false"
    override fun isTrue() = v

    override fun cmpEq(a2: Value) = (a2 is VBool) && (v == a2.v)
    override fun cmpGt(a2: Value) = v && (a2 is VBool) && !a2.v
    override fun cmpGe(a2: Value) = v && (a2 is VBool)

    override fun negate() = VBool(!v)

    override fun plus(a2: Value) = if (a2 is VString) VString(v.toString() + a2.v) else null

    override fun getProp(c: Context, name: String): Value? {
        when (name) {
            "asInt" -> return VInt(if (v) 1 else 0)
            "asString" -> return VString(asString())
        }
        return null
    }

}
