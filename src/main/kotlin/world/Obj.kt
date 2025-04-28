package com.dlfsystems.world

import com.dlfsystems.server.CommandMatch
import com.dlfsystems.server.Preposition
import com.dlfsystems.server.Yegg
import com.dlfsystems.value.VList
import com.dlfsystems.value.VObj
import com.dlfsystems.value.VString
import com.dlfsystems.value.Value
import com.dlfsystems.vm.VMException
import com.dlfsystems.vm.VMException.Type
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

    var vLocation: VObj = Yegg.vNullObj
    val location: Obj?
        get() = vLocation.v?.let { Yegg.world.getObj(it) }
    var vContents: VList = VList()
    val contents: List<Obj>
        get() = vContents.v.mapNotNull { (it as VObj).v?.let { Yegg.world.getObj(it) }}

    var vName: VString = Yegg.vNullStr
    val name: String
        get() = vName.v
    var vAliases: VList = VList()
    val aliases: List<String>
        get() = vAliases.v.map { (it as VString).v }


    fun acquireTrait(trait: Trait) {
        traits.add(trait.id)
    }

    fun dispelTrait(trait: Trait) {
        trait.props.keys.forEach { props.remove(it) }
        traits.remove(trait.id)
    }

    fun getProp(propName: String): Value? {
        props[propName]?.also { return it }

        traits.forEach {
            Yegg.world.getTrait(it)?.getProp(this, propName)?.also { return it }
        }
        return null
    }

    fun setProp(propName: String, value: Value): Boolean {
        if (propName == "name") {
            if (value is VString) {
                setName(value.v)
                return true
            } else throw VMException(Type.E_TYPE, "name must be string")
        }
        if (hasProp(propName)) {
            props[propName] = value
            return true
        }
        return false
    }

    fun hasProp(propName: String): Boolean {
        if (propName in props.keys) return true
        traits.forEach { traitID ->
            if (propName in (Yegg.world.getTrait(traitID)?.props?.keys ?: listOf())) return true
        }
        return false
    }

    fun matchCommand(cmdstr: String, argstr: String, dobjstr: String, dobj: Obj?, prep: Preposition?, iobjstr: String, iobj: Obj?): CommandMatch? {
        traits.mapNotNull { Yegg.world.getTrait(it) }.forEach { trait ->
            trait.matchCommand(this, cmdstr, argstr, dobjstr, dobj, prep, iobjstr, iobj)?.also { return it }
        }
        return null
    }

    fun setName(newName: String) {
        vAliases.v.removeIf { (it as VString).v == name }
        vName = VString(newName)
        if (!vAliases.v.any { (it as VString).v == newName }) {
            vAliases.v.add(VString(newName))
        }
    }
}
