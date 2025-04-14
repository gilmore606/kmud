package com.dlfsystems.world.trait

import com.dlfsystems.server.Yegg
import com.dlfsystems.value.VBool
import com.dlfsystems.value.Value
import com.dlfsystems.world.Obj
import kotlinx.serialization.Serializable

@Serializable
class UserTrait : Trait("user") {

    override fun getProp(obj: Obj?, propName: String): Value? {
        when (propName) {
            "isConnected" -> return propIsConnected(obj)
        }
        return super.getProp(obj, propName)
    }

    private fun propIsConnected(obj: Obj?) = VBool(Yegg.connectedUsers.containsKey(obj))

}
