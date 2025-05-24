package com.dlfsystems.world

import com.dlfsystems.server.Yegg
import com.dlfsystems.value.*
import com.dlfsystems.world.trait.*
import kotlinx.serialization.Serializable

@Serializable
data class World(val name: String) {

    val traits: MutableMap<Trait.ID, Trait> = mutableMapOf()
    private val traitIDs: MutableMap<String, Trait.ID> = mutableMapOf()

    val objs: MutableMap<Obj.ID, Obj> = mutableMapOf()

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

    val sys: Trait
        get() = getTrait("sys")!!
    fun getSysValue(name: String): Value = sys.getProp(name) ?: VVoid
    fun getSysInt(name: String): Int = (sys.getProp(name) as VInt).v

    fun addTrait(name: String): Trait {
        if (traits.values.none { it.name == name }) {
            return when (name) {
                "sys" -> SysTrait()
                "user" -> UserTrait()
                else -> Trait.NTrait(name)
            }.also {
                traits[it.id] = it
                traitIDs[it.name] = it.id
            }
        }
        throw IllegalArgumentException("Trait with id $name already exists")
    }

    fun createObj() = Obj().also { objs[it.id] = it }

    fun destroyObj(obj: Obj) {
        obj.traits.forEach { traits[it]?.unapplyFrom(obj, forDestroy = true) }
        obj.contents.v.forEach {
            moveObj((it as VObj).obj()!!, obj.location)
        }
        moveObj(obj, Yegg.vNullObj)
        objs.remove(obj.id)
    }

    fun moveObj(obj: Obj, newLocV: VObj) {
        val oldLoc = obj.location
        // Prevent recursive move
        var checkLoc = newLocV.obj()
        while (checkLoc != null) {
            if (checkLoc.location == obj.vThis) throw IllegalArgumentException("Recursive move")
            checkLoc = checkLoc.location.obj()
        }
        oldLoc.obj()?.also { it.contents.v.removeIf { it == obj.vThis } }
        newLocV.obj()?.also {
            it.contents.v.add(obj.vThis)
            obj.location = newLocV
        } ?: run {
            obj.location = Yegg.vNullObj
        }
    }

    fun programVerb(traitName: String, name: String, code: String): String {
        getTrait(traitName)?.also { trait ->
            try {
                trait.programVerb(name, code)
                return "programmed \$${traitName}.${name}"
            } catch (e: Exception) {
                return "compile error: $e"
            }
        }
        return "ERR: unknown trait $traitName"
    }

    fun listVerb(traitName: String, name: String): String {
        getTrait(traitName)?.also { trait ->
            trait.verbs[name]?.also { return it.source }
                ?: return "ERR: verb not found $name"
        }
        return "ERR: unknown trait $traitName"
    }
}
