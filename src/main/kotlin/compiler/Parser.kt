@file:Suppress("NOTHING_TO_INLINE")

package com.dlfsystems.compiler

import com.dlfsystems.compiler.TokenType.*
import com.dlfsystems.compiler.ast.*

// Take a stream of input tokens from Lexer and produce a tree of syntax nodes.

class Parser(inputTokens: List<Token>) {

    // Internal buffer for input tokens.  We consume these as we parse.
    private val tokens = inputTokens.toMutableList()

    // Track line and char position as we go, to tag nodes for tracebacks.
    private var lineNum: Int = 0
    private var charNum: Int = 0

    private inline fun EOF() = Token(T_EOF, "", lineNum, charNum)
    private inline fun fail(m: String) { throw CompileException(m, lineNum, charNum) }
    private inline fun expectCloseParen() { consume(T_PAREN_CLOSE) ?: fail("unclosed parentheses") }

    // Pull the next token from the input stream.
    private inline fun consume() = (if (tokens.isEmpty()) EOF() else tokens.removeAt(0)).also {
        lineNum = it.lineNum
        charNum = it.charNum
    }

    // Pull the next token if of the given type, else return null.
    private inline fun consume(vararg types: TokenType) = if (nextIs(types.toList())) consume() else null

    // Peek at the next token in the input stream.
    private inline fun nextToken(skip: Int = 0) = if (skip >= tokens.size) EOF() else tokens[skip]

    // Is the next token one of the given types?
    private inline fun nextIs(vararg types: TokenType) = (nextToken().type in types)
    private inline fun nextIs(types: List<TokenType>) = (nextToken().type in types)
    // Are the next tokens each of the given ordered types?
    private inline fun nextAre(vararg types: TokenType): Boolean {
        for (i in types.indices) {
            if (nextToken(i).type != types[i]) return false
        }
        return true
    }

    // Tag returned nodes with the current lineNum and charNum for tracebacks.
    private inline fun <T: Node>node(n: T): T = n.apply {
        lineNum = this@Parser.lineNum
        charNum = this@Parser.charNum
    }

    //
    // Parse our token stream into a tree of syntax nodes.
    // Expect a series of statements.  Return N_BLOCK containing the series of statements.
    //
    fun parse(): N_BLOCK {
        val statements = ArrayList<N_STATEMENT>()
        while(tokens.isNotEmpty()) {
            pStatement()?.also {
                statements.add(it)
            } ?: fail("incomplete statement")
        }
        return N_BLOCK(statements)
    }

    // Statements

    // Parse any statement type we find next.
    private fun pStatement(): N_STATEMENT? {
        pBlock()?.also { return it }
        pIfThen()?.also { return it }
        pForLoop()?.also { return it }
        pWhileLoop()?.also { return it }
        pReturn()?.also { return it }
        pFail()?.also { return it }
        pIncrement()?.also { return it }
        pAssign()?.also { return it }
        pExprStatement()?.also { return it }
        return null
    }

    // Parse: { <statement> <statement>... }
    private fun pBlock(): N_STATEMENT? {
        consume(T_BRACE_OPEN) ?: return null
        val statements = ArrayList<N_STATEMENT>()
        while (!nextIs(T_BRACE_CLOSE)) {
            pStatement()?.also { statements.add(it) } ?: fail("non-statement in braces")
        }
        consume(T_BRACE_CLOSE) ?: fail("unclosed braces")
        return when (statements.size) {
            0 -> null
            1 -> node(statements[0])
            else -> node(N_BLOCK(statements))
        }
    }

    // Parse: if <expr> <statement> [else <statement>]
    private fun pIfThen(): N_STATEMENT? {
        consume(T_IF) ?: return null
        pExpression()?.also { condition ->
            pStatement()?.also { eThen ->
                consume(T_ELSE)?.also {
                    pStatement()?.also { eElse ->
                        return node(N_IFSTATEMENT(condition, eThen, eElse))
                    } ?: fail("missing else block")
                }
                return node(N_IFSTATEMENT(condition, eThen))
            } ?: fail("missing then block")
        } ?: fail("missing condition")
        return null
    }

    // Parse for loop in its various forms
    private fun pForLoop(): N_STATEMENT? {
        consume(T_FOR) ?: return null
        val withParen = consume(T_PAREN_OPEN)
        return if (nextAre(T_IDENTIFIER, T_IN)) {
            pForLoopOnValueOrRange(withParen)
        } else {
            pForLoopThreeArg(withParen)
        }
    }

