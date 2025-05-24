package com.dlfsystems.value

import com.dlfsystems.server.Yegg
import com.dlfsystems.vm.Context
import com.dlfsystems.world.trait.Trait
import com.dlfsystems.world.trait.Verb
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("VTrait")
data class VTrait(val v: Trait.ID?): Value() {

    @SerialName("yType")
    override val type = Type.TRAIT

    inline fun trait() = Yegg.world.traits[v]

    override fun toString() = "$" + (v?.let { it.trait()?.name } ?: "null")
    override fun asString() = toString()

    override fun isTrue() = v != null

    override fun cmpEq(a2: Value) = (a2 is VTrait) && (v == a2.v)

    override fun getProp(name: String): Value? {
        when (name) {
            "asString" -> return propAsString()
        }
        return trait()?.getProp(name)
    }

    override fun setProp(name: String, value: Value): Boolean {
        return trait()?.setProp(name, value) ?: false
    }

    private fun propAsString() = v?.let { v ->
        VString("$" + Yegg.world.traits[v]?.name)
    } ?: VString(asString())

    override fun callStaticVerb(c: Context, name: String, args: List<Value>): Value? {
        return trait()?.callStaticVerb(c, name, args)
    }

    override fun getVerb(c: Context, name: String): Verb? = trait()?.getVerb(name)

}
