package com.dlfsystems.world.trait

import com.dlfsystems.server.Yegg
import com.dlfsystems.app.Log
import com.dlfsystems.compiler.Compiler
import com.dlfsystems.server.Command
import com.dlfsystems.server.CommandMatch
import com.dlfsystems.server.Preposition
import com.dlfsystems.util.matchesWildcard
import com.dlfsystems.value.*
import com.dlfsystems.vm.Context
import com.dlfsystems.world.Obj
import com.dlfsystems.world.ObjID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// A collection of verbs and props, which can apply to an Obj.

@Serializable
@SerialName("TraitID")
data class TraitID(val id: String) { override fun toString() = id }

@Serializable
sealed class Trait(val name: String) {

    val id = TraitID(Yegg.newID())
    val vTrait = VTrait(id)

    val traits: MutableList<TraitID> = mutableListOf()

    val commands: MutableSet<Command> = mutableSetOf()
    val verbs: MutableMap<String, Verb> = mutableMapOf()
    val props: MutableMap<String, Value> = mutableMapOf()

    val objects: MutableSet<ObjID> = mutableSetOf()

    fun applyTo(obj: Obj) {
        obj.traits.forEach {
            if (Yegg.world.getTrait(it)?.hasTrait(this.id) == true)
                throw IllegalArgumentException("obj already inherits trait")
        }
        objects.add(obj.id)
        obj.acquireTrait(this)
    }

    fun removeFrom(obj: Obj) {
        objects.remove(obj.id)
        obj.dispelTrait(this)
    }

    fun hasTrait(trait: TraitID): Boolean = (trait in traits) ||
            (traits.firstOrNull { Yegg.world.getTrait(it)?.hasTrait(trait) ?: false } != null)

    fun setCommand(command: Command) {
        commands.removeIf { it.verb == command.verb }
        commands.add(command)
    }

    fun programVerb(verbName: String, cOut: Compiler.Result) {
        verbs[verbName]?.also {
            it.program(cOut)
        } ?: run {
            verbs[verbName] = Verb(verbName).apply { program(cOut) }
        }
    }

    open fun getProp(obj: Obj?, propName: String): Value? {
        return when (propName) {
            "objects" -> return VList(objects.mapNotNull { Yegg.world.getObj(it)?.vThis }.toMutableList())
            else -> props.getOrDefault(propName, null)
        }
    }

    open fun setProp(propName: String, value: Value): Boolean {
        props[propName] = value
        return true
    }

    open fun callVerb(c: Context, verbName: String, args: List<Value>): Value? {
        verbs[verbName]?.also {
            Log.i("static verb call: \$$name.$verbName($args)")
            return it.call(c, Yegg.vNullObj, vTrait, args)
        }
        return null
    }

    fun matchCommand(obj: Obj?, cmdstr: String, dobjstr: String, dobj: Obj?, prep: Preposition?, iobjstr: String, iobj: Obj?): CommandMatch? {
        commands.firstOrNull { it.names.any { cmdstr.matchesWildcard(it) } }?.also { command ->
            if (command.prep != prep) return null
            val args = mutableListOf<Value>()
            when (command.dobj) {
                null -> { if (dobjstr.isNotBlank()) return null }
                is Command.Arg.This -> { if (dobj != obj) return null }
                is Command.Arg.Text -> { args.add(VString(dobjstr)) }
                is Command.Arg.Any -> { if (dobj == null) return null else args.add(dobj.vThis) }
            }
            when (command.iobj) {
                null -> { if (iobjstr.isNotBlank()) return null }
                is Command.Arg.This -> { if (iobj != obj) return null }
                is Command.Arg.Text -> { args.add(VString(iobjstr)) }
                is Command.Arg.Any -> { if (iobj == null) return null else args.add(iobj.vThis) }
            }
            return CommandMatch(command.verb, this, obj, args)
        }
        traits.mapNotNull { Yegg.world.getTrait(it) }.forEach { parent ->
            parent.matchCommand(obj, cmdstr, dobjstr, dobj, prep, iobjstr, iobj)?.also { return it }
        }
        return null
    }
}

@Serializable
@SerialName("NTrait")
// A "normal" dynamically defined trait.  We need this subclass for serialization.
class NTrait(private val n: String) : Trait(n)
