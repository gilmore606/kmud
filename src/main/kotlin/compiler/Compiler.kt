package com.dlfsystems.compiler

import com.dlfsystems.server.Yegg
import com.dlfsystems.app.Log
import com.dlfsystems.compiler.ast.Node
import com.dlfsystems.server.Connection
import com.dlfsystems.value.VString
import com.dlfsystems.vm.*

object Compiler {

    class Result(
        val source: String,
        val code: List<VMWord>,
        val symbols: Map<String, Int>,
        val tokens: List<Token>,
        val ast: Node,
    )

    fun compile(source: String): Result {
        var tokens: List<Token>? = null
        var code: List<VMWord>? = null
        var ast: Node? = null
        var symbols: Map<String, Int>? = null
        try {
            // Stage 1: Lex source into tokens.
            tokens = Lexer(source).lex()
            // Stage 2: Parse tokens into AST nodes.
            val parser = Parser(tokens)
            // Stage 3: Find variables.
            val shaker = Shaker(parser.parse())
            ast = shaker.shake()
            symbols = shaker.symbols
            // Stage 4: Generate VM opcodes.
            code = Coder(ast).generate()
            return Result(source, code, symbols, tokens, ast)
        } catch (e: CompileException) {
            throw e.withInfo(code, symbols, tokens, ast)
        } catch (e: Exception) {
            throw CompileException("GURU: " + e.stackTraceToString(), 0, 0)
                .withInfo(code, symbols, tokens, ast)
        }
    }

    fun eval(c: Context, code: String, verbose: Boolean = false): String {
        Log.d("eval: $code")
        c.push(Yegg.vNullObj, Yegg.vNullTrait, "(eval)", listOf(VString(code)))
        var cOut: Result? = null
        try {
            cOut = compile(code)
            Log.d("  opcodes: \n${cOut.code.dumpText()}")
            val vmOut = VM(cOut.code, cOut.symbols).execute(c).toString()
            return if (verbose) dumpText(cOut.tokens, cOut.ast, cOut.code, vmOut) else vmOut
        } catch (e: CompileException) {
            return if (verbose) dumpText(e.tokens, e.ast, e.code, "") else e.toString()
        } catch (e: Exception) {
            return if (verbose) dumpText(cOut?.tokens, cOut?.ast, cOut?.code, "") else "$e\n${c.stackDump()}"
        }
    }

    private fun dumpText(tokens: List<Token>?, ast: Node?, code: List<VMWord>?, result: String?): String =
        "TOKENS:\n${tokens}\n\nNODES:\n${ast}\n\nCODE:\n${code?.dumpText()}\nRESULT:\n$result\n"

}
