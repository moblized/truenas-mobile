package app.truenasmobile.api

import app.truenasmobile.net.arr
import app.truenasmobile.net.bool
import app.truenasmobile.net.jsonOrNull
import app.truenasmobile.net.keyList
import app.truenasmobile.net.long
import app.truenasmobile.net.obj
import app.truenasmobile.net.objects
import app.truenasmobile.net.str
import org.json.JSONArray
import org.json.JSONObject

/**
 * The install questions of a catalog app (its questions.yaml, as normalized by the server),
 * plus helpers to work with the values the user fills in. Values are plain Kotlin trees:
 * Map<String, Any?>, List<Any?>, String, Long, Double, Boolean or null.
 */
data class EnumOption(val value: Any?, val description: String)

data class QSchema(
    val type: String,
    val default: Any?,
    val required: Boolean,
    val nullable: Boolean,
    val hidden: Boolean,
    val private: Boolean,
    val enum: List<EnumOption>?,
    val min: Long?,
    val max: Long?,
    val minLength: Int?,
    val maxLength: Int?,
    val validChars: String?,
    val validCharsError: String?,
    val showIf: List<Any?>?,
    val attrs: List<Question>,
    val items: List<Question>,
) {
    val isText: Boolean get() = type in TEXT_TYPES
    val isNumber: Boolean get() = type == "int"
    val isBoolean: Boolean get() = type == "boolean"
    val isDict: Boolean get() = type == "dict" || type == "cron"
    val isList: Boolean get() = type == "list"
    val isPath: Boolean get() = type == "hostpath" || type == "hostpathdirectory" || type == "hostpathfile" || type == "path"

    companion object {
        val TEXT_TYPES = setOf("string", "text", "path", "hostpath", "hostpathdirectory", "hostpathfile", "uri", "ipaddr")
    }
}

data class Question(
    val variable: String,
    val label: String,
    val description: String?,
    val group: String?,
    val schema: QSchema,
)

data class QGroup(val name: String, val description: String?)

data class AppSchema(val groups: List<QGroup>, val questions: List<Question>) {
    /** Top-level questions per group, in the catalog's order. Ungrouped questions go last. */
    fun grouped(): List<Pair<QGroup, List<Question>>> {
        val known = groups.map { g -> g to questions.filter { it.group == g.name } }
        val names = groups.map { it.name }.toSet()
        val other = questions.filter { it.group == null || it.group !in names }
        return (known + (if (other.isEmpty()) emptyList() else listOf(QGroup("Other", null) to other)))
            .filter { (_, qs) -> qs.any { !it.schema.hidden } }
    }
}

data class CatalogApp(
    val name: String,
    val title: String,
    val description: String,
    val iconUrl: String?,
    val train: String,
    val categories: List<String>,
    val installed: Boolean,
    val recommended: Boolean,
    val popularity: Int?,
    val latestVersion: String?,
    val latestHumanVersion: String?,
)

data class CatalogAppDetails(
    val name: String,
    val title: String,
    val description: String,
    val iconUrl: String?,
    val home: String?,
    val train: String,
    val version: String,
    val humanVersion: String,
    val schema: AppSchema,
    val defaults: Map<String, Any?>,
    val screenshots: List<String>,
)

object Schema {

    // ------------------------------------------------------------ parsing

