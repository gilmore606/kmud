package com.dlfsystems.server

import kotlinx.serialization.Serializable
import com.dlfsystems.server.Command.Arg.*

@Serializable
class Command(
    val names: List<String>,
    val dobj: Arg? = null,
    val prep: Preposition? = null,
    val iobj: Arg? = null,
    val verb: String,
) {

    @Serializable
    sealed class Arg(val s: String) {
        override fun toString() = s
        @Serializable data object Text: Arg("text")
        @Serializable data object This: Arg("this")
        @Serializable data object Any: Arg("any")
    }

    override fun toString() = names.joinToString("/") + " " +
        (dobj?.s ?: "none") + " " +
        (prep?.strings?.joinToString("/") ?: "none") + " " +
        (iobj?.s ?: "none")

    companion object {
        // Generate a Command from a string representation.
        // "who = cmdWho"
        // "get/take contents from this = cmdGetFrom"
        // "switch/turn on this = cmdSwitchOn"
        // "co*nnect text = cmdConnect"
        fun fromString(s: String): Command? {
            val specAndVerb = s.split(" = ")
            if (specAndVerb.size != 2) return null
            val verb = specAndVerb[1]
            val namesAndArgs = specAndVerb[0].split(" ", limit = 2)
            val names = namesAndArgs[0].split("/")
            if (namesAndArgs.size == 1) return Command(names, verb = verb)
            val args = namesAndArgs[1].split(" ")
            if (args.size > 3) return null
            var dobj: Arg? = null
            var prep: Preposition? = null
            var iobj: Arg? = null
            args.forEach { arg ->
                listOf(Text, This, Any).firstOrNull { it.s == arg }?.also { argType ->
                    if (dobj == null && prep == null) dobj = argType else iobj = argType
                } ?: run {
                    Preposition.entries.firstOrNull { it.strings.contains(arg) }?.also { prepType ->
                        prep = prepType
                    } ?: run {
                        return null
                    }
                }
            }
            return Command(names, dobj, prep, iobj, verb)
        }
    }
}
