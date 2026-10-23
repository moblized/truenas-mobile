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
    val immutable: Boolean,
    val editable: Boolean,
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
        val schema = appSchema(v.obj("schema"))
        val defaults = buildDefaults(schema.questions)
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

    fun appSchema(schemaJson: JSONObject?): AppSchema = AppSchema(
        groups = schemaJson?.arr("groups")?.objects()?.map { QGroup(it.str("name") ?: "", cleanText(it.str("description"))) } ?: emptyList(),
        questions = schemaJson?.arr("questions")?.objects()?.map(::question) ?: emptyList(),
    )

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
                immutable = s.bool("immutable"),
                editable = s.bool("editable", default = true),
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

    /** Default value of one question (used for new list items and when building the form's starting values). */
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

    /**
     * Starting values built from the (server-normalized) questions, so defaults such as the
     * server's own time zone are used. Optional fields with no default are left out, like the
     * web UI does, so the server applies its own.
     */
    fun buildDefaults(questions: List<Question>): Map<String, Any?> =
        LinkedHashMap<String, Any?>().also { m ->
            questions.forEach { q ->
                val s = q.schema
                if (s.default != null || s.isDict || s.isList || s.required || s.nullable) m[q.variable] = defaultFor(q)
            }
        }

    /** Fills in anything missing from [values] (e.g. new fields after an app update) without touching what's set. */
    fun withDefaults(questions: List<Question>, values: Map<*, *>?): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        values?.forEach { (k, v) -> out[k.toString()] = v }
        for (q in questions) {
            val cur = out[q.variable]
            if (!out.containsKey(q.variable)) {
                val s = q.schema
                if (s.default != null || s.isDict || s.isList || s.required || s.nullable) out[q.variable] = defaultFor(q)
            } else if (q.schema.isDict && cur is Map<*, *>) {
                out[q.variable] = withDefaults(q.schema.attrs, cur)
            }
        }
        return out
    }

    private val passwordChars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Strong random password without characters that break database URLs. */
    fun generatePassword(length: Int = 24): String {
        val rnd = java.security.SecureRandom()
        return (1..length).map { passwordChars[rnd.nextInt(passwordChars.length)] }.joinToString("")
    }

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


    // ------------------------------------------------------------ simple layout

    /** A question placed in the easy part of the form, with where its value lives. */
    data class Placed(val q: Question, val path: List<Any>)

    /**
     * Splits an app's questions into the few things most people set (storage, ports, GPU, and
     * required fields with no value yet) and everything else, which goes under Advanced.
     */
    data class Layout(
        val needsInput: List<Placed>,
        val storage: List<Placed>,
        val storageLists: List<Placed>,
        val ports: List<Placed>,
        val gpu: Placed?,
        /** Paths shown in the easy sections, so Advanced can skip them. */
        val claimed: Set<List<Any>>,
    )

    private val storageTypes = setOf("host_path", "ix_volume")

    fun isStorage(q: Question): Boolean =
        q.schema.isDict && q.schema.attrs.any { a -> a.variable == "type" && a.schema.enum?.any { it.value in storageTypes } == true }

    fun isStorageList(q: Question): Boolean =
        q.schema.isList && q.schema.items.firstOrNull()?.let(::isStorage) == true

    fun isPort(q: Question): Boolean =
        q.schema.isDict && q.schema.attrs.any { it.variable == "port_number" && it.schema.isNumber }

    fun isGpu(q: Question): Boolean =
        q.schema.isDict && q.schema.attrs.any { it.variable == "use_all_gpus" || it.variable == "nvidia_gpu_selection" }

    fun layout(questions: List<Question>, values: Map<*, *>): Layout {
        val needs = mutableListOf<Placed>()
        val storage = mutableListOf<Placed>()
        val lists = mutableListOf<Placed>()
        val ports = mutableListOf<Placed>()
        var gpu: Placed? = null
        val claimed = mutableSetOf<List<Any>>()

        fun walk(qs: List<Question>, parent: List<Any>, siblings: Map<*, *>?) {
            for (q in qs) {
                val s = q.schema
                if (s.hidden || !visible(s.showIf, siblings)) continue
                val path = parent + q.variable
                val v = siblings?.get(q.variable)
                when {
                    isStorage(q) -> { storage += Placed(q, path); claimed += path }
                    isStorageList(q) -> { lists += Placed(q, path); claimed += path }
                    isPort(q) -> { ports += Placed(q, path); claimed += path }
                    isGpu(q) && gpu == null -> {
                        if (s.attrs.any { !it.schema.hidden }) { gpu = Placed(q, path); claimed += path }
                    }
                    s.isDict -> walk(s.attrs, path, v as? Map<*, *>)
                    s.isList -> Unit
                    s.required && (v == null || v == "") -> { needs += Placed(q, path); claimed += path }
                }
            }
        }
        walk(questions, emptyList(), values)
        return Layout(needs, storage, lists, ports, gpu, claimed)
    }

    /** Internal service passwords (database, cache) nobody needs to type, so they're filled in for you. */
    fun isInternalSecret(q: Question): Boolean {
        if (!q.schema.private) return false
        val v = q.variable.lowercase()
        return listOf("db_", "database", "redis", "postgres", "mariadb", "mysql", "valkey", "secret_key", "jwt").any { it in v }
    }

    /** Pre-fills internal passwords with strong random ones. */
    @Suppress("UNCHECKED_CAST")
    fun fillGeneratedSecrets(questions: List<Question>, values: Map<String, Any?>): Map<String, Any?> {
        var out: Any? = values
        layout(questions, values).needsInput.forEach { p ->
            if (isInternalSecret(p.q)) out = setAt(out, p.path, generatePassword())
        }
        return out as Map<String, Any?>
    }

    /** Only the top-level answers the app defines (drops context keys the server adds to saved configs). */
    fun onlyQuestions(questions: List<Question>, values: Map<String, Any?>): Map<String, Any?> {
        val keys = questions.map { it.variable }.toSet()
        return values.filterKeys { it in keys }
    }

    /**
     * The values as the server should receive them. Like the web UI, leaves out fields whose
     * show_if doesn't match (such as the folder settings of storage set to Automatic): the server
     * still checks any value it's sent, so an empty hidden path fails with "Empty value not allowed".
     * Also leaves out empty optional paths, URLs and IP addresses, which the server rejects the same way.
     */
    fun forSubmit(questions: List<Question>, values: Map<String, Any?>): Map<String, Any?> =
        pruneDict(questions, onlyQuestions(questions, values))

    private fun pruneDict(questions: List<Question>, values: Map<*, *>): Map<String, Any?> {
        val byName = questions.associateBy { it.variable }
        // show_if is evaluated against the sibling values, with defaults for missing ones (as the server does).
        val ctx = LinkedHashMap<Any?, Any?>(values)
        questions.forEach { q -> if (q.variable !in ctx && q.schema.default != null) ctx[q.variable] = q.schema.default }
        val out = LinkedHashMap<String, Any?>()
        for ((k, v) in values) {
            val key = k as? String ?: continue
            val q = byName[key]
            if (q == null) {
                out[key] = v
                continue
            }
            val s = q.schema
            if (!visible(s.showIf, ctx)) continue
            if (v is String && v.isEmpty() && !s.required && (s.isPath || s.type == "uri" || s.type == "ipaddr")) {
                if (s.nullable) out[key] = null
                continue
            }
            out[key] = pruneValue(q, v)
        }
        return out
    }

    private fun pruneValue(q: Question, v: Any?): Any? {
        val s = q.schema
        return when {
            s.isDict && s.attrs.isNotEmpty() && v is Map<*, *> -> pruneDict(s.attrs, v)
            s.isList && v is List<*> -> {
                val item = s.items.firstOrNull() ?: return v
                v.map { pruneValue(item, it) }
            }
            else -> v
        }
    }

    /**
     * Rewrites server errors such as "values.storage.data.host_path_config.acl.path: Empty value not allowed"
     * with the form's own labels ("Data Storage › Host Path: Empty value not allowed").
     */
    fun describeErrors(questions: List<Question>, message: String): String =
        message.lines().joinToString("\n") { line ->
            val m = ERROR_PATH.matchEntire(line.trim()) ?: return@joinToString line
            val label = labelFor(questions, m.groupValues[1].split('.')) ?: return@joinToString line
            "$label: ${m.groupValues[2]}"
        }

    private val ERROR_PATH = Regex("""^(?:\[\w+]\s*)?(?:app_(?:create|update)\.)?values\.([\w.\-]+):\s*(.+)$""")

    /** The labels along [path], shortened to the entry and the field (e.g. "Data Storage › Host Path"). */
    fun labelFor(questions: List<Question>, path: List<String>): String? {
        val labels = mutableListOf<String>()
        var current = questions
        var item: Question? = null
        for (seg in path) {
            val index = seg.toIntOrNull()
            if (index != null) {
                val list = item ?: return null
                if (labels.isNotEmpty()) labels[labels.size - 1] = "${labels.last()} ${index + 1}"
                val itemQ = list.schema.items.firstOrNull() ?: return null
                current = itemQ.schema.attrs
                item = itemQ
                continue
            }
            val q = current.firstOrNull { it.variable == seg } ?: return null
            labels += q.label.ifBlank { q.variable }
            item = q
            current = q.schema.attrs
        }
        if (labels.isEmpty()) return null
        return if (labels.size == 1) labels[0] else "${labels[if (labels.size >= 3) 1 else 0]} › ${labels.last()}"
    }

    /** Sets a storage entry to use a folder on the server. */
    fun useHostPath(entry: Map<*, *>?, path: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        var out = (setAt(entry ?: emptyMap<String, Any?>(), listOf("type"), "host_path") as Map<String, Any?>)
        val hp = (out["host_path_config"] as? Map<*, *>)
        out = setAt(out, listOf("host_path_config"), LinkedHashMap<String, Any?>().apply {
            hp?.forEach { (k, v) -> put(k.toString(), v) }
            if (!containsKey("acl_enable")) put("acl_enable", false)
            put("path", path)
        }) as Map<String, Any?>
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
