@file:Suppress("NOTHING_TO_INLINE")

package com.dlfsystems.world

import com.dlfsystems.server.parser.CommandMatch
import com.dlfsystems.server.parser.Preposition
import com.dlfsystems.server.Yegg
import com.dlfsystems.util.NanoID
import com.dlfsystems.value.VList
import com.dlfsystems.value.VObj
import com.dlfsystems.value.Value
import com.dlfsystems.world.trait.Trait
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


// An instance in the world.

@Serializable
@SerialName("Obj")
class Obj {

    @Serializable @JvmInline
    @SerialName("ObjID")
    value class ID(val id: String) {
        override fun toString() = id
        inline fun obj() = Yegg.world.objs[this]
    }

    val id = ID(NanoID.newID())
    val vThis = VObj(id)

    val traits: MutableList<Trait.ID> = mutableListOf()

    // One entry per property, from all traits.  If value is null, default comes from traitID.
    val props: MutableMap<String, Propval> = mutableMapOf()

    var location: VObj = Yegg.vNullObj
    val locationObj: Obj?
        get() = location.v?.obj()
    var contents: VList = VList()
    val contentsObjs: List<Obj>
        get() = contents.v.mapNotNull { (it as VObj).obj() }

    // Traits

    // Add trait to this object.
    fun addTrait(trait: Trait) {
        traits.forEach {
            if (it.trait()!!.inherits(trait.id)) throw IllegalArgumentException("obj already has trait")
        }
        traits.add(trait.id)
        trait.applyTo(this)
    }

    // Remove trait from this object.
    fun removeTrait(trait: Trait) {
        if (trait.id !in traits) throw IllegalArgumentException("obj does not have trait")
        trait.unapplyFrom(this)
        traits.remove(trait.id)
    }

    // Props

    fun addProp(propName: String, trait: Trait) {
        if (!props.containsKey(propName)) props[propName] = Propval(trait.id)
    }

    fun removeProp(propName: String) { props.remove(propName) }

    inline fun getProp(propName: String): Value? = props[propName]?.get(propName)

    inline fun setProp(propName: String, value: Value): Boolean =
        props[propName]?.let { it.v = value ; true } ?: false

    inline fun clearProp(propName: String): Boolean =
        props[propName]?.let { it.v = null ; true } ?: false

    // Commands

    fun matchCommand(cmdstr: String, argstr: String, dobjstr: String, dobj: Obj?, prep: Preposition?, iobjstr: String, iobj: Obj?): CommandMatch? {
        traits.mapNotNull { it.trait() }.forEach { trait ->
            trait.matchCommand(this, cmdstr, argstr, dobjstr, dobj, prep, iobjstr, iobj)?.also { return it }
        }
        return null
    }
}
