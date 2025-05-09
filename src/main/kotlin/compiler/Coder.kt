package com.dlfsystems.compiler

import com.dlfsystems.compiler.ast.Node
import com.dlfsystems.vm.Opcode
import com.dlfsystems.vm.Opcode.*
import com.dlfsystems.vm.VMWord
import com.dlfsystems.value.*
import com.dlfsystems.world.ObjID

class Coder(val ast: Node) {

    var mem = mutableListOf<VMWord>()

    // Addresses to be filled in with a named jump point once coded.
    val forwardJumps = HashMap<String, MutableSet<Int>>()
    // Addresses stored to be used as future jump destinations.
    val backJumps = HashMap<String, Int>()
    // Entry points to literal VFuns.
    val entryPoints = mutableListOf<Int>()

    fun last() = if (mem.isEmpty()) null else mem[mem.size - 1]

    // Compile the AST into a list of opcodes, by recursively asking the nodes to code themselves.
    // Nodes will then call Coder.code() and Coder.value() to output their compiled code.
    fun generate() {
        ast.code(this)
        Optimizer(this).optimize()
    }

    // Write an opcode into memory.
    fun code(from: Node, op: Opcode) {
        mem.add(VMWord(from.lineNum, from.charNum, opcode = op))
    }

    // Write a Value into memory, as an argument to the previous opcode.
    fun value(from: Node, value: Value) {
        mem.add(VMWord(from.lineNum, from.charNum, value = value))
    }
    fun value(from: Node, intValue: Int) { value(from, VInt(intValue)) }
    fun value(from: Node, boolValue: Boolean) { value(from, VBool(boolValue)) }
    fun value(from: Node, floatValue: Float) { value(from, VFloat(floatValue)) }
    fun value(from: Node, stringValue: String) { value(from, VString(stringValue)) }
    fun value(from: Node, objValue: ObjID) { value(from, VObj(objValue)) }
    fun value(from: Node, listValue: List<Value>) { value(from, VList(listValue.toMutableList())) }
    fun value(from: Node, mapValue: Map<Value, Value>) { value(from, VMap(mapValue.toMutableMap())) }

    // Record the entryPoint of a literal VFun.
    fun codeEntryPoint(from: Node) {
        value(from, entryPoints.size)
        entryPoints.add(mem.size + 2) // +2 to skip the O_JUMP<addr> which will follow
    }

    // Write a placeholder address for a jump we'll locate in the future.
    // Nodes call this to jump to a named future address.
    fun jumpForward(from: Node, name: String) {
        val address = mem.size
        val fullname = "$name${from.id}"
        if (forwardJumps.containsKey(fullname)) {
            forwardJumps[fullname]!!.add(address)
        } else {
            forwardJumps[fullname] = mutableSetOf(address)
        }
        mem.add(VMWord(from.lineNum, from.charNum, address = -1))
    }

    // Reach a previously named jump point.  Fill in all previous references with the current address.
    // Nodes call this when a previously named jumpForward address is reached.
    fun setForwardJump(from: Node, name: String) {
        val dest = mem.size
        val fullname = "$name${from.id}"
        forwardJumps[fullname]!!.forEach { loc ->
            mem[loc].fillAddress(dest)
        }
        forwardJumps.remove(fullname)
    }

    // Record a jump address we'll jump back to later.
    // Nodes call this to mark a named address which they'll code a jumpBack to.
    fun setBackJump(from: Node, name: String) {
        val dest = mem.size
        val fullname = "$name${from.id}"
        backJumps[fullname] = dest
    }

    // Write address of a jump located in the past.
    // Nodes call this to jump to a named past address.
    fun jumpBack(from: Node, name: String) {
        val fullname = "$name${from.id}"
        val dest = backJumps[fullname]
        mem.add(VMWord(from.lineNum, from.charNum, address = dest))
    }


    class Optimizer(private val coder: Coder) {
        private val mem = coder.mem
        private val outMem = mutableListOf<VMWord>()
        private val jumpMap = mutableMapOf<Int, Int>()
        private val entryMap = mutableListOf<Int>()
        private var pc = 0
        private var lastMatchSize = 0

