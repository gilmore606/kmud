package com.dlfsystems.value

import com.dlfsystems.server.Yegg
import com.dlfsystems.world.Obj
import com.dlfsystems.world.ObjID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("VObj")
data class VObj(val v: ObjID?): Value() {

    @SerialName("yType")
    override val type = Type.OBJ

    override fun toString() = "#${v.toString().takeLast(5)}"
    override fun asString() = "OBJ" // TODO: use name from passed context?

    override fun isTrue() = v != null

    override fun cmpEq(a2: Value) = (a2 is VObj) && (v == a2.v)

    override fun plus(a2: Value) = if (a2 is VString) VString(v.toString() + a2.v) else null

    override fun getProp(name: String): Value? {
        Yegg.world.getObj(v)?.also { obj ->
            when (name) {
                "asString" -> return propAsString()
                "traits" -> return propTraits(obj)
                "location" -> return obj.vLocation
                "contents" -> return obj.vContents
                "name" -> return obj.vName
                "aliases" -> return obj.vAliases
            }
            return obj.getProp(name)
        }
        throw IllegalArgumentException("Invalid obj")
    }

    override fun setProp(name: String, value: Value): Boolean {
        Yegg.world.getObj(v)?.also { obj ->
            return obj.setProp(name, value)
        }
        return false
    }


    // Custom props

    private fun propAsString() = VString(toString())

    private fun propTraits(obj: Obj) = VList(obj.traits.mapNotNull { Yegg.world.getTrait(it)?.vTrait }.toMutableList())

    // Custom verbs

}
