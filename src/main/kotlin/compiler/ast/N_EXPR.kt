package com.dlfsystems.compiler.ast

import com.dlfsystems.compiler.Coder
import com.dlfsystems.vm.Opcode
import com.dlfsystems.vm.Opcode.*

// An expression which reduces to a Value.

abstract class N_EXPR: N_STATEMENT() {
    // Generate opcodes for this node as the left side of assignment.
    open fun codeAssign(coder: Coder) { fail("illegal left side of assignment") }
}

// Parenthetical expressions are parsed to N_PARENS to prevent X.(identifier) from binding as a literal reference.
class N_PARENS(val expr: N_EXPR): N_EXPR() {
    override fun toText() = "($expr)"
    override fun kids() = listOf(expr)
    override fun code(coder: Coder) {
        expr.code(coder)
    }
}

abstract class N_MATH_BINOP(val opString: String, val left: N_EXPR, val right: N_EXPR, val ops: List<Opcode>): N_EXPR() {
    override fun toText() = "($left $opString $right)"
    override fun kids() = listOf(left, right)
    override fun code(coder: Coder) {
        left.code(coder)
        right.code(coder)
        ops.forEach { coder.code(this, it) }
    }
}
class N_ADD(left: N_EXPR, right: N_EXPR): N_MATH_BINOP("+", left, right, listOf(O_ADD))
class N_SUBTRACT(left: N_EXPR, right: N_EXPR): N_MATH_BINOP("-", left, right, listOf(O_NEGATE, O_ADD))
class N_MULTIPLY(left: N_EXPR, right: N_EXPR): N_MATH_BINOP("*", left, right, listOf(O_MULT))
class N_DIVIDE(left: N_EXPR, right: N_EXPR): N_MATH_BINOP("/", left, right, listOf(O_DIV))
class N_POWER(left: N_EXPR, right: N_EXPR): N_MATH_BINOP("^", left, right, listOf(O_POWER))
class N_MODULUS(left: N_EXPR, right: N_EXPR): N_MATH_BINOP("%", left, right, listOf(O_MODULUS))

abstract class N_MATH_UNOP(val opString: String, val expr: N_EXPR, val mutateOp: Opcode): N_EXPR() {
    override fun toText() = "$opString$expr"
    override fun kids() = listOf(expr)
    override fun code(coder: Coder) {
        expr.code(coder)
        coder.code(this, mutateOp)
    }
}
class N_INVERSE(expr: N_EXPR): N_MATH_UNOP("!", expr, O_NEGATE)
class N_NEGATE(expr: N_EXPR): N_MATH_UNOP("-", expr, O_NEGATE)

abstract class N_LOGIC_BINOP(val opString: String, val left: N_EXPR, val right: N_EXPR, val ops: List<Opcode>): N_EXPR() {
    override fun toText() = "($left $opString $right)"
    override fun kids() = listOf(left, right)
    override fun code(coder: Coder) {
        left.code(coder)
        right.code(coder)
        ops.forEach { coder.code(this, it) }
    }
}
class N_LOGIC_AND(left: N_EXPR, right: N_EXPR): N_LOGIC_BINOP("&&", left, right, listOf(O_AND))
class N_LOGIC_OR(left: N_EXPR, right: N_EXPR): N_LOGIC_BINOP("||", left, right, listOf(O_OR))

class N_CONDITIONAL(val condition: N_EXPR, val eTrue: N_EXPR, val eFalse: N_EXPR): N_EXPR() {
    override fun toText() = "($condition ? $eTrue : $eFalse)"
    override fun kids() = listOf(condition, eTrue, eFalse)
    override fun code(coder: Coder) {
        condition.code(coder)
        coder.code(this, O_IF)
        coder.jumpForward(this, "cond$id")
        eTrue.code(coder)
        coder.code(this, O_JUMP)
        coder.jumpForward(this, "condFalse$id")
        coder.setForwardJump(this, "cond$id")
        eFalse.code(coder)
        coder.setForwardJump(this, "condFalse$id")
    }
}

abstract class N_COMPARE_BINOP(val opString: String, val left: N_EXPR, val right: N_EXPR, val compareOps: List<Opcode>): N_EXPR() {
    override fun toText() = "($left $opString $right)"
    override fun kids() = listOf(left, right)
    override fun code(coder: Coder) {
        left.code(coder)
        right.code(coder)
        compareOps.forEach { coder.code(this, it) }
    }
}
class N_EQUALS(left: N_EXPR, right: N_EXPR): N_COMPARE_BINOP("==", left, right, listOf(O_CMP_EQ))
class N_NOTEQUALS(left: N_EXPR, right: N_EXPR): N_COMPARE_BINOP("!=", left, right, listOf(O_CMP_EQ, O_NEGATE))
class N_GREATER_THAN(left: N_EXPR, right: N_EXPR): N_COMPARE_BINOP(">", left, right, listOf(O_CMP_GT))
class N_LESS_THAN(left: N_EXPR, right: N_EXPR): N_COMPARE_BINOP("<", left, right, listOf(O_CMP_LT))
class N_GREATER_EQUAL(left: N_EXPR, right: N_EXPR): N_COMPARE_BINOP(">=", left, right, listOf(O_CMP_GE))
class N_LESS_EQUAL(left: N_EXPR, right: N_EXPR): N_COMPARE_BINOP("<=", left, right, listOf(O_CMP_LE))

class N_INDEXREF(val left: N_EXPR, val index: N_EXPR): N_EXPR() {
    override fun toText() = "$left[$index]"
    override fun kids() = listOf(left, index)
}
