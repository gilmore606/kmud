package com.dlfsystems.value

import com.dlfsystems.server.Yegg
import com.dlfsystems.world.Obj
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("VObj")
data class VObj(val v: Obj.ID?): Value() {

    @SerialName("yType")
    override val type = Type.OBJ

    inline fun obj() = Yegg.world.objs[v]

    override fun toString() = "#${v.toString().takeLast(5)}"
    override fun asString() = "OBJ" // TODO: use name

    override fun isTrue() = v != null

    override fun cmpEq(a2: Value) = (a2 is VObj) && (v == a2.v)

    override fun plus(a2: Value) = if (a2 is VString) VString(v.toString() + a2.v) else null

    override fun getProp(name: String): Value? {
        obj()?.also { obj ->
            when (name) {
                "asString" -> return propAsString()
                "traits" -> return propTraits(obj)
                "location" -> return obj.location
                "contents" -> return obj.contents
            }
            return obj.getProp(name)
        }
        throw IllegalArgumentException("Invalid obj")
    }

    override fun setProp(name: String, value: Value): Boolean {
        obj()?.also { obj ->
            return obj.setProp(name, value)
        }
        return false
    }

    private fun propAsString() = VString(toString())

    private fun propTraits(obj: Obj) = VList.make(obj.traits.mapNotNull { it.trait()?.vTrait })

}
