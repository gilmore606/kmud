package com.dlfsystems.value

import com.dlfsystems.server.Yegg
import com.dlfsystems.vm.Context
import com.dlfsystems.world.trait.TraitID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("VTrait")
data class VTrait(val v: TraitID?): Value() {

    @SerialName("yType")
    override val type = Type.TRAIT

    override fun toString() = "\$$v"
    override fun asString() = "\$TRAIT" // TODO: get from context

    override fun isTrue() = v != null

    override fun cmpEq(a2: Value) = (a2 is VTrait) && (v == a2.v)

    private fun getTrait() = v?.let { Yegg.world.getTrait(it) }

    override fun getProp(name: String): Value? {
        when (name) {
            "asString" -> return propAsString()
        }
        return getTrait()?.getProp(null, name)
    }

    override fun setProp(name: String, value: Value): Boolean {
        return getTrait()?.setProp(name, value) ?: false
    }


    // Custom props

    private fun propAsString() = v?.let { v ->
        VString("$" + Yegg.world.getTrait(v)?.name)
    } ?: VString(asString())

    // Custom verbs

    override fun callVerb(c: Context, name: String, args: List<Value>): Value? {
        return getTrait()?.callVerb(c, name, args)
    }
}