    // Parse for loop over a value or range
    private fun pForLoopOnValueOrRange(withParen: Token?): N_STATEMENT? {
        consume(T_IDENTIFIER)!!.also { indexIdent ->
            consume(T_IN)
            val index = node(N_IDENTIFIER(indexIdent.string))
            pExpression()?.also { target1 ->
                consume(T_DOTDOT)?.also {
                    // "for (x in start..end)..."
                    pExpression()?.also { target2 ->
                        withParen?.also { expectCloseParen() }
                        pStatement()?.also { body ->
                            return node(N_FORRANGE(index, target1, target2, body))
                        } ?: fail("missing for-loop body")
                    } ?: fail("missing end of range")
                } ?: run {
                    // "for (x in value)..."
                    withParen?.also { expectCloseParen() }
                    pStatement()?.also { body ->
                        return node(N_FORVALUE(index, target1, body))
                    } ?: fail("missing for-loop body")
                }
            } ?: fail("missing expression after: for (var in...")
        }
        return null
    }

    // Parse: for (<init>;<check>;<increment>) <statement>
    private fun pForLoopThreeArg(withParen: Token?): N_STATEMENT? {
        pStatement()?.also { assign ->
            consume(T_SEMICOLON) ?: fail("expected semicolon after for-loop index assignment")
            pExpression()?.also { check ->
                consume(T_SEMICOLON) ?: fail("expected semicolon after for-loop check")
                pStatement()?.also { increment ->
                    withParen?.also { expectCloseParen() }
                    pStatement()?.also { body ->
                        return node(N_FORLOOP(assign, check, increment, body))
                    } ?: fail("missing for-loop body")
                } ?: fail("missing for-loop increment statement")
            } ?: fail("missing for-loop check expression")
        } ?: fail("missing for-loop init statement")
        return null
    }

    // Parse: while <expr> <statement>
    private fun pWhileLoop(): N_STATEMENT? {
        consume(T_WHILE) ?: return null
        pExpression()?.also { check ->
            pStatement()?.also { body ->
                return node(N_WHILELOOP(check, body))
            } ?: fail("missing while body")
        } ?: fail("missing while check expression")
        return null
    }

    // Parse: return [<expr>]
    private fun pReturn(): N_STATEMENT? {
        consume(T_RETURN) ?: return null
        return node(N_RETURN(pExpression()))
    }

    // Parse: fail <expr>
    private fun pFail(): N_STATEMENT? {
        consume(T_FAIL) ?: return null
        pExpression()?.also { return node(N_FAIL(it)) }
            ?: fail("missing message expression for fail")
        return null
    }

    // Parse: <ident>++|-- / ++|--<ident>
    private fun pIncrement(): N_STATEMENT? {
        fun make(ident: String, isDec: Boolean): N_STATEMENT {
            val identifier = node(N_IDENTIFIER(ident))
            return node(N_INCREMENT(identifier, isDec))
        }
        consume(T_INCREMENT, T_DECREMENT)?.also { operator ->
            consume(T_IDENTIFIER)?.also { ident ->
                return make(ident.string, operator.type == T_DECREMENT)
            } ?: fail("increment missing identifier")
        }
        if (nextIs(T_IDENTIFIER) && nextToken(1).type in listOf(T_INCREMENT, T_DECREMENT)) {
            return make(consume().string, consume().type == T_DECREMENT)
        }
        return null
    }

    // Parse: <expr> = <expr>
    // This should run last in statement parsing, since it consumes expressions.
    private fun pAssign(): N_STATEMENT? {
        pExpression()?.also { left ->
            if (nextIs(T_ASSIGN, T_ADD_ASSIGN, T_SUBTRACT_ASSIGN, T_MULT_ASSIGN, T_DIV_ASSIGN)) {
                val operator = consume()
                pExpression()?.also { right ->
                    return node(when (operator.type) {
                        T_ADD_ASSIGN -> N_ASSIGN(left, node(N_ADD(left, right)))
                        T_SUBTRACT_ASSIGN -> N_ASSIGN(left, node(N_SUBTRACT(left, right)))
                        T_MULT_ASSIGN -> N_ASSIGN(left, node(N_MULTIPLY(left, right)))
                        T_DIV_ASSIGN -> N_ASSIGN(left, node(N_DIVIDE(left, right)))
                        else -> N_ASSIGN(left, right)
                    })
                } ?: fail("missing predicate for assignment")
            }
            return node(N_EXPRSTATEMENT(left))
        }
        return null
    }

    // Parse a bare expression whose return value is ignored.
    private fun pExprStatement(): N_STATEMENT? {
        pExpression()?.also { expr ->
            return node(N_EXPRSTATEMENT(expr))
        }
        return null
    }

    // Expressions

    // Parse any expression we find next.
    // Start with the lowest precedence operation, passing down to look for higher precedence operations.
    private fun pExpression(): N_EXPR? {
        val next = this::pAndOr
        return next()
    }

