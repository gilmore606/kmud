package com.dlfsystems.value

import com.dlfsystems.vm.Context
import com.dlfsystems.vm.VMException.Type.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("VMap")
data class VMap(val v: MutableMap<Value, Value>): Value() {

    @SerialName("yType")
    override val type = Type.MAP
    override fun toString() = "[${v.entries.joinToString()}]"
    override fun asString() = v.entries.joinToString(", ")

    override fun contains(a2: Value) = v.containsKey(a2)

    override fun plus(a2: Value) = when (a2) {
        is VMap -> VMap((v + a2.v).toMutableMap())
        else -> null
    }

    override fun getProp(name: String): Value? {
        when (name) {
            "length" -> return propLength()
            "keys" -> return propKeys()
            "values" -> return propValues()
        }
        return null
    }

    override fun getIndex(i: Value): Value? {
        if (v.containsKey(i)) return v[i]
        else fail(E_RANGE, "no map entry $i")
        return null
    }

    override fun setIndex(i: Value, value: Value): Boolean {
        if (i.type !in keyTypes) fail(E_TYPE, "${i.type} cannot be map key")
        v[i] = value
        return true
    }

    override fun callVerb(c: Context, name: String, args: List<Value>): Value? {
        when (name) {
            "hasKey" -> return verbHasKey(args)
            "hasValue" -> return verbHasValue(args)
        }
        return null
    }

    // Custom props

    private fun propLength() = VInt(v.size)
    private fun propKeys() = VList(v.keys.toMutableList())
    private fun propValues() = VList(v.values.toMutableList())

    // Custom verbs

    private fun verbHasKey(args: List<Value>): Value {
        requireArgCount(args, 1, 1)
        return VBool(v.containsKey(args[0]))
    }

    private fun verbHasValue(args: List<Value>): Value {
        requireArgCount(args, 1, 1)
        return VBool(v.containsValue(args[0]))
    }


    companion object {
        val keyTypes = listOf(Type.STRING, Type.INT, Type.OBJ, Type.TRAIT)
    }
}
