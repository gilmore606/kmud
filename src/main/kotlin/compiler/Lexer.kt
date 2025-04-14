package com.dlfsystems.compiler

import com.dlfsystems.compiler.TokenType.*

// Take an input string and produce a stream of tokens.

class Lexer(val source: String) {

    // Internal buffer for output tokens.  We produce these as we lex.
    private val tokens = ArrayList<Token>()

    // Are we constructing some kind of token?
    private var tokenType: TokenType? = null
    // If so, buffer its characters as we get them.
    private var tokenString: String = ""

    // Are we expecting a backslashed escaped string char next?
    private var inStringEscape: Boolean = false
    // Are we inside a string $var substitution?
    private var inStringVarsub: Boolean = false
    // Are we inside a string ${code} substitution?
    private var inStringCodesub: Boolean = false

    // Track line and char position as we go, to tag tokens for tracebacks.
    var lineNum: Int = 0
    var charNum: Int = 0

    private fun fail(m: String) { throw CompileException(m, lineNum, charNum) }


    //
    // Scan our input string and generate a stream of tokens.
    //
    fun lex(): List<Token> {
        source.forEach { c ->
            consume(c)
            if (c == '\n') {
                lineNum++
                charNum = 0
            } else {
                charNum++
            }
        }
        consume('\n')
        if (tokenType == T_STRING) fail("unterminated string")
        if (tokenType != null) fail("incomplete token")
        return tokens
    }


