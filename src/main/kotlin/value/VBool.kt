package com.dlfsystems.value

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("VBool")
data class VBool(val v: Boolean): Value() {

    @SerialName("yType")
    override val type = Type.BOOL

    override fun toString() = v.toString()
    override fun asString() = if (v) "true" else "false"
    override fun isTrue() = v

    override fun cmpEq(a2: Value) = (a2 is VBool) && (v == a2.v)
    override fun cmpGt(a2: Value) = v && (a2 is VBool) && !a2.v
    override fun cmpGe(a2: Value) = v && (a2 is VBool)

    override fun negate() = VBool(!v)

    override fun plus(a2: Value) = if (a2 is VString) VString(v.toString() + a2.v) else null

    override fun getProp(name: String): Value? {
        when (name) {
            "asInt" -> return propAsInt()
            "asString" -> return propAsString()
        }
        return null
    }


    // Custom props

    private fun propAsInt() = VInt(if (v) 1 else 0)
    private fun propAsString() = VString(asString())

    // Custom verbs

}
