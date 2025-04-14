package com.dlfsystems.server

import com.dlfsystems.value.Value
import com.dlfsystems.world.Obj
import com.dlfsystems.world.trait.Trait

class CommandMatch(
    val verb: String,
    val trait: Trait,
    var obj: Obj?,
    val args: List<Value>,
)
