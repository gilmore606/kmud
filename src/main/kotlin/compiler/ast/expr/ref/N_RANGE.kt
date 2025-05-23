package com.dlfsystems.compiler.ast.expr.ref

import com.dlfsystems.compiler.Coder
import com.dlfsystems.compiler.ast.expr.N_EXPR
import com.dlfsystems.vm.Opcode.O_GETRANGE
import com.dlfsystems.vm.Opcode.O_SETRANGE

// A range index into a value: <expr>[<expr>..<expr>]
class N_RANGE(val left: N_EXPR, val index1: N_EXPR, val index2: N_EXPR): N_EXPR() {
    override fun toText() = "RANGE<$left[$index1..$index2]>"
    override fun kids() = listOf(left, index1, index2)

    override fun code(coder: Coder) {
        left.code(coder)
        index1.code(coder)
        index2.code(coder)
        coder.code(this, O_GETRANGE)
    }

    override fun codeAssign(coder: Coder) {
        left.code(coder)
        index1.code(coder)
        index2.code(coder)
        coder.code(this, O_SETRANGE)
    }
}
