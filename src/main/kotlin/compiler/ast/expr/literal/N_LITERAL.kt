package com.dlfsystems.compiler.ast.expr.literal

import com.dlfsystems.compiler.Coder
import com.dlfsystems.compiler.ast.expr.N_EXPR
import com.dlfsystems.value.*
import com.dlfsystems.vm.Opcode.*
import com.dlfsystems.world.Obj

// A literal value appearing in code.

abstract class N_LITERAL: N_EXPR() {
    open fun codeValue(coder: Coder) { }

    override fun code(coder: Coder) {
        coder.code(this, O_VAL)
        codeValue(coder)
    }
}

class N_LITERAL_BOOLEAN(val value: Boolean): N_LITERAL() {
    override fun toText() = if (value) "true" else "false"
    override fun codeValue(coder: Coder) { coder.value(this, value) }
    override fun constantValue() = VBool(value)
}

class N_LITERAL_INTEGER(val value: Int): N_LITERAL() {
    override fun toText() = "$value"
    override fun codeValue(coder: Coder) { coder.value(this, value) }
    override fun constantValue() = VInt(value)
}

class N_LITERAL_FLOAT(val value: Float): N_LITERAL() {
    override fun toText() = "$value"
    override fun codeValue(coder: Coder) { coder.value(this, value) }
    override fun constantValue() = VFloat(value)
}

class N_LITERAL_OBJ(val objID: Obj.ID): N_LITERAL() {
    override fun toText() = "#$id"
    override fun codeValue(coder: Coder) { coder.value(this, objID) }
    override fun constantValue() = VObj(objID)
}

class N_LITERAL_STRING(val value: String): N_LITERAL() {
    override fun toText() = "\"$value\""
    override fun codeValue(coder: Coder) { coder.value(this, value) }
    override fun constantValue() = VString(value)
}
