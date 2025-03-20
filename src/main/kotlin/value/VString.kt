package com.dlfsystems.value

import com.dlfsystems.vm.Context
import com.dlfsystems.vm.VMException.Type.*

data class VString(var v: String): Value() {

    override val type = Type.STRING

    override fun toString() = "\"$v\""
    override fun asString() = v
    override fun asMapKey() = v

    override fun iterableSize() = v.length

    override fun contains(a2: Value) = v.contains(a2.asString())

    override fun cmpEq(a2: Value) = (a2 is VString) && (v == a2.v)
    override fun cmpGt(a2: Value) = (a2 is VString) && (v > a2.v)
    override fun cmpGe(a2: Value) = (a2 is VString) && (v >= a2.v)

    override fun plus(a2: Value): Value? {
        a2.asString()?.also { return VString(v + it) }
        return null
    }

    override fun getProp(c: Context, name: String): Value? {
        when (name) {
            "length" -> return propLength()
            "asInt" -> return propAsInt()
            "asFloat" -> return propAsFloat()
            "isEmpty" -> return propIsEmpty()
            "isNotEmpty" -> return propIsNotEmpty()
        }
        return null
    }

    override fun getIndex(c: Context, index: Value): Value? {
        if (index is VInt) {
            if (index.v < 0 || index.v >= v.length) fail(E_RANGE, "string index ${index.v} out of bounds")
            return VString(v[index.v].toString())
        }
        return null
    }

    override fun getRange(c: Context, from: Value, to: Value): Value? {
        if (from is VInt && to is VInt) {
            if (from.v < 0 || to.v >= v.length) fail(E_RANGE, "string range ${from.v}..${to.v} out of bounds")
            return VString(v.substring(from.v, to.v))
        }
        return null
    }

    override fun setIndex(c: Context, index: Value, value: Value): Boolean {
        if (index is VInt) {
            if (index.v < 0 || index.v > v.length) fail(E_RANGE, "list index ${index.v} out of bounds")
            val old = v
            v = ""
            if (index.v > 0) v += old.substring(0..<index.v)
            v += value.asString()
            if (index.v < old.length - 1) v += old.substring(index.v..<old.length)
            return true
        }
        return false
    }

    override fun setRange(c: Context, from: Value, to: Value, value: Value): Boolean {
        if (from is VInt && to is VInt) {
            if (from.v < 0 || to.v >= v.length) fail(E_RANGE, "string range ${from.v}..${to.v} out of bounds")
            if (from.v > to.v) fail(E_RANGE, "string range start after end")
            val old = v
            v = ""
            if (from.v > 0) v += old.substring(0..<from.v)
            v += value.asString()
            if (to.v < old.length - 1) v += old.substring(to.v+1..<old.length)
            return true
        }
        return false
    }

    override fun callFunc(c: Context, name: String, args: List<Value>): Value? {
        when (name) {
            "split" -> return funcSplit(args)
            "contains" -> return funcContains(args)
            "startsWith" -> return funcStartsWith(args)
            "endsWith" -> return funcEndsWith(args)
            "indexOf" -> return funcIndexOf(args)
        }
        return null
    }


    // Custom props

    private fun propLength() = VInt(v.length)
    private fun propAsInt() = VInt(v.toInt())
    private fun propAsFloat() = VFloat(v.toFloat())
    private fun propIsEmpty() = VBool(v.isEmpty())
    private fun propIsNotEmpty() = VBool(v.isNotEmpty())

    // Custom funcs

    private fun funcSplit(args: List<Value>): Value {
        requireArgCount(args, 0, 1)
        return VList(
            v.split(
                if (args.isEmpty()) " " else args[0].asString()
            ).map { VString(it) }.toMutableList()
        )
    }

    private fun funcContains(args: List<Value>): Value {
        requireArgCount(args, 1, 1)
        return VBool(v.contains(args[0].asString()))
    }

    private fun funcStartsWith(args: List<Value>): Value {
        requireArgCount(args, 1, 1)
        return VBool(v.startsWith(args[0].asString()))
    }

    private fun funcEndsWith(args: List<Value>): Value {
        requireArgCount(args, 1, 1)
        return VBool(v.endsWith(args[0].asString()))
    }

    private fun funcIndexOf(args: List<Value>): Value {
        requireArgCount(args, 1, 1)
        val s = args[0].asString()
        return if (v.contains(s)) VInt(v.indexOf(s)) else VInt(-1)
    }
}
