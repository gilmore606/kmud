@file:Suppress("NOTHING_TO_INLINE")

package com.dlfsystems.vm

import com.dlfsystems.server.MCP
import com.dlfsystems.server.SuspendException
import com.dlfsystems.server.Yegg
import com.dlfsystems.value.Value
import com.dlfsystems.vm.Opcode.*
import com.dlfsystems.value.*
import com.dlfsystems.vm.VMException.Type.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// A stack machine for executing a verb.

class VM(val exe: Executable) {

    // Program Counter: index of the opcode we're about to execute (or argument we're about to fetch).
    private var pc: Int = 0
    // The local stack.
    private val stack = ArrayDeque<Value>()
    private fun dumpStack() = stack.joinToString(",") { "$it" }

    // Local variables by ID.
    private val variables: MutableMap<Int, Value> = mutableMapOf()

    // Preserve error position.
    var lineNum: Int = 0
    var charNum: Int = 0
    private fun fail(type: VMException.Type, m: String) { throw VMException(type, m) }

    private inline fun push(v: Value) = stack.addFirst(v)
    private inline fun peek() = stack.first()
    private inline fun pop() = stack.removeFirst()
    private inline fun popTwo() = listOf(stack.removeFirst(), stack.removeFirst())
    private inline fun popThree() = listOf(stack.removeFirst(), stack.removeFirst(), stack.removeFirst())
    private inline fun popFour() = listOf(stack.removeFirst(), stack.removeFirst(), stack.removeFirst(), stack.removeFirst())
    private inline fun next() = exe.code[pc++]

    fun execute(
        c: Context,
        args: List<Value> = listOf(),
        withVars: Map<String, Value>? = null
    ): Value {

        pc = 0

        withVars?.forEach { (name, value) -> initVar(name, value) }
        initVar("args", VList.make(args))
        initVar("this", c.vThis)
        initVar("user", c.vUser)

        try {
            return executeCode(c)
        } catch (e: Exception) {
            throw (e as? VMException ?: VMException(E_SYS, "${e.message} (mem $pc)\n${e.stackTraceToString()}"))
                .withLocation(lineNum, charNum)
        }
    }

    private fun initVar(name: String, value: Value) {
        exe.symbols[name]?.also { variables[it] = value }
    }

