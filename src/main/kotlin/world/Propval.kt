package com.dlfsystems.world

import com.dlfsystems.server.Yegg
import com.dlfsystems.value.Value
import com.dlfsystems.world.trait.Trait
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("PV")
data class Propval(
    val traitID: Trait.ID,
    var v: Value? = null,
) {
    inline fun get(name: String) = v ?: Yegg.world.traits[traitID]!!.getProp(name)
}