    // Consume a character and, depending on what kind of token we might already be in, possibly generate a completed token.
    private fun consume(c: Char) {
        if (inStringVarsub) {
            if (c == '{') { inStringVarsub = false ; inStringCodesub = true; tokenType = null; tokenString = "" }
            else if (isIdentifierChar(c)) accumulate(c)
            else {
                inStringVarsub = false
                emit(T_IDENTIFIER)
                emit(T_STRING_SUB_END)
                when (c) {
                    '\"' -> { }
                    '\$' -> { emit(T_STRING_SUB_START) ; begin(T_IDENTIFIER) ; inStringVarsub = true }
                    else -> begin(T_STRING, c)
                }
            }
        } else when (tokenType) {
            T_ASSIGN ->
                if (c == '=') emit(T_EQUALS) else emit(T_ASSIGN, c)
            T_NOTEQUALS ->
                if (c == '=') emit(T_NOTEQUALS) else emit(T_BANG, c)
            T_GREATER_THAN ->
                if (c == '=') emit(T_GREATER_EQUAL) else emit(T_GREATER_THAN, c)
            T_LESS_THAN ->
                if (c == '=') emit(T_LESS_EQUAL) else emit(T_LESS_THAN, c)
            T_PLUS -> when (c) {
                '=' -> emit(T_ADD_ASSIGN)
                '+' -> emit(T_INCREMENT)
                else -> emit(T_PLUS, c)
            }
            T_MINUS -> when (c) {
                in '0'..'9' -> if (negateOK()) begin(T_INTEGER, c) else emit(T_MINUS, c)
                '.' -> if (negateOK()) begin(T_FLOAT, c) else emit(T_MINUS, c)
                '=' -> emit(T_SUBTRACT_ASSIGN)
                '-' -> emit(T_DECREMENT)
                '>' -> emit(T_ARROW)
                else -> emit(T_MINUS, c)
            }
            T_DOT -> when (c) {
                '.' -> emit(T_DOTDOT)
                else -> emit(T_DOT, c)
            }
            T_MULTIPLY ->
                if (c == '=') emit(T_MULT_ASSIGN) else emit(T_MULTIPLY, c)
            T_DIVIDE -> when (c) {
                '/' -> begin(T_COMMENT)
                '=' -> emit(T_DIV_ASSIGN)
                else -> emit(T_DIVIDE, c)
            }
            T_LOGIC_OR ->
                if (c == '|') emit(T_LOGIC_OR) else fail("expected ||")
            T_LOGIC_AND ->
                if (c == '&') emit(T_LOGIC_AND) else fail("expected &&")
            T_STRING ->
                if (inStringEscape) {
                    when (c) {
                        'n' -> accumulate('\n')
                        else -> accumulate(c)
                    }
                    inStringEscape = false
                } else if (c == '\\') inStringEscape = true
                else if (c == '$') { emit(T_STRING_SUB_START) ; begin(T_IDENTIFIER) ; inStringVarsub = true }
                else if (c == '"') emit(T_STRING)
                else accumulate(c)
            T_COMMENT ->
                if (c == '\n') emit(T_COMMENT) else accumulate(c)
            T_INTEGER -> when (c) {
                in '0'..'9' -> accumulate(c)
                '.' -> begin(T_FLOAT, c)
                else -> emit(T_INTEGER, c)
            }
            T_FLOAT -> when (c) {
                '.' -> {
                    tokenString = tokenString.filter { it.isDigit() }
                    emit(T_INTEGER)
                    emit(T_DOTDOT)
                }
                in '0'..'9' -> accumulate(c)
                else -> emit(T_FLOAT, c)
            }
            T_IDENTIFIER -> {
                if (isIdentifierChar(c)) accumulate(c) else emit(T_IDENTIFIER, c)
            }
            else -> {
                when (c) {
                    ' ', '\t', '\n' -> { }
                    in '0'..'9' -> begin(T_INTEGER, c)
                    '}' -> if (inStringCodesub) { emit(T_STRING_SUB_END) ; begin(T_STRING) ; inStringCodesub = false }
                            else emit(T_BRACE_CLOSE)
                    '"' -> begin(T_STRING)
                    '=' -> begin(T_ASSIGN)
                    '>' -> begin(T_GREATER_THAN)
                    '<' -> begin(T_LESS_THAN)
                    '+' -> begin(T_PLUS)
                    '-' -> begin(T_MINUS, c)
                    '*' -> begin(T_MULTIPLY)
                    '/' -> begin(T_DIVIDE)
                    '!' -> begin(T_NOTEQUALS)
                    '|' -> begin(T_LOGIC_OR)
                    '&' -> begin(T_LOGIC_AND)
                    '.' -> begin(T_DOT)
                    '(' -> emit(T_PAREN_OPEN)
                    ')' -> emit(T_PAREN_CLOSE)
                    '[' -> emit(T_BRACKET_OPEN)
                    ']' -> emit(T_BRACKET_CLOSE)
                    '{' -> emit(T_BRACE_OPEN)
                    ':' -> emit(T_COLON)
                    ';' -> emit(T_SEMICOLON)
                    '$' -> emit(T_DOLLAR)
                    ',' -> emit(T_COMMA)
                    '?' -> emit(T_QUESTION)
                    '^' -> emit(T_POWER)
                    '@' -> emit(T_AT)
                    '%' -> emit(T_MODULUS)
                    else -> {
                        if (isIdentifierChar(c)) begin(T_IDENTIFIER, c)
                        else fail("unexpected character: $c")
                    }
                }
            }
        }
    }

    // Given the previous token, is it safe to assume '-' is part of a negated value, or is it a minus operator?
    private fun negateOK() =
        tokens.isEmpty() || tokens.last().type !in listOf(T_PAREN_CLOSE, T_INTEGER, T_FLOAT, T_IDENTIFIER)

    // Is this a legal char for an identifier name?
    private fun isIdentifierChar(c: Char) = (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || (c == '_')

    // Begin a new accumulating token of (possibly) type.  Start with acc if given.
    private fun begin(type: TokenType, acc: Char? = null) {
        tokenType = type
        acc?.also { accumulate(it) }
    }

    // Add this char to the token string we're currently accumulating.
    private fun accumulate(c: Char) {
        tokenString += c
    }

    // Emit a discovered token.  Reconsume the triggering character, if given.
    private fun emit(tokenType: TokenType, reconsume: Char? = null) {
        var newType = tokenType
        if (tokenType == T_IDENTIFIER) {
            TokenType.entries.firstOrNull { it.isKeyword && it.literal == tokenString }?.also {
                newType = it
            }
        }
        if (tokenType != T_COMMENT) { // don't actually emit comment tokens at all
            tokens.add(Token(newType, tokenString, lineNum, charNum))
        }
        tokenString = ""
        this.tokenType = null
        reconsume?.also { consume(it) }
    }

}
