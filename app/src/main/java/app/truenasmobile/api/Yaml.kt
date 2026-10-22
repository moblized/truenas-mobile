package app.truenasmobile.api

/**
 * Minimal YAML writer for showing a custom app's Docker Compose settings as editable text.
 * Strings are quoted whenever YAML could read them as something else, so what you see is
 * exactly what gets saved.
 */
object Yaml {
    fun write(value: Any?): String = buildString { node(value, 0, this, inList = false) }.trimEnd() + "\n"

    private fun node(v: Any?, indent: Int, out: StringBuilder, inList: Boolean) {
        when (v) {
            is Map<*, *> -> {
                if (v.isEmpty()) {
                    out.append("{}\n")
                    return
                }
                var first = true
                for ((k, value) in v) {
                    if (!(first && inList)) out.append(" ".repeat(indent))
                    first = false
                    out.append(key(k.toString())).append(':')
                    child(value, indent, out)
                }
            }
            is List<*> -> {
                if (v.isEmpty()) {
                    out.append("[]\n")
                    return
                }
                var first = true
                for (item in v) {
                    if (!(first && inList)) out.append(" ".repeat(indent))
                    first = false
                    out.append("- ")
                    if (item is Map<*, *> && item.isNotEmpty()) node(item, indent + 2, out, inList = true)
                    else if (item is List<*> && item.isNotEmpty()) node(item, indent + 2, out, inList = true)
                    else out.append(scalar(item)).append('\n')
                }
            }
            else -> out.append(scalar(v)).append('\n')
        }
    }

    private fun child(value: Any?, indent: Int, out: StringBuilder) {
        when {
            value is Map<*, *> && value.isNotEmpty() -> {
                out.append('\n')
                node(value, indent + 2, out, inList = false)
            }
            value is List<*> && value.isNotEmpty() -> {
                out.append('\n')
                node(value, indent + 2, out, inList = false)
            }
            value is String && value.contains('\n') && !value.startsWith(" ") && !value.endsWith("\n\n") -> {
                out.append(if (value.endsWith("\n")) " |\n" else " |-\n")
                value.trimEnd('\n').lines().forEach { line -> out.append(" ".repeat(indent + 2)).append(line).append('\n') }
            }
            else -> out.append(' ').append(scalar(value)).append('\n')
        }
    }

    private val plainKey = Regex("^[A-Za-z0-9_./-]+$")
    // No ':' in plain strings: YAML 1.1 (which TrueNAS reads with) turns "22:22" into a number.
    private val plainString = Regex("^[A-Za-z0-9_./@%+-][A-Za-z0-9 _./@%+=,-]*$")
    private val reserved = setOf("true", "false", "yes", "no", "on", "off", "null", "~")

    private fun key(k: String): String = if (plainKey.matches(k) && k.lowercase() !in reserved) k else quote(k)

    private fun scalar(v: Any?): String = when (v) {
        null -> "null"
        is Boolean -> v.toString()
        is Long, is Int -> v.toString()
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        is Number -> v.toString()
        is Map<*, *> -> "{}"
        is List<*> -> "[]"
        else -> {
            val s = v.toString()
            val looksNumeric = s.toDoubleOrNull() != null || s.startsWith("0x") || s.startsWith("0o")
            if (s.isEmpty() || looksNumeric || s.lowercase() in reserved || !plainString.matches(s) || s.endsWith(" ") || s.startsWith("-") ) quote(s) else s
        }
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }
}
