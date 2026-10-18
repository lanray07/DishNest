package com.dishnest.app.data

import java.net.URI
import java.util.Locale

/** Per-import robots policy. This is a courtesy access rule, never permission to copy content. */
internal class RobotsPolicy(text: String) {
    private data class Rule(val pattern: String, val allow: Boolean)

    private data class Group(val agents: MutableList<String>, val rules: MutableList<Rule>)

    private val groups = mutableListOf<Group>()

    init {
        var group: Group? = null
        var hasRules = false
        for (line in text.removePrefix("\uFEFF").lineSequence()) {
            val record = line.substringBefore('#').trim()
            if (!record.contains(':')) continue
            val name = record.substringBefore(':').trim().lowercase(Locale.ROOT)
            val value = record.substringAfter(':').trim()
            if (name == "user-agent") {
                if (group == null || hasRules) {
                    group = Group(mutableListOf(), mutableListOf())
                    groups.add(group)
                    hasRules = false
                }
                group.agents.add(value.lowercase(Locale.ROOT))
            } else if (name == "allow" || name == "disallow") {
                hasRules = true
                if (value.startsWith('/'))
                    group?.rules?.add(Rule(normalise(value), name == "allow"))
            }
        }
    }

    fun allows(uri: URI): Boolean {
        val exact = groups.filter { "dishnest" in it.agents }
        val selected = exact.ifEmpty { groups.filter { "*" in it.agents } }
        val path = normalise(uri.rawPath.ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: ""))
        val matches = selected.flatMap { it.rules }.filter { matches(it.pattern, path) }
        val longest =
            matches.maxOfOrNull { it.pattern.toByteArray(Charsets.UTF_8).size } ?: return true
        return matches.any { it.allow && it.pattern.toByteArray(Charsets.UTF_8).size == longest }
    }

    companion object {
        // Decode only unreserved ASCII. Encoded slashes stay distinct from path separators.
        private fun normalise(value: String): String = buildString {
            var i = 0
            while (i < value.length) {
                val c = value[i]
                val byte =
                    if (c == '%' && i + 2 < value.length)
                        value.substring(i + 1, i + 3).toIntOrNull(16)
                    else null
                if (byte != null) {
                    val decoded = byte.toChar()
                    if (
                        decoded in 'a'..'z' ||
                            decoded in 'A'..'Z' ||
                            decoded in '0'..'9' ||
                            decoded in "-._~"
                    )
                        append(decoded)
                    else append("%%%02X".format(Locale.ROOT, byte))
                    i += 3
                } else {
                    val point = value.codePointAt(i)
                    if (point > 127) {
                        String(Character.toChars(point)).toByteArray(Charsets.UTF_8).forEach {
                            append("%%%02X".format(Locale.ROOT, it.toInt() and 255))
                        }
                    } else append(c)
                    i += Character.charCount(point)
                }
            }
        }

        // Iterative wildcard matching avoids regex backtracking on untrusted robots files.
        private fun matches(pattern: String, path: String): Boolean {
            val anchored = pattern.endsWith('$')
            val rule = if (anchored) pattern.dropLast(1) else pattern
            var p = 0
            var s = 0
            var star = -1
            var retry = 0
            while (s < path.length) {
                if (p == rule.length && !anchored) return true
                if (p < rule.length && rule[p] == '*') {
                    star = p++
                    retry = s
                } else if (p < rule.length && rule[p] == path[s]) {
                    p++
                    s++
                } else if (star >= 0) {
                    p = star + 1
                    s = ++retry
                } else return false
            }
            while (p < rule.length && rule[p] == '*') p++
            return p == rule.length
        }
    }
}
