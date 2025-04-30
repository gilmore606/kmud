package com.dlfsystems.world

import com.dlfsystems.server.Yegg
import com.dlfsystems.compiler.Compiler
import com.dlfsystems.value.*
import com.dlfsystems.world.trait.*
import kotlinx.serialization.Serializable

@Serializable
data class World(val name: String) {

    private val traits: MutableMap<TraitID, Trait> = mutableMapOf()
    private val traitIDs: MutableMap<String, TraitID> = mutableMapOf()

    private val objs: MutableMap<ObjID, Obj> = mutableMapOf()

    fun getUserLogin(name: String, password: String): Obj? {
        getTrait("user")?.objects?.forEach { obj ->
            objs[obj]?.getProp("username")?.also {
                if (it == VString(name)) {
                    objs[obj]?.getProp("password")?.also {
                        if (it == VString(password)) {
                            return objs[obj]
                        }
                    }
                }
            }
        }
        return null
    }

    fun getTrait(named: String) = traits[traitIDs[named]]
    fun getTrait(id: TraitID?) = id?.let { traits[it] }
    fun getObj(id: ObjID?) = id?.let { objs[it] }

    val sys: Trait
        get() = getTrait("sys")!!
    fun getSysValue(name: String): Value = sys.getProp(null, name) ?: VVoid
    fun getSysInt(name: String): Int = (sys.getProp(null, name) as VInt).v

    fun addTrait(name: String): Trait {
        if (traits.values.none { it.name == name }) {
            return when (name) {
                "sys" -> SysTrait()
                "user" -> UserTrait()
                else -> NTrait(name)
            }.also {
                traits[it.id] = it
                traitIDs[it.name] = it.id
            }
        }
        throw IllegalArgumentException("Trait with id $name already exists")
    }

    fun createObj() = Obj().also { objs[it.id] = it }

    fun destroyObj(obj: Obj) {
        obj.vContents.v.forEach {
            moveObj(getObj((it as VObj).v)!!, obj.vLocation)
        }
        moveObj(obj, Yegg.vNullObj)
        objs.remove(obj.id)
    }

    fun moveObj(obj: Obj, newLocV: VObj) {
        val oldLoc = obj.vLocation
        // Prevent recursive move
        var checkLoc = getObj(newLocV.v)
        while (checkLoc != null) {
            if (checkLoc.vLocation == obj.vThis) throw IllegalArgumentException("Recursive move")
            checkLoc = getObj(checkLoc.vLocation.v)
        }
        oldLoc.v?.also { getObj(it)!!.vContents.v.removeIf { it == obj.vThis } }
        getObj(newLocV.v)?.also {
            it.vContents.v.add(obj.vThis)
            obj.vLocation = newLocV
        } ?: run {
            obj.vLocation = Yegg.vNullObj
        }
    }

    fun applyTrait(traitID: TraitID, objID: ObjID) {
        traits[traitID]?.applyTo(objs[objID]!!)
    }

    fun dispelTrait(traitID: TraitID, objID: ObjID) {
        traits[traitID]?.removeFrom(objs[objID]!!)
    }

    fun programVerb(traitName: String, name: String, code: String): String {
        getTrait(traitName)?.also { trait ->
            try {
                val cOut = Compiler.compile(code)
                trait.programVerb(name, cOut)
                return "programmed \$${traitName}.${name} (${cOut.code.size} words)"
            } catch (e: Exception) {
                return "compile error: $e"
            }
        }
        return "ERR: unknown trait $traitName"
    }

    fun listVerb(traitName: String, name: String): String {
        getTrait(traitName)?.also { trait ->
            trait.verbs[name]?.also { return it.getListing() }
                ?: return "ERR: verb not found $name"
        }
        return "ERR: unknown trait $traitName"
    }
}
