package app.truenasmobile.net

import org.json.JSONArray
import org.json.JSONObject

/** Converts Kotlin values (maps, lists, primitives, null) to org.json values for sending. */
object Json {
    fun value(v: Any?): Any = when (v) {
        null -> JSONObject.NULL
        is JSONObject, is JSONArray -> v
        is Map<*, *> -> obj(v)
        is Collection<*> -> array(v)
        is Array<*> -> array(v.toList())
        is Boolean, is String -> v
        is Int, is Long -> v
        is Float -> v.toDouble()
        is Number -> v
        is Enum<*> -> v.name
        else -> v.toString()
    }

    fun obj(m: Map<*, *>): JSONObject {
        val o = JSONObject()
        for ((k, v) in m) o.put(k.toString(), value(v))
        return o
    }

    fun array(c: Collection<*>): JSONArray {
        val a = JSONArray()
        for (item in c) a.put(value(item))
        return a
    }
}

/** JSONObject.NULL and missing values both become Kotlin null. */
fun Any?.jsonOrNull(): Any? = if (this == null || this == JSONObject.NULL) null else this

fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else opt(key)?.toString()

fun JSONObject.long(key: String): Long? {
    if (isNull(key)) return null
    return when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong()
        else -> null
    }
}

fun JSONObject.int(key: String): Int? = long(key)?.toInt()

fun JSONObject.double(key: String): Double? {
    if (isNull(key)) return null
    return when (val v = opt(key)) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }
}

fun JSONObject.bool(key: String, default: Boolean = false): Boolean {
    if (isNull(key)) return default
    return when (val v = opt(key)) {
        is Boolean -> v
        is String -> v.equals("true", ignoreCase = true)
        is Number -> v.toInt() != 0
        else -> default
    }
}

fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

fun JSONObject.arr(key: String): JSONArray? = optJSONArray(key)

fun JSONObject.keyList(): List<String> = keys().asSequence().toList()

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONArray.strings(): List<String> =
    (0 until length()).mapNotNull { i -> opt(i).jsonOrNull()?.toString() }

fun JSONArray.doubles(): List<Double> =
    (0 until length()).mapNotNull { i -> (opt(i) as? Number)?.toDouble() }

/**
 * TrueNAS sends datetimes as {"$date": epochMillis}. Some fields are plain epoch numbers
 * (seconds or millis) or ISO strings, so all of those are accepted.
 */
fun JSONObject.dateMillis(key: String): Long? {
    if (isNull(key)) return null
    return when (val v = opt(key)) {
        is JSONObject -> v.long("\$date")
        is Number -> v.toLong().let { if (it < 100_000_000_000L) it * 1000 else it }
        is String -> parseIsoMillis(v)
        else -> null
    }
}

fun parseIsoMillis(s: String): Long? = try {
    java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
} catch (_: Exception) {
    try {
        java.time.LocalDateTime.parse(s.replace(' ', 'T'))
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }
}
