package com.dlfsystems.value

import com.dlfsystems.vm.Context
import com.dlfsystems.vm.VMException.Type.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("VList")
data class VList(var v: MutableList<Value> = mutableListOf()): Value() {

    @SerialName("yType")
    override val type = Type.LIST

    override fun toString() = "[${v.joinToString(", ")}]"
    override fun asString() = v.joinToString(", ")

    override fun iterableSize() = v.size

    override fun contains(a2: Value) = v.contains(a2)

    override fun plus(a2: Value) = when (a2) {
        is VList -> VList(v.toMutableList().apply { addAll(a2.v) })
        else -> null
    }

    override fun getProp(name: String): Value? {
        when (name) {
            "length" -> return propSize()
            "isEmpty" -> return propIsEmpty()
            "isNotEmpty" -> return propIsNotEmpty()
        }
        return null
    }

    override fun getIndex(index: Value): Value? {
        if (index is VInt) {
            if (index.v < 0 || index.v >= v.size) fail(E_RANGE, "list index ${index.v} out of bounds")
            return v[index.v]
        }
        return null
    }

    override fun getRange(from: Value, to: Value): Value? {
        if (from is VInt && to is VInt) {
            if (from.v < 0 || to.v >= v.size) fail(E_RANGE, "list range ${from.v}..${to.v} out of bounds")
            return VList(v.subList(from.v, to.v))
        }
        return null
    }

    override fun setIndex(index: Value, value: Value): Boolean {
        if (index is VInt) {
            if (index.v < 0 || index.v >= v.size) fail(E_RANGE, "list index ${index.v} out of bounds")
            v[index.v] = value
            return true
        }
        return false
    }

    override fun setRange(from: Value, to: Value, value: Value): Boolean {
        if (from is VInt && to is VInt) {
            if (from.v < 0 || to.v >= v.size) fail(E_RANGE, "list range ${from.v}..${to.v} out of bounds")
            if (from.v > to.v) fail(E_RANGE, "list range start after end")
            if (value !is VList) fail(E_TYPE, "list range insert must be a list")
            val old = v
            v = mutableListOf<Value>().apply {
                addAll(old.subList(0, from.v))
                addAll((value as VList).v)
                addAll(old.subList(to.v+1, old.size))
            }
            return true
        }
        return false
    }

    override fun callVerb(c: Context, name: String, args: List<Value>): Value? {
        when (name) {
            "join" -> return verbJoin(args)
            "push" -> return verbPush(args)
            "pop" -> return verbPop(args)
            "contains" -> return verbContains(args)
            "indexOf" -> return verbIndexOf(args)
        }
        return null
    }


    // Custom props

    private fun propSize(): Value = VInt(v.size)
    private fun propIsEmpty(): Value = VBool(v.isEmpty())
    private fun propIsNotEmpty(): Value = VBool(v.isNotEmpty())

    // Custom verbs

    private fun verbJoin(args: List<Value>): Value {
        requireArgCount(args, 0, 1)
        return VString(
            v.joinToString(
                if (args.isEmpty()) " " else args[0].asString()
            ) { it.asString() }
        )
    }

    private fun verbPush(args: List<Value>): Value {
        requireArgCount(args, 1, 1)
        v.add(0, args[0])
        return VVoid
    }

    private fun verbPop(args: List<Value>): Value {
        requireArgCount(args, 0, 0)
        if (v.isEmpty()) fail(E_RANGE, "cannot pop empty list")
        return v.removeAt(0)
    }

    private fun verbContains(args: List<Value>): Value {
        requireArgCount(args, 1, 1)
        return VBool(v.contains(args[0]))
    }

    private fun verbIndexOf(args: List<Value>): Value {
        requireArgCount(args, 1, 1)
        return if (v.contains(args[0])) VInt(v.indexOf(args[0])) else VInt(-1)
    }

    companion object {
        // TODO: figure out consistent mutability semantics
        fun make(v: List<Value>) = VList(v.toMutableList())
    }
}