        fun optimize() {

            // Find all jump destinations in source
            mem.forEach { word ->
                word.address?.also { address ->
                    jumpMap[address] = -1
                }
            }
            entryMap.addAll(coder.entryPoints)

            pc = 0
            while (pc < mem.size) {
                // If we've reached a jump dest, record its new address
                if (jumpMap.containsKey(pc)) jumpMap[pc] = outMem.size
                // If we've reached an entry point, record its new address
                if (coder.entryPoints.contains(pc)) {
                    entryMap[coder.entryPoints.indexOf(pc)] = outMem.size
                }

                // NEGATE NEGATE => ()
                consume(O_NEGATE, O_NEGATE)?.also { }

                // SETVAR GETVAR => SETGETVAR
                ?: consume(O_SETVAR, null, O_GETVAR, null) { args ->
                    args[0].isInt() && args[1].isInt(args[0].intFromV)
                }?.also { args ->
                    code(O_SETGETVAR)
                    value(args[0].value!!)
                }

                // O_VAL 0 O_CMP_xx => O_CMP_xxZ
                ?: consume(O_VAL, null, O_CMP_EQ) { args -> args[0].isInt(0) }?.also { code(O_CMP_EQZ) }
                ?: consume(O_VAL, null, O_CMP_GT) { args -> args[0].isInt(0) }?.also { code(O_CMP_GTZ) }
                ?: consume(O_VAL, null, O_CMP_GE) { args -> args[0].isInt(0) }?.also { code(O_CMP_GEZ) }
                ?: consume(O_VAL, null, O_CMP_LT) { args -> args[0].isInt(0) }?.also { code(O_CMP_LTZ) }
                ?: consume(O_VAL, null, O_CMP_LE) { args -> args[0].isInt(0) }?.also { code(O_CMP_LEZ) }

                // O_GETVAR O_CMP_EQ O_IF => O_IFVAREQ
                ?: consume(O_GETVAR, null, O_CMP_EQ, O_IF, null)?.also { args ->
                    code(O_IFVAREQ)
                    value(args[0].value!!)
                    address(args[1].address!!)
                }

                // O_VAL O_RETURN => O_RETVAL
                ?: consume(O_VAL, null, O_RETURN)?.also { args ->
                    code(O_RETVAL)
                    value(args[0].value!!)
                }

                // O_GETVAR O_RETURN => O_RETVAR
                ?: consume(O_GETVAR, null, O_RETURN)?.also { args ->
                    code(O_RETVAR)
                    value(args[0].value!!)
                }

                // If nothing matched, copy and continue
                ?: run {
                    outMem.add(mem[pc++])
                }
            }

            // Replace all jump dests
            jumpMap.keys.forEach { old ->
                outMem.forEach { word ->
                    if (word.address == old) word.address = jumpMap[old]
                }
            }
            // Replace all entry points
            coder.entryPoints.addAll(entryMap)
            // Replace compiled code
            coder.mem = outMem
        }

        // Match and consume a series of opcodes (or null for any non-opcode word).
        private fun consume(vararg opcodes: Opcode?, check: ((List<VMWord>)->Boolean)? = null): List<VMWord>? {
            if (opcodes.size > (mem.size - pc)) return null
            var hit = true
            val nulls = mutableListOf<VMWord>()
            opcodes.forEachIndexed { i, t ->
                if (t == null) nulls.add(mem[pc + i])
                else if ((pc + i) in jumpMap.keys) hit = false  // Miss if we overlap a jump dest
                else if (mem[pc + i].opcode != t) hit = false
            }
            if (hit && (check?.invoke(nulls) != false)) {
                pc += opcodes.size
                lastMatchSize = opcodes.size
                return nulls
            }
            return null
        }

        private fun code(op: Opcode) {
            val oldword = mem[pc - lastMatchSize]
            outMem.add(VMWord(oldword.lineNum, oldword.charNum, op))
        }
        private fun value(v: Value) {
            val oldword = mem[pc - lastMatchSize]
            outMem.add(VMWord(oldword.lineNum, oldword.charNum, value = v))
        }
        private fun address(a: Int) {
            val oldword = mem[pc - lastMatchSize]
            outMem.add(VMWord(oldword.lineNum, oldword.charNum, address = a))
        }
    }
}
