package com.dlfsystems.compiler.ast

import com.dlfsystems.compiler.TokenType

abstract class N_STATEMENT: Node()

class N_ASSIGN(val ident: String, val right: N_EXPR): N_STATEMENT() {
    override fun toString() = "$ident = $right"
    override fun kids() = listOf(right)
}

class N_RETURN(val expr: N_EXPR): N_STATEMENT() {
    override fun toString() = "return $expr"
    override fun kids() = listOf(expr)
}

class N_FORLOOP(val assign: N_STATEMENT, val check: N_EXPR, val increment: N_STATEMENT, val body: N_STATEMENT): N_STATEMENT() {
    override fun toString() = "for ($assign; $check; $increment) $body"
    override fun kids() = listOf(assign, check, increment, body)
}

class N_WHILELOOP(val check: N_EXPR, val body: N_STATEMENT): N_STATEMENT() {
    override fun toString() = "while ($check) $body"
    override fun kids() = listOf(check, body)
}
