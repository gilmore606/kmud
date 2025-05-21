package com.dlfsystems.value

import com.dlfsystems.server.Yegg
import com.dlfsystems.vm.Context
import com.dlfsystems.vm.VMException.Type.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
@SerialName("VString")
data class VString(var v: String): Value() {

    @SerialName("yType")
    override val type = Type.STRING

    override fun toString() = "\"$v\""
    override fun asString() = v

    override fun iterableSize() = v.length

    override fun contains(a2: Value) = v.contains(a2.asString())

    override fun cmpEq(a2: Value) = (a2 is VString) && (v == a2.v)
    override fun cmpGt(a2: Value) = (a2 is VString) && (v > a2.v)
    override fun cmpGe(a2: Value) = (a2 is VString) && (v >= a2.v)

    override fun plus(a2: Value): Value? {
        a2.asString()?.also { return VString(v + it) }
        return null
    }

    override fun getProp(name: String): Value? {
        when (name) {
            "length" -> return VInt(v.length)
            "asInt" -> return VInt(v.toInt())
            "asFloat" -> return VFloat(v.toFloat())
            "isEmpty" -> return if (v.isEmpty()) Yegg.vTrue else Yegg.vFalse
            "isNotEmpty" -> return if (v.isNotEmpty()) Yegg.vTrue else Yegg.vFalse
        }
        return null
    }

    override fun getIndex(index: Value): Value? {
        if (index is VInt) {
            if (index.v < 0 || index.v >= v.length) fail(E_RANGE, "string index ${index.v} out of bounds")
            return VString(v[index.v].toString())
        }
        return null
    }

    override fun getRange(from: Value, to: Value): Value? {
        if (from is VInt && to is VInt) {
            if (from.v < 0 || to.v >= v.length) fail(E_RANGE, "string range ${from.v}..${to.v} out of bounds")
            return VString(v.substring(from.v, to.v))
        }
        return null
    }

    override fun setIndex(index: Value, value: Value): Boolean {
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

    override fun setRange(from: Value, to: Value, value: Value): Boolean {
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

    override fun callStaticVerb(c: Context, name: String, args: List<Value>): Value? {
        when (name) {
            "split" -> return verbSplit(args)
            "contains" -> return verbContains(args)
            "startsWith" -> return verbStartsWith(args)
            "endsWith" -> return verbEndsWith(args)
            "indexOf" -> return verbIndexOf(args)
            "replace" -> return verbReplace(args)
            "capitalize" -> return verbCapitalize(args)
            "trim" -> return verbTrim(args)
        }
        return null
    }

    private fun verbSplit(args: List<Value>): VList {
        requireArgCount(args, 0, 1)
        return VList(
            v.split(
                if (args.isEmpty()) " " else args[0].asString()
            ).map { VString(it) }.toMutableList()
        )
    }

    private fun verbContains(args: List<Value>): VBool {
        requireArgCount(args, 1, 1)
        return VBool(v.contains(args[0].asString()))
    }

    private fun verbStartsWith(args: List<Value>): VBool {
        requireArgCount(args, 1, 1)
        return VBool(v.startsWith(args[0].asString()))
    }

    private fun verbEndsWith(args: List<Value>): VBool {
        requireArgCount(args, 1, 1)
        return VBool(v.endsWith(args[0].asString()))
    }

    private fun verbIndexOf(args: List<Value>): VInt {
        requireArgCount(args, 1, 1)
        val s = args[0].asString()
        return if (v.contains(s)) VInt(v.indexOf(s)) else VInt(-1)
    }

    private fun verbReplace(args: List<Value>): VString {
        requireArgCount(args, 2, 3)
        val ignoreCase = args.size == 2 || args[3].isTrue()
        return VString(v.replace(args[0].asString(), args[1].asString(), ignoreCase))
    }

    private fun verbCapitalize(args: List<Value>): VString {
        requireArgCount(args, 0, 0)
        return VString(v.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
    }

    private fun verbTrim(args: List<Value>): VString {
        requireArgCount(args, 0, 0)
        return VString(v.trim())
    }

}
