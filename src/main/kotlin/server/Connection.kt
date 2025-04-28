package com.dlfsystems.server

import com.dlfsystems.compiler.Compiler
import com.dlfsystems.vm.Context
import com.dlfsystems.world.Obj

data class ConnectionID(val id: String) { override fun toString() = id }

class Connection(private val sendText: (String) -> Unit) {

    val id = ConnectionID(Yegg.newID())

    var buffer = mutableListOf<String>()
    var programming: Pair<String, String>? = null
    var quitRequested = false

    var user: Obj? = null

    fun sendText(text: String) = sendText.invoke(text)

    fun receiveText(text: String) {
        // TODO: rework program-buffer in-DB with actual suspend/readLines
        programming?.also { verb ->
            if (text == ".") {
                val result = Yegg.world.programVerb(verb.first, verb.second, buffer.joinToString("\n"))
                buffer.clear()
                programming = null
                sendText(result)
            } else buffer.add(text)
        } ?: run {
            if (text.startsWith(";")) {
                val code = text.substringAfter(";")
                sendText(Compiler.eval(Context(this), code))
            } else if (text.startsWith("@")) {
                // TODO: get rid of these hardcoded @meta commands
                parseMeta(text)
            } else if (text.isNotBlank()) {
                parseCommand(text)
            }
        }
    }

    private fun parseCommand(text: String) {
        // Split command into string parts
        val splits = text.split("\\s+".toRegex(), 2)
        val cmdstr = splits[0]
        val argstr = if (splits.size < 2) "" else splits[1]
        var dobjstr = argstr
        var iobjstr = ""
        val prep = Preposition.entries.firstOrNull { p ->
            p.strings.firstOrNull { argstr.contains(it) }?.also { prepstr ->
                dobjstr = argstr.substringBefore(prepstr).trimEnd(' ')
                iobjstr = argstr.substringAfter(prepstr).trimStart(' ')
            } != null
        }

        user?.also { user ->
            // If logged in, match against all objs in scope
            val scope = buildList {
                add(user)
                addAll(user.contents)
                user.location?.also { loc ->
                    add(loc)
                    addAll(loc.contents)
                }
            }
            val dobj = matchObj(dobjstr, user, scope)
            val iobj = matchObj(iobjstr, user, scope)
            for (target in scope) {
                target.matchCommand(cmdstr, argstr, dobjstr, dobj, prep, iobjstr, iobj)?.also {
                    runCommand(it)
                    return
                }
            }
        } ?: run {
            // If not logged in, match against $sys
            Yegg.world.sys.matchCommand(null, cmdstr, argstr, dobjstr, null, prep, iobjstr, null)?.also {
                runCommand(it)
                return
            }
        }

        sendText(Yegg.HUH_MSG)
    }

    private fun matchObj(s: String, objMe: Obj?, scope: List<Obj>): Obj? {
        if (s == "me") return objMe
        if (s == "here") return objMe?.location
        // TODO: match against scope when objs actually have names/aliases
        return null
    }

    private fun runCommand(match: CommandMatch) {
        val c = Context(this).apply {
            vThis = match.obj?.vThis ?: Yegg.vNullObj
            vUser = connection?.user?.vThis ?: Yegg.vNullObj
        }
        try {
            match.trait.callVerb(c, match.verb, match.args)
        } catch (e: Exception) {
            sendText(e.toString())
            sendText(c.stackDump())
        }
    }

    // Respond to meta commands.
    private fun parseMeta(text: String) {
        if (text == "@") return
        val words = text.split(" ")
        when (words[0].substring(1, words[0].length)) {
            "program" -> parseProgram(words)
            "list" -> parseList(words)
            else -> sendText(Yegg.HUH_MSG)
        }
    }

    private fun parseProgram(words: List<String>) {
        if (words.size < 2) { sendText("@program what?") ; return }
        val terms = words[1].split(".")
        if (terms.size != 2) { sendText("@program what?") ; return }
        programming = Pair(terms[0], terms[1])
        sendText("Enter verbcode.  Terminate with '.' on a line by itself.")
    }

    private fun parseList(words: List<String>) {
        if (words.size < 2) { sendText("@list what?") ; return }
        val terms = words[1].split(".")
        if (terms.size != 2) { sendText("@list what?") ; return }
        sendText(Yegg.world.listVerb(terms[0], terms[1]))
    }

}
