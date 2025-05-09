package com.dlfsystems.compiler

import com.dlfsystems.server.Yegg
import com.dlfsystems.compiler.ast.Node
import com.dlfsystems.vm.*
import com.dlfsystems.world.trait.Verb

object Compiler {

    class Result(
        val source: String,
        val code: List<VMWord>,
        val symbols: Map<String, Int>,
        val tokens: List<Token>,
        val ast: Node,
        val entryPoints: List<Int>,
    )

    fun compile(source: String): Result {
        var tokens: List<Token>? = null
        var code: List<VMWord>? = null
        var ast: Node? = null
        var symbols: Map<String, Int>? = null
        var entryPoints: List<Int>? = null
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
            val coder = Coder(ast).apply { generate() }
            code = coder.mem
            entryPoints = coder.entryPoints
            return Result(source, code, symbols, tokens, ast, entryPoints)
        } catch (e: CompileException) {
            throw e.withInfo(code, symbols, tokens, ast)
        } catch (e: Exception) {
            throw CompileException("GURU: " + e.stackTraceToString(), 0, 0)
                .withInfo(code, symbols, tokens, ast)
        }
    }

    fun eval(c: Context, source: String, verbose: Boolean = false): String {
        var verb: Verb? = null
        try {
            verb = Verb("eval", Yegg.world.sys.id).apply { program(source) }
            val vmOut = verb.call(c, Yegg.vNullObj, listOf()).toString()
            return if (verbose) dumpText(verb.code, vmOut) else vmOut
        } catch (e: CompileException) {
            return if (verbose) dumpText(verb?.code, "") else e.toString()
        } catch (e: Exception) {
            return if (verbose) dumpText(verb?.code, "") else "$e\n${c.stackDump()}"
        }
    }

    private fun dumpText(code: List<VMWord>?, result: String?): String =
        "CODE:\n${code?.dumpText()}\nRESULT:\n$result\n"

}
