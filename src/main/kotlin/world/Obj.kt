package com.dlfsystems.world

import com.dlfsystems.server.CommandMatch
import com.dlfsystems.server.Preposition
import com.dlfsystems.server.Yegg
import com.dlfsystems.value.VList
import com.dlfsystems.value.VObj
import com.dlfsystems.value.Value
import com.dlfsystems.world.trait.Trait
import com.dlfsystems.world.trait.TraitID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// An instance in the world.

@Serializable
@SerialName("ObjID")
data class ObjID(val id: String) { override fun toString() = id }

@Serializable
@SerialName("Obj")
class Obj {
    val id = ObjID(Yegg.newID())
    val vThis = VObj(id)

    val traits: MutableList<TraitID> = mutableListOf()

    val props: MutableMap<String, Value> = mutableMapOf()

    var location: VObj = Yegg.vNullObj
    val locationObj: Obj?
        get() = location.v?.let { Yegg.world.getObj(it) }
    var contents: VList = VList()
    val contentsObjs: List<Obj>
        get() = contents.v.mapNotNull { (it as VObj).v?.let { Yegg.world.getObj(it) }}

    fun acquireTrait(trait: Trait) {
        traits.add(trait.id)
    }

    fun dispelTrait(trait: Trait) {
        trait.props.keys.forEach { props.remove(it) }
        traits.remove(trait.id)
    }

    fun getProp(name: String): Value? {
        props[name]?.also { return it }

        traits.forEach {
            Yegg.world.getTrait(it)?.getProp(this, name)?.also { return it }
        }
        return null
    }

    fun setProp(name: String, value: Value): Boolean {
        if (hasProp(name)) {
            props[name] = value
            return true
        }
        return false
    }

    fun hasProp(name: String): Boolean {
        if (name in props.keys) return true
        traits.forEach { traitID ->
            if (name in (Yegg.world.getTrait(traitID)?.props?.keys ?: listOf())) return true
        }
        return false
    }

    fun matchCommand(cmdstr: String, dobjstr: String, dobj: Obj?, prep: Preposition?, iobjstr: String, iobj: Obj?): CommandMatch? {
        traits.mapNotNull { Yegg.world.getTrait(it) }.forEach { trait ->
            trait.matchCommand(this, cmdstr, dobjstr, dobj, prep, iobjstr, iobj)?.also { return it }
        }
        return null
    }
}