    fun catalogApp(o: JSONObject) = CatalogApp(
        name = o.str("name") ?: "",
        title = o.str("title")?.takeIf { it.isNotBlank() } ?: o.str("name") ?: "",
        description = o.str("description") ?: "",
        iconUrl = o.str("icon_url")?.takeIf { it.startsWith("http") },
        train = o.str("train") ?: "stable",
        categories = o.arr("categories")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } } } ?: emptyList(),
        installed = o.bool("installed"),
        recommended = o.bool("recommended"),
        popularity = o.long("popularity_rank")?.toInt(),
        latestVersion = o.str("latest_version"),
        latestHumanVersion = o.str("latest_human_version"),
    )

    /** Picks the newest supported version from catalog.get_app_details. */
    fun details(o: JSONObject, train: String): CatalogAppDetails? {
        val versions = o.obj("versions") ?: return null
        val keys = versions.keyList()
        if (keys.isEmpty()) return null
        val supported = keys.filter { versions.obj(it)?.bool("supported", default = true) != false }
        val latest = o.str("latest_version")
        val chosen = when {
            latest != null && latest in supported -> latest
            supported.isNotEmpty() -> supported.maxWithOrNull(::compareVersions)!!
            else -> keys.maxWithOrNull(::compareVersions)!!
        }
        val v = versions.obj(chosen) ?: return null
        val schemaJson = v.obj("schema") ?: JSONObject()
        val schema = AppSchema(
            groups = schemaJson.arr("groups")?.objects()?.map { QGroup(it.str("name") ?: "", cleanText(it.str("description"))) } ?: emptyList(),
            questions = schemaJson.arr("questions")?.objects()?.map(::question) ?: emptyList(),
        )
        @Suppress("UNCHECKED_CAST")
        val defaults = (toKotlin(v.opt("values")) as? Map<String, Any?>) ?: buildDefaults(schema.questions)
        return CatalogAppDetails(
            name = o.str("name") ?: "",
            title = o.str("title")?.takeIf { it.isNotBlank() } ?: o.str("name") ?: "",
            description = o.str("description") ?: "",
            iconUrl = o.str("icon_url")?.takeIf { it.startsWith("http") },
            home = o.str("home")?.takeIf { it.startsWith("http") },
            train = train,
            version = chosen,
            humanVersion = v.str("human_version") ?: chosen,
            schema = schema,
            defaults = defaults,
            screenshots = o.arr("screenshots")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.startsWith("http") } } } ?: emptyList(),
        )
    }

    fun question(o: JSONObject): Question {
        val s = o.obj("schema") ?: JSONObject()
        val enum = s.arr("enum")?.objects()?.map { e ->
            val value = toKotlin(e.opt("value"))
            EnumOption(value, enumLabel(cleanText(e.str("description"))) ?: value?.toString() ?: "None")
        }
        return Question(
            variable = o.str("variable") ?: "",
            label = o.str("label")?.takeIf { it.isNotBlank() } ?: "",
            description = cleanText(o.str("description")),
            group = o.str("group"),
            schema = QSchema(
                type = s.str("type") ?: "string",
                default = if (s.has("default")) toKotlin(s.opt("default")) else null,
                required = s.bool("required"),
                nullable = s.bool("null"),
                hidden = s.bool("hidden"),
                private = s.bool("private"),
                enum = enum?.takeIf { it.isNotEmpty() || s.bool("required") },
                min = s.long("min"),
                max = s.long("max"),
                minLength = s.long("min_length")?.toInt(),
                maxLength = s.long("max_length")?.toInt(),
                validChars = s.str("valid_chars"),
                validCharsError = s.str("valid_chars_error"),
                showIf = (toKotlin(s.opt("show_if")) as? List<*>)?.toList(),
                attrs = s.arr("attrs")?.objects()?.map(::question) ?: emptyList(),
                items = s.arr("items")?.objects()?.map(::question) ?: emptyList(),
            ),
        )
    }

    /** The server labels generated choices like "'Etc/UTC' timezone"; show just "Etc/UTC". */
    fun enumLabel(d: String?): String? {
        if (d.isNullOrBlank()) return null
        Regex("^'([^']+)'\\s+(timezone|IP Address|Interface)$").matchEntire(d)?.let { return it.groupValues[1] }
        Regex("^'([^']+)'\\s+Certificate$").matchEntire(d)?.let { return "${it.groupValues[1]} (certificate)" }
        return d.removeSurrounding("'")
    }

    /** Catalog descriptions contain bits of HTML ("</br>", "<a href>"). Keep the text, drop the tags. */
    fun cleanText(s: String?): String? {
        if (s.isNullOrBlank()) return null
        return s.replace(Regex("<\\s*/?\\s*br\\s*/?\\s*>[ \t]*\n?", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .ifBlank { null }
    }

    fun toKotlin(v: Any?): Any? = when (val x = v.jsonOrNull()) {
        is JSONObject -> LinkedHashMap<String, Any?>().also { m -> x.keyList().forEach { k -> m[k] = toKotlin(x.opt(k)) } }
        is JSONArray -> (0 until x.length()).map { toKotlin(x.opt(it)) }
        is Int -> x.toLong()
        is Long -> x
        is Double -> if (x % 1.0 == 0.0 && kotlin.math.abs(x) < 1e15) x.toLong() else x
        is Float -> x.toDouble()
        is Number -> x.toLong()
        else -> x
    }

    fun compareVersions(a: String, b: String): Int {
        val pa = a.split('.', '-', '_').map { it.toIntOrNull() }
        val pb = b.split('.', '-', '_').map { it.toIntOrNull() }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i) ?: -1
            val y = pb.getOrNull(i) ?: -1
            if (x != y) return x.compareTo(y)
        }
        return a.compareTo(b)
    }

    // ------------------------------------------------------------ defaults

    /** Default value of one question (used for new list items, or when the server sends no defaults). */
    fun defaultFor(q: Question): Any? {
        val s = q.schema
        if (s.default != null) return s.default
        return when {
            s.isDict -> buildDefaults(s.attrs)
            s.isList -> emptyList<Any?>()
            s.nullable -> null
            s.isBoolean -> false
            s.enum != null && s.enum.isNotEmpty() && s.required -> s.enum.first().value
            s.isText -> ""
            else -> null
        }
    }

    fun buildDefaults(questions: List<Question>): Map<String, Any?> =
        LinkedHashMap<String, Any?>().also { m -> questions.forEach { q -> m[q.variable] = defaultFor(q) } }

    // ------------------------------------------------------------ value tree

    fun getAt(root: Any?, path: List<Any>): Any? {
        var cur = root
        for (p in path) {
            cur = when {
                p is String && cur is Map<*, *> -> cur[p]
                p is Int && cur is List<*> -> cur.getOrNull(p)
                else -> return null
            }
        }
        return cur
    }

    /** Returns a copy of [root] with the value at [path] replaced (missing maps are created). */
    fun setAt(root: Any?, path: List<Any>, value: Any?): Any? {
        if (path.isEmpty()) return value
        val head = path.first()
        val rest = path.drop(1)
        return when (head) {
            is String -> {
                val m = LinkedHashMap<String, Any?>()
                (root as? Map<*, *>)?.forEach { (k, v) -> m[k.toString()] = v }
                m[head] = setAt(m[head], rest, value)
                m
            }
            is Int -> {
                val l = (root as? List<*>)?.toMutableList() ?: mutableListOf()
                while (l.size <= head) l.add(null)
                l[head] = setAt(l[head], rest, value)
                l
            }
            else -> root
        }
    }

    fun removeAt(root: Any?, listPath: List<Any>, index: Int): Any? {
        val list = (getAt(root, listPath) as? List<*>)?.toMutableList() ?: return root
        if (index in list.indices) list.removeAt(index)
        return setAt(root, listPath, list)
    }

    // ------------------------------------------------------------ show_if

    /**
     * Evaluates a TrueNAS filter list such as [["type", "=", "host_path"]] against the values of
     * the dict the question lives in. All conditions must match; ["OR", [...]] matches any.
     */
    fun visible(showIf: List<Any?>?, siblings: Map<*, *>?): Boolean {
        if (showIf.isNullOrEmpty()) return true
        val ctx = siblings ?: emptyMap<String, Any?>()
        return showIf.all { cond -> evalCondition(cond, ctx) }
    }

    private fun evalCondition(cond: Any?, ctx: Map<*, *>): Boolean {
        val c = cond as? List<*> ?: return true
        if (c.size == 2 && c[0] == "OR") {
            val options = c[1] as? List<*> ?: return true
            return options.any { evalCondition(it, ctx) }
        }
        if (c.size < 3) return true
        val field = c[0] as? String ?: return true
        val op = c[1] as? String ?: return true
        val expected = c[2]
        val actual = if (field.contains('.')) getAt(ctx, field.split('.')) else ctx[field]
        return when (op) {
            "=" -> same(actual, expected)
            "!=" -> !same(actual, expected)
            "in" -> (expected as? List<*>)?.any { same(actual, it) } ?: false
            "nin" -> (expected as? List<*>)?.none { same(actual, it) } ?: true
            "rin" -> (actual as? List<*>)?.any { same(it, expected) } ?: (actual?.toString()?.contains(expected.toString()) ?: false)
            "rnin" -> !((actual as? List<*>)?.any { same(it, expected) } ?: (actual?.toString()?.contains(expected.toString()) ?: false))
            ">", ">=", "<", "<=" -> {
                val a = (actual as? Number)?.toDouble() ?: return false
                val b = (expected as? Number)?.toDouble() ?: return false
                when (op) {
                    ">" -> a > b
                    ">=" -> a >= b
                    "<" -> a < b
                    else -> a <= b
                }
            }
            "^" -> actual?.toString()?.startsWith(expected.toString()) ?: false
            "!^" -> !(actual?.toString()?.startsWith(expected.toString()) ?: false)
            "$" -> actual?.toString()?.endsWith(expected.toString()) ?: false
            "!$" -> !(actual?.toString()?.endsWith(expected.toString()) ?: false)
            "~" -> runCatching { Regex(expected.toString()).containsMatchIn(actual?.toString() ?: "") }.getOrDefault(true)
            else -> true
        }
    }

    fun same(a: Any?, b: Any?): Boolean = when {
        a == null || b == null -> a == b
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a == b
    }

    // ------------------------------------------------------------ validation

    data class Problem(val path: List<Any>, val label: String, val message: String)

    /** Checks the visible fields the way the server will, so the user gets the error before installing. */
    fun validate(questions: List<Question>, values: Map<*, *>?, path: List<Any> = emptyList(), parentLabel: String = ""): List<Problem> {
        val out = mutableListOf<Problem>()
        for (q in questions) {
            val s = q.schema
            if (s.hidden || !visible(s.showIf, values)) continue
            val p = path + q.variable
            val v = values?.get(q.variable)
            val label = q.label.ifBlank { q.variable }.let { if (parentLabel.isBlank()) it else "$parentLabel › $it" }
            out += validateValue(q, v, p, label)
        }
        return out
    }

    private fun validateValue(q: Question, v: Any?, p: List<Any>, label: String): List<Problem> {
        val s = q.schema
        val out = mutableListOf<Problem>()
        val empty = v == null || (v is String && v.isEmpty())
        when {
            s.isDict -> out += validate(s.attrs, v as? Map<*, *>, p, if (q.label.isBlank()) label.substringBeforeLast(" › ", "") else label)
            s.isList -> {
                val items = v as? List<*> ?: emptyList<Any?>()
                val itemQ = s.items.firstOrNull()
                if (itemQ != null) {
                    items.forEachIndexed { i, item ->
                        out += validateValue(itemQ, item, p + i, "$label ${i + 1}")
                    }
                }
                if (s.required && items.isEmpty() && s.min != null && s.min > 0) out += Problem(p, label, "Add at least one")
            }
            s.required && empty && !(s.nullable && v == null && s.enum != null) ->
                out += Problem(p, label, "Required")
            !empty && s.isNumber -> {
                val n = (v as? Number)?.toLong()
                when {
                    n == null -> out += Problem(p, label, "Must be a whole number")
                    s.min != null && n < s.min -> out += Problem(p, label, "Must be at least ${s.min}")
                    s.max != null && n > s.max -> out += Problem(p, label, "Must be at most ${s.max}")
                }
            }
            !empty && v is String -> {
                if (s.minLength != null && v.length < s.minLength) out += Problem(p, label, "Must be at least ${s.minLength} characters")
                if (s.maxLength != null && v.length > s.maxLength) out += Problem(p, label, "Must be at most ${s.maxLength} characters")
                if (s.validChars != null && runCatching { !Regex(s.validChars).matches(v) }.getOrDefault(false)) {
                    out += Problem(p, label, s.validCharsError ?: "Contains characters that aren't allowed")
                }
            }
        }
        return out
    }

    val APP_NAME = Regex("^[a-z]([-a-z0-9]*[a-z0-9])?$")

    fun appNameProblem(name: String, taken: Set<String>): String? = when {
        name.isEmpty() -> "Give the app a name"
        name.length > 40 -> "40 characters at most"
        !APP_NAME.matches(name) -> "Lowercase letters, numbers and hyphens; start with a letter"
        name in taken -> "An app with this name is already installed"
        else -> null
    }

    /** "jellyfin", or "jellyfin-2" if that's taken. */
    fun suggestName(base: String, taken: Set<String>): String {
        val clean = base.lowercase().replace(Regex("[^a-z0-9-]"), "-").trim('-').take(36).ifBlank { "app" }
        if (clean !in taken) return clean
        var i = 2
        while ("$clean-$i" in taken) i++
        return "$clean-$i"
    }
}