    private fun executeCode(c: Context): Value {

        val stackLimit = Yegg.world.getSysInt("stackLimit")
        var ticksLeft = c.ticksLeft

        while (pc < exe.code.size) {

            if (--ticksLeft < 0) fail(E_LIMIT, "tick limit exceeded")
            if (stack.size > stackLimit) fail(E_LIMIT, "stack depth exceeded")
            if (c.callsLeft < 1) fail(E_MAXREC, "call depth exceeded")

            val word = next()
            lineNum = word.lineNum
            charNum = word.charNum

            when (word.opcode) {

                O_DISCARD -> {
                    if (stack.isNotEmpty()) pop()
                }

                // Value ops

                O_VAL -> {
                    push(next().value!!)
                }
                O_LISTVAL -> {
                    val count = next().intFromV
                    val elements = mutableListOf<Value>()
                    repeat(count) { elements.add(0, pop()) }
                    push(VList(elements))
                }
                O_MAPVAL -> {
                    val count = next().intFromV
                    val entries = mutableMapOf<Value, Value>()
                    repeat(count) { entries.put(pop(), pop()) }
                    push(VMap(entries))
                }
                O_FUNVAL -> {
                    val block = next().intFromV
                    // Capture variables from scope
                    val withVars = buildMap {
                        (pop() as VList).v.map { (it as VString).v }.forEach { varName ->
                            exe.symbols[varName]?.also {
                                variables[it]?.also { put(varName, it) }
                            }
                        }
                    }
                    val args = (pop() as VList).v.map { (it as VString).v }
                    push(exe.getLambda(block, c.vThis, args, withVars))
                }

                // Index/range ops

                O_GETI -> {
                    val (a2, a1) = popTwo()
                    a1.getIndex(a2)?.also { push(it) }
                        ?: fail(E_TYPE, "cannot index into ${a1.type} with ${a2.type}")
                }
                O_GETRANGE -> {
                    val (a3, a2, a1) = popThree()
                    a1.getRange(a2, a3)?.also { push(it) }
                        ?: fail(E_TYPE, "cannot range into ${a1.type} with ${a2.type}..${a3.type}")
                }
                O_SETI -> {
                    val (a3, a2, a1) = popThree()
                    if (!a2.setIndex(a3, a1)) fail(E_RANGE, "cannot index into ${a2.type} with ${a3.type}")
                }
                O_SETRANGE -> {
                    val (a4, a3, a2, a1) = popFour()
                    if (!a2.setRange(a3, a4, a1)) fail(E_RANGE, "cannot range into ${a1.type} with ${a2.type}..${a3.type}")
                }

                // Control flow ops

                O_IF -> {
                    val elseAddr = next().address!!
                    val condition = pop()
                    if (condition.isFalse()) pc = elseAddr
                }
                O_IFVAREQ -> {
                    val varID = next().intFromV
                    val elseAddr = next().address!!
                    if (variables[varID] != pop()) pc = elseAddr
                }
                O_JUMP -> {
                    val addr = next().address!!
                    if (addr >= 0) pc = addr else return VVoid  // Unresolved jump dest means end-of-code
                }
                O_RETURN -> {
                    if (stack.isEmpty()) return VVoid
                    if (stack.size > 1) fail(E_SYS, "stack polluted on return!  ${dumpStack()}")
                    return pop()
                }
                O_RETURNNULL -> {
                    if (stack.isNotEmpty()) fail(E_SYS, "stack polluted on returnnull!  ${dumpStack()}")
                    return VVoid
                }
                O_RETVAL -> {
                    return next().value!!
                }
                O_RETVAR -> {
                    return variables[next().intFromV]!!
                }
                O_FAIL -> {
                    val a = pop()
                    fail(E_USER, a.asString())
                }

                // Verb ops

                O_CALL, O_VCALL, O_VCVOKE -> {
                    val argCount = next().intFromV
                    val a2 = if (word.opcode == O_CALL) pop() else next().value
                    val a1 = pop()
                    val args = mutableListOf<Value>()
                    repeat(argCount) { args.add(0, pop()) }
                    if (a2 is VString) {
                        c.ticksLeft = ticksLeft
                        c.callsLeft--
                        a1.callVerb(c, a2.v, args)?.also {
                            if (word.opcode != O_VCVOKE) push(it)
                        } ?: fail(E_VERBNF, "verb not found")
                        c.callsLeft++
                        ticksLeft = c.ticksLeft
                    } else fail(E_VERBNF, "verb name must be string")
                }
                O_FUNCALL, O_FUNVOKE -> {
                    val name = (next().value as VString).v
                    val argCount = next().intFromV
                    val args = mutableListOf<Value>()
                    repeat (argCount) { args.add(0, pop()) }
                    exe.symbols[name]?.also { variableID ->
                        var result: Value? = null
                        // Look for variable with VFun
                        variables[variableID]?.let { subject ->
                            if (subject is VFun) {
                                result = subject.verbInvoke(c, args)
                            } else fail(E_TYPE, "cannot invoke ${subject.type} as fun")
                        // Look for $sys.verb
                        } ?: Yegg.world.sys.callVerb(c, name, args)?.also {
                            result = it
                        } ?: fail(E_VARNF, "no such fun or variable")
                        result?.also { if (word.opcode == O_FUNCALL) push(it) }
                    }
                }

                // Task ops

                O_SUSPEND -> {
                    val a = pop()
                    throw SuspendException((a as VInt).v)
                }
                O_FORK -> {
                    val (a2, a1) = popTwo()
                    val taskID = MCP.schedule(Context(c.connection).apply {
                        vThis = c.vThis
                        vUser = c.vUser
                    }, a2 as VFun, listOf(), (a1 as VInt).v)
                    push(VTask(taskID))
                }

                // Variable ops

                O_GETVAR -> {
                    val varID = next().intFromV
                    variables[varID]?.also { push(it) }
                        ?: fail(E_VARNF, "variable not found")
                }
                O_SETVAR -> {
                    val varID = next().intFromV
                    val a1 = pop()
                    variables[varID] = a1
                }
                O_SETGETVAR -> {
                    variables[next().intFromV] = peek()
                }
                O_INCVAR, O_DECVAR -> {
                    val varID = next().intFromV
                    variables[varID]?.also {
                        if (it is VInt)
                            variables[varID] = VInt(it.v + if (word.opcode == O_INCVAR) 1 else -1)
                        else fail(E_TYPE, "cannot increment ${it.type}")
                    } ?: fail(E_VARNF, "variable not found")
                }
                O_DESTRUCT -> {
                    val (a2, a1) = popTwo()
                    if (a2 !is VList) fail(E_TYPE, "cannot destructure from non-list")
                    if ((a2 as VList).v.size < (a1 as VList).v.size) fail(E_RANGE, "missing args")
                    a1.v.forEachIndexed { i, vn ->
                        exe.symbols[(vn as VString).v]?.also { variables[it] = a2.v[i] }
                    }
                }

                // Iterator ops

                O_ITERSIZE -> {
                    val a1 = pop()
                    a1.iterableSize()?.also {
                        push(VInt(it))
                    } ?: fail(E_TYPE, "cannot iterate ${a1.type}")
                }
                O_ITERPICK -> {
                    val sourceID = next().intFromV
                    val indexID = next().intFromV
                    variables[sourceID]!!.getIndex(variables[indexID] as VInt)?.also { push(it) }
                        ?: fail(E_TYPE, "cannot iterate")
                }

                // Property ops

                O_GETPROP, O_VGETPROP -> {
                    val a2 = if (word.opcode == O_VGETPROP) next().value!! else pop()
                    val a1 = pop()
                    if (a2 is VString) {
                        a1.getProp(a2.v)?.also { push(it) }
                            ?: fail(E_PROPNF, "property not found")
                    } else fail(E_PROPNF, "property name must be string")
                }
                O_SETPROP -> {
                    val (a3, a2, a1) = popThree()
                    if (!a2.setProp((a3 as VString).v, a1))
                        fail(E_PROPNF, "property not found")
                }
                O_GETTRAIT -> {
                    val a1 = pop()
                    if (a1 is VString) {
                        Yegg.world.getTrait(a1.v)?.also { push(it.vTrait) }
                            ?: fail (E_TRAITNF, "trait not found")
                    } else fail(E_TRAITNF, "trait name must be string")
                }

                // Boolean ops

                O_NEGATE -> {
                    val a1 = pop()
                    a1.negate()?.also { push(it) }
                        ?: fail(E_TYPE, "cannot negate ${a1.type}")
                }
                O_AND -> {
                    val (a2, a1) = popTwo()
                    if (a1 is VBool && a2 is VBool) push(VBool(a1.v && a2.v))
                    else fail(E_TYPE, "cannot AND ${a1.type} and ${a2.type}")
                }
                O_OR -> {
                    val (a2, a1) = popTwo()
                    if (a1 is VBool && a2 is VBool) push(VBool(a1.v || a2.v))
                    else fail(E_TYPE, "cannot OR ${a1.type} and ${a2.type}")
                }
                O_IN -> {
                    val (a2, a1) = popTwo()
                    a1.isIn(a2)?.also { push(VBool(it)) }
                        ?: fail(E_TYPE, "cannot check ${a1.type} in ${a2.type}")
                }
                O_CMP_EQ, O_CMP_GT, O_CMP_GE, O_CMP_LT, O_CMP_LE -> {
                    val (a2, a1) = popTwo()
                    when (word.opcode) {
                        O_CMP_EQ -> push(VBool(a1.cmpEq(a2)))
                        O_CMP_GT -> push(VBool(a1.cmpGt(a2)))
                        O_CMP_GE -> push(VBool(a1.cmpGe(a2)))
                        O_CMP_LT -> push(VBool(a1.cmpLt(a2)))
                        O_CMP_LE -> push(VBool(a1.cmpLe(a2)))
                        else -> { }
                    }
                }
                O_CMP_EQZ -> { push(VBool(pop().cmpEq(VInt(0)))) }
                O_CMP_GTZ -> { push(VBool(pop().cmpGt(VInt(0)))) }
                O_CMP_GEZ -> { push(VBool(pop().cmpGe(VInt(0)))) }
                O_CMP_LTZ -> { push(VBool(pop().cmpLt(VInt(0)))) }
                O_CMP_LEZ -> { push(VBool(pop().cmpLe(VInt(0)))) }

                // Math ops

                O_ADD -> {
                    val (a2, a1) = popTwo()
                    a1.plus(a2)?.also { push(it) }
                        ?: fail(E_TYPE, "cannot add ${a1.type} and ${a2.type}")
                }
                O_ADDVAL -> {
                    val a1 = pop()
                    val a2 = next().value!!
                    a1.plus(a2)?.also { push(it) }
                        ?: fail(E_TYPE, "cannot add ${a1.type} and ${a2.type}")
                }
                O_CONCAT -> {
                    val (a2, a1) = popTwo()
                    val a3 = next().value!!
                    a1.plus(a2)?.also { a12 ->
                        a12.plus(a3)?.also { push(it) }
                            ?: fail(E_TYPE, "cannot add ${a12.type} and ${a3.type}")
                    } ?: fail(E_TYPE, "cannot add ${a1.type} and ${a2.type}")
                }
                O_MULT -> {
                    val (a2, a1) = popTwo()
                    a1.multiply(a2)?.also { push(it) }
                        ?: fail(E_TYPE, "cannot multiply ${a1.type} and ${a2.type}")
                }
                O_DIV -> {
                    val (a2, a1) = popTwo()
                    if (a2.isZero() || a1.isZero()) fail(E_DIV, "divide by zero")
                    a1.divide(a2)?.also { push(it) }
                        ?: fail(E_TYPE, "cannot divide ${a1.type} and ${a2.type}")
                }
                O_POWER -> {
                    val (a2, a1) = popTwo()
                    a1.toPower(a2)?.also { push(it) }
                        ?: fail(E_TYPE, "cannot raise ${a1.type} to power ${a2.type}")
                }
                O_MODULUS -> {
                    val (a2, a1) = popTwo()
                    a1.modulo(a2)?.also { push(it) }
                        ?: fail(E_TYPE, "cannot modulo ${a1.type} by ${a2.type}")
                }

                else -> fail(E_SYS, "unknown opcode $word")
            }
        }
        return if (stack.isEmpty()) VVoid else pop()
    }

}

