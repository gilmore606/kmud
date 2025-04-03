package com.dlfsystems.world.trait

import com.dlfsystems.Yegg
import com.dlfsystems.app.Log
import com.dlfsystems.compiler.Compiler
import com.dlfsystems.value.VList
import com.dlfsystems.value.VObj
import com.dlfsystems.value.VTrait
import com.dlfsystems.value.Value
import com.dlfsystems.vm.Context
import com.dlfsystems.world.Obj
import com.dlfsystems.world.World
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.uuid.Uuid

// A collection of verbs and props, which can apply to an Obj.

@Serializable
open class Trait(val name: String) {

    @Transient lateinit var world: World

    val id: Uuid = Uuid.random()
    private val vTrait = VTrait(id)

    val traits: MutableList<Uuid> = mutableListOf()

    val verbs: MutableMap<String, Verb> = mutableMapOf()
    open val props: MutableMap<String, Value> = mutableMapOf()

    val objects: MutableSet<Uuid> = mutableSetOf()

    fun applyTo(obj: Obj) {
        obj.traits.forEach {
            if (world.getTrait(it)?.hasTrait(this.id) == true)
                throw IllegalArgumentException("obj already inherits trait")
        }
        objects.add(obj.id)
        obj.acquireTrait(this)
    }

    fun removeFrom(obj: Obj) {
        objects.remove(obj.id)
        obj.dispelTrait(this)
    }

    fun hasTrait(trait: Uuid): Boolean = (trait in traits) || (traits.first { world.getTrait(it)?.hasTrait(trait) ?: false } != null)

    fun programVerb(verbName: String, cOut: Compiler.Result) {
        verbs[verbName]?.also {
            it.program(cOut)
        } ?: run {
            verbs[verbName] = Verb(verbName).apply { program(cOut) }
        }
    }

    open fun getProp(c: Context, propName: String): Value? {
        return when (propName) {
            "objects" -> return VList(objects.map { VObj(it) }.toMutableList())
            else -> props.getOrDefault(propName, null)
        }
    }

    open fun setProp(c: Context, propName: String, value: Value): Boolean {
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

}