    // Parse: <expr> and|or <expr>
    private fun pAndOr(): N_EXPR? {
        val next = this::pConditional
        var left = next() ?: return null
        while (nextIs(T_AND, T_OR)) {
            val operator = consume()
            next()?.also { right ->
                left = node(if (operator.type == T_AND) N_AND(left, right)
                            else N_OR(left, right))
            }
        }
        return left
    }

    // Parse: <expr> ? <expr> : <expr>
    private fun pConditional(): N_EXPR? {
        val next = this::pIn
        val left = next() ?: return null
        consume(T_QUESTION)?.also {
            next()?.also { mid ->
                consume(T_COLON)?.also {
                    next()?.also { right ->
                        return node(N_CONDITIONAL(left, mid, right))
                    } ?: fail("incomplete conditional")
                } ?: fail("missing colon in conditional")
            } ?: fail("incomplete condition")
        }
        return left
    }

    // Parse: <expr> in <expr>
    private fun pIn(): N_EXPR? {
        val next = this::pEquals
        var left = next() ?: return null
        consume(T_IN)?.also {
            next()?.also { right ->
                left = node(N_IN(left, right))
            }
        }
        return left
    }

    // Parse: <expr> ==|!= <expr>
    private fun pEquals(): N_EXPR? {
        val next = this::pCompare
        var left = next() ?: return null
        while (nextIs(T_EQUALS, T_NOTEQUALS)) {
            val operator = consume()
            next()?.also { right ->
                left = node(if (operator.type == T_EQUALS) N_CMP_EQ(left, right)
                            else N_CMP_NEQ(left, right))
            }
        }
        return left
    }

    // Parse: <expr> >|>=|<|<= <expr>
    private fun pCompare(): N_EXPR? {
        val next = this::pAdd
        var left = next() ?: return null
        while (nextIs(T_GREATER_THAN, T_GREATER_EQUAL, T_LESS_THAN, T_LESS_EQUAL)) {
            val operator = consume()
            next()?.also { right ->
                left = node(when (operator.type) {
                    T_GREATER_THAN -> N_CMP_GT(left, right)
                    T_GREATER_EQUAL -> N_CMP_GE(left, right)
                    T_LESS_THAN -> N_CMP_LT(left, right)
                    else -> N_CMP_LE(left, right)
                })
            }
        }
        return left
    }

    // Parse: <expr> +|- <expr>
    private fun pAdd(): N_EXPR? {
        val next = this::pPower
        var left = next() ?: return null
        while (nextIs(T_PLUS, T_MINUS)) {
            val operator = consume()
            next()?.also { right ->
                left = node(if (operator.type == T_PLUS) N_ADD(left, right)
                            else N_SUBTRACT(left, right))
            }
        }
        return left
    }

    // Parse: <expr> ^ <expr>
    private fun pPower(): N_EXPR? {
        val next = this::pMultiply
        var left = next() ?: return null
        consume(T_POWER)?.also {
            next()?.also { right ->
                left = node(N_POWER(left, right))
            }
        }
        return left
    }

    // Parse: <expr> *|/|^ <expr>
    private fun pMultiply(): N_EXPR? {
        val next = this::pInverse
        var left = next() ?: return null
        while (nextIs(T_MULTIPLY, T_DIVIDE, T_MODULUS)) {
            val operator = consume()
            next()?.also { right ->
                left = node(when (operator.type) {
                    T_MULTIPLY -> N_MULTIPLY(left, right)
                    T_DIVIDE -> N_DIVIDE(left, right)
                    else -> N_MODULUS(left, right)
                })
            }
        }
        return left
    }

    // Parse: !|-<expr>
    private fun pInverse(): N_EXPR? {
        val next = this::pIfElse
        consume(T_BANG, T_MINUS)?.also { operator ->
            pExpression()?.also { right ->
                return node(N_NEGATE(right))
            } ?: fail("expression expected after $operator")
        }
        return next()
    }

    // Parse (as expression): if <expr> <expr> else <expr>
    private fun pIfElse(): N_EXPR? {
        val next = this::pReference
        consume(T_IF)?.also {
            pExpression()?.also { condition ->
                pExpression()?.also { eThen ->
                    consume(T_ELSE)?.also {
                        pExpression()?.also { eElse ->
                            return node(N_CONDITIONAL(condition, eThen, eElse))
                        } ?: fail("missing else expression")
                    } ?: fail("expected else in if expression")
                } ?: fail("missing expression")
            } ?: fail("missing condition")
        }
        return next()
    }

