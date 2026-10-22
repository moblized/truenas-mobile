package app.truenasmobile.data

import android.content.Context
import android.content.SharedPreferences
import app.truenasmobile.net.Endpoint
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class AuthMethod { API_KEY, PASSWORD }

data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val auth: AuthMethod,
    val username: String,
    /** API key or password, encrypted with [SecretBox]. */
    val secretEnc: String,
    /** SHA-256 fingerprint of a certificate the user approved, if the phone doesn't trust it by itself. */
    val certPin: String? = null,
) {
    val endpoint: Endpoint? get() = Endpoint.parse(address)
    val displayName: String get() = name.ifBlank { endpoint?.bareHost ?: address }

    fun secret(): String? = SecretBox.decrypt(secretEnc)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("address", address)
        .put("auth", auth.name)
        .put("username", username)
        .put("secret", secretEnc)
        .put("certPin", certPin ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = ServerProfile(
            id = o.getString("id"),
            name = o.optString("name"),
            address = o.optString("address"),
            auth = runCatching { AuthMethod.valueOf(o.optString("auth")) }.getOrDefault(AuthMethod.API_KEY),
            username = o.optString("username"),
            secretEnc = o.optString("secret"),
            certPin = if (o.isNull("certPin")) null else o.optString("certPin"),
        )
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class NotifyLevel(val minRank: Int, val label: String) {
    WARNING(2, "Warnings and above"),
    CRITICAL(4, "Critical only"),
}

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    val notifyAlerts: Boolean = false,
    val notifyLevel: NotifyLevel = NotifyLevel.WARNING,
)

/** Saved servers and settings, in plain SharedPreferences (secrets inside are encrypted). */
class Store(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("truenas_mobile", Context.MODE_PRIVATE)

    fun servers(): List<ServerProfile> {
        val raw = prefs.getString("servers", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { runCatching { ServerProfile.fromJson(it) }.getOrNull() } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveServers(list: List<ServerProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("servers", arr.toString()).apply()
    }

    var activeId: String?
        get() = prefs.getString("active", null)
        set(value) {
            prefs.edit().putString("active", value).apply()
        }

    fun activeServer(): ServerProfile? {
        val all = servers()
        return all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
    }

    fun settings(): Settings = Settings(
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean("dynamic", true),
        pureBlack = prefs.getBoolean("pureBlack", false),
        notifyAlerts = prefs.getBoolean("notify", false),
        notifyLevel = runCatching { NotifyLevel.valueOf(prefs.getString("notifyLevel", null) ?: "") }.getOrDefault(NotifyLevel.WARNING),
    )

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putBoolean("dynamic", s.dynamicColor)
            .putBoolean("pureBlack", s.pureBlack)
            .putBoolean("notify", s.notifyAlerts)
            .putString("notifyLevel", s.notifyLevel.name)
            .apply()
    }

    /** Alerts already shown (in the app or as a notification), per server, so they don't notify again. */
    fun seenAlerts(serverId: String): Set<String> = prefs.getStringSet("seen_$serverId", emptySet()) ?: emptySet()

    fun setSeenAlerts(serverId: String, uuids: Set<String>) {
        prefs.edit().putStringSet("seen_$serverId", HashSet(uuids)).apply()
    }
}
