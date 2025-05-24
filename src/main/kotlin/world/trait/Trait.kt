package com.dlfsystems.world.trait

import com.dlfsystems.server.Yegg
import com.dlfsystems.server.parser.Command
import com.dlfsystems.server.parser.Command.Arg
import com.dlfsystems.server.parser.CommandMatch
import com.dlfsystems.server.parser.Preposition
import com.dlfsystems.util.NanoID
import com.dlfsystems.util.matchesWildcard
import com.dlfsystems.value.*
import com.dlfsystems.vm.Context
import com.dlfsystems.world.Obj
import com.dlfsystems.world.Propval
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// A collection of verbs and props, which can apply to an Obj.

@Serializable
sealed class Trait(val name: String) {

    @Serializable
    @SerialName("NTrait")
    // A "normal" dynamically defined trait.  We need this subclass for serialization.
    class NTrait(private val n: String) : Trait(n)

    @Serializable @JvmInline
    @SerialName("TraitID")
    value class ID(val id: String) {
        override fun toString() = id
        inline fun trait() = Yegg.world.traits[this]
    }

    val id = ID(NanoID.newID())
    val vTrait = VTrait(id)

    val traits: MutableList<ID> = mutableListOf()
    val childTraits: MutableList<ID> = mutableListOf()

    val commands: MutableSet<Command> = mutableSetOf()
    val verbs: MutableMap<String, Verb> = mutableMapOf()
    val props: MutableMap<String, Propval> = mutableMapOf()

    val objects: MutableSet<Obj.ID> = mutableSetOf()

    // Traits

    // Add parent trait to this trait.
    fun addTrait(otrait: Trait) {
        traits.forEach {
            if (it.trait()!!.inherits(otrait.id)) throw IllegalArgumentException("trait already inherits $otrait")
        }
        traits.add(otrait.id)
        otrait.childTraits.add(id)
        objects.forEach { otrait.applyTo(it.obj()!!) }
    }

    // Remove parent trait from this trait.
    fun removeTrait(otrait: Trait) {
        if (otrait.id !in traits) throw IllegalArgumentException("trait does not have trait")
        traits.remove(otrait.id)
        otrait.childTraits.remove(id)
        objects.forEach { otrait.unapplyFrom(it.obj()!!) }
    }

    fun inherits(otrait: ID): Boolean {
        if (otrait == id) return true
        traits.forEach { if (it.trait()!!.inherits(otrait)) return true }
        return false
    }

    // Objects

    fun applyTo(obj: Obj) {
        objects.add(obj.id)
        nativeProps().forEach {
            if (!obj.props.containsKey(it)) {
                obj.addProp(it, this)
            }
        }
        traits.forEach { it.trait()!!.applyTo(obj) }
    }

    fun unapplyFrom(obj: Obj, forDestroy: Boolean = false) {
        objects.remove(obj.id)
        if (!forDestroy) nativeProps().forEach {
            obj.removeProp(it)
        }
        traits.forEach { it.trait()!!.unapplyFrom(obj) }
    }

    // Props

    private fun nativeProps() = props.keys.filter { props[it]!!.traitID == id }

    fun addProp(propName: String, value: Value) {
        if (hasProp(propName)) throw IllegalArgumentException("trait already has prop $propName")
        props[propName] = Propval(this.id, value)
        childTraits.forEach { it.trait()!!.addInheritedProp(propName, this) }
        objects.forEach { it.obj()!!.addProp(propName, this) }
    }

    private fun addInheritedProp(propName: String, fromTrait: Trait) {
        props[propName] = Propval(fromTrait.id)
    }

    fun removeProp(propName: String) {
        if (!props.containsKey(propName)) throw IllegalArgumentException("trait has no property $propName")
        if (props[propName]!!.traitID != id) throw IllegalArgumentException("prop $propName is not owned by trait")
        props.remove(propName)
        childTraits.forEach { it.trait()!!.removeInheritedProp(propName) }
        objects.forEach { it.obj()!!.also { if (it.props[propName]!!.traitID == id) it.removeProp(propName) } }
    }

    fun removeInheritedProp(propName: String) {
        props.remove(propName)
    }

    inline fun hasProp(propName: String): Boolean = props.containsKey(propName)

    open fun getProp(propName: String): Value? {
        return when (propName) {
            "objects" -> return VList.make(objects.mapNotNull { it.obj()?.vThis })
            else -> props[propName]?.get(propName)
        }
    }

    inline fun setProp(propName: String, value: Value): Boolean =
        props[propName]?.let { it.v = value ; true } ?: false

    inline fun clearProp(propName: String): Boolean =
        props[propName]?.let { it.v = null ; true } ?: false

    // Verbs

    fun getVerb(name: String): Verb? = verbs[name]

    fun programVerb(verbName: String, source: String) {
        verbs[verbName]?.also {
            it.program(source)
        } ?: run {
            verbs[verbName] = Verb(verbName).apply { program(source) }
        }
    }

    fun removeVerb(verbName: String) {
        verbs.remove(verbName)
    }

    open fun callStaticVerb(c: Context, verbName: String, args: List<Value>): Value? = null

    // Commands

    fun setCommand(command: Command) {
        commands.removeIf { it.verb == command.verb }
        commands.add(command)
    }

    fun removeCommand(spec: String) {
        commands.removeIf { it.spec == spec }
    }

    fun matchCommand(obj: Obj?, cmdstr: String, argstr: String, dobjstr: String, dobj: Obj?, prep: Preposition?, iobjstr: String, iobj: Obj?): CommandMatch? {
        fun matchArg(argType: Arg?, argString: String, matchedObj: Obj?): Value? =
            when (argType) {
                Arg.THIS -> if (obj != matchedObj) null else VVoid
                Arg.STRING -> VString(argString)
                Arg.ANY -> matchedObj?.vThis
                null -> if (argString.isNotBlank()) null else VVoid
            }

        for (cmd in commands) {
            if (cmd.names.any { cmdstr.matchesWildcard(it) }) {
                var a1: Value = VVoid
                var a2: Value = VVoid
                if (cmd.prep == null) {
                    a1 = matchArg(cmd.dobj, argstr, dobj) ?: continue
                } else if (cmd.prep == prep) {
                    a1 = matchArg(cmd.dobj, dobjstr, dobj) ?: continue
                    a2 = matchArg(cmd.iobj, iobjstr, iobj) ?: continue
                }
                return CommandMatch(cmd.verb, this, obj, buildList {
                    if (a1 != VVoid) add(a1)
                    if (a2 != VVoid) add(a2)
                })
            }
        }

        traits.mapNotNull { Yegg.world.traits[it] }.forEach { parent ->
            parent.matchCommand(obj, cmdstr, argstr, dobjstr, dobj, prep, iobjstr, iobj)?.also { return it }
        }

        return null
    }

}
