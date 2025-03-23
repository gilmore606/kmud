package com.dlfsystems.compiler

import com.dlfsystems.compiler.ast.Node
import com.dlfsystems.vm.VMWord


class CompileException(m: String, lineNum: Int, charNum: Int): Exception("$m at line $lineNum c$charNum") {
    var code: List<VMWord>? = null
    var variableIDs: Map<String, Int>? = null
    var tokens: List<Token>? = null
    var ast: Node? = null

    fun withInfo(c: List<VMWord>?, vids: Map<String, Int>?, t: List<Token>?, a: Node?): CompileException {
        code = c
        variableIDs = vids
        tokens = t
        ast = a
        return this
    }
}