    // Parse an index ref: <expr>[<expr>] or a dotref: <expr>.<expr>
    // We parse these together so they can be linearly chained.
    private fun pReference(): N_EXPR? {

        fun pIndex(left: N_EXPR): N_EXPR {
            consume(T_BRACKET_OPEN)
            var newLeft = left
            pExpression()?.also { index ->
                consume(T_DOTDOT)?.also {
                    pExpression()?.also { index2 ->
                        newLeft = node(N_RANGE(left, index, index2))
                    } ?: fail("expression expected after range token")
                } ?: run {
                    newLeft = node(N_INDEX(left, index))
                }
            } ?: fail("expression expected for index reference")
            consume(T_BRACKET_CLOSE) ?: fail("missing close bracket for index reference")
            return newLeft
        }

        fun pDotref(left: N_EXPR): N_EXPR {
            consume(T_DOT)
            var newLeft = left
            pStringSub()?.also { right ->
                consume(T_PAREN_OPEN)?.also {
                    val args = mutableListOf<N_EXPR>()
                    var moreArgs = true
                    while (moreArgs) {
                        pExpression()?.also { arg ->
                            args.add(arg)
                            consume(T_COMMA) ?: run { moreArgs = false }
                        } ?: run { moreArgs = false }
                    }
                    expectCloseParen()
                    newLeft = node(N_VERBREF(left, right, args))
                } ?: run {
                    newLeft = node(N_PROPREF(left, right))
                }
            } ?: fail("expression expected after dot reference")
            return newLeft
        }

        val next = this::pTrait
        var left = next() ?: return null
        while (nextIs(T_BRACKET_OPEN, T_DOT)) {
            val operator = consume()
            when (operator.type) {
                T_BRACKET_OPEN -> { left = pIndex(left) }
                T_DOT -> { left = pDotref(left) }
                else -> { }
            }
        }
        return left
    }

    // Parse a trait reference: $<expr>
    private fun pTrait(): N_EXPR? {
        val next = this::pStringSub
        consume(T_DOLLAR)?.also {
            next()?.also { expr ->
                return node(N_TRAITREF(expr))
            } ?: fail("expected expression after $")
        }
        return next()
    }

    // Parse a substituted string, consuming all its parts.
    private fun pStringSub(): N_EXPR? {
        val next = this::pValue
        if (nextIs(T_STRING_SUB_START)) {
            val parts = mutableListOf<N_EXPR>()
            while (nextIs(T_STRING_SUB_START)) {
                val start = consume()
                parts.add(node(N_LITERAL_STRING(start.string)))
                pExpression()?.also { parts.add(it) } ?: fail("expression expected in string sub")
                consume(T_STRING_SUB_END) ?: fail("unterminated string sub")
                consume(T_STRING)?.also { parts.add(node(N_LITERAL_STRING(it.string))) }
            }
            return node(N_STRING_SUB(parts))
        }
        return next()
    }

    // Parse a bare value (a literal, or a variable identifier)
    private fun pValue(): N_EXPR? {
        val next = this::pCollection
        consume(T_STRING)?.also { return node(N_LITERAL_STRING(it.string)) }
        consume(T_INTEGER)?.also { return node(N_LITERAL_INTEGER(it.string.toInt())) }
        consume(T_FLOAT)?.also { return node(N_LITERAL_FLOAT(it.string.toFloat())) }
        consume(T_IDENTIFIER)?.also { return node(N_IDENTIFIER(it.string)) }
        consume(T_TRUE, T_FALSE)?.also { return node(N_LITERAL_BOOLEAN(it.type == T_TRUE)) }
        return next()
    }

    // Parse a literal list or map: [<expr>, ...] or [<expr>:<expr>, ...]
    private fun pCollection(): N_EXPR? {
        val next = this::pParens
        consume(T_BRACKET_OPEN)?.also {
            var done = false
            var isMap: Boolean? = null
            val listArgs = mutableListOf<N_EXPR>()
            val mapArgs = mutableMapOf<N_EXPR, N_EXPR>()
            while (!done) {
                pExpression()?.also { arg ->
                    consume(T_COLON)?.also {
                        if (isMap == false) fail("colon in list element (was this supposed to be a map?)")
                        isMap = true
                        pExpression()?.also { value ->
                            mapArgs[arg] = value
                        } ?: fail("expression expected for map element value")
                    } ?: run {
                        if (isMap == true) fail("colon missing in map element")
                        isMap = false
                        listArgs.add(arg)
                    }
                }
                consume(T_COMMA) ?: run { done = true }
            }
            consume(T_BRACKET_CLOSE)?.also {
                return node(if (isMap == true) N_LITERAL_MAP(mapArgs) else N_LITERAL_LIST(listArgs))
            } ?: fail("missing close bracket on collection literal")
        }
        return next()
    }

    // Parse: (<expr>)
    private fun pParens(): N_EXPR? {
        consume(T_PAREN_OPEN)?.also {
            pExpression()?.also { expr ->
                expectCloseParen()
                return node(N_PARENS(expr))
            } ?: fail("non-expressions in parens")
        }
        return null
    }

}