// An atom of VM opcode memory.
// Can hold an Opcode, a Value, or an int representing a memory address (for jumps).
@Serializable
data class VMWord(
    @SerialName("l") val lineNum: Int,
    @SerialName("c") val charNum: Int,
    @SerialName("o") val opcode: Opcode? = null,
    @SerialName("v") val value: Value? = null,
    @SerialName("a") var address: Int? = null,
) {
    // In compilation, an address word may be written before the address it points to is known.
    // fillAddress is called to set it once calculated.
    fun fillAddress(newAddress: Int) { address = newAddress }

    override fun toString() = opcode?.toString() ?: value?.toString() ?: address?.let { "<$it>" } ?: "!!NULL!!"

    fun isInt(equals: Int? = null): Boolean {
        if (value !is VInt) return false
        if (equals == null) return true
        return (value.v == equals)
    }

    // If this is known to be an int opcode arg, just get the int value.
    val intFromV: Int
        get() = (value as VInt).v
}

fun List<VMWord>.dumpText(): String {
    if (isEmpty()) return "<not programmed>\n"
    var s = ""
    var pc = 0
    while (pc < size) {
        val cell = get(pc)
        s += "<$pc> "
        s += cell.toString()
        cell.opcode?.also { opcode ->
            repeat (opcode.argCount) {
                pc++
                val arg = get(pc)
                s += " $arg"
            }
        }
        s += "\n"
        pc++
    }
    return s
}
