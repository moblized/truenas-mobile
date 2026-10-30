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
    /** Look for new versions of this app (when it opens, and once a day). */
    val checkUpdates: Boolean = true,
    /** Download and install new versions in the background. */
    val autoInstallUpdates: Boolean = false,
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
        checkUpdates = prefs.getBoolean("checkUpdates", true),
        autoInstallUpdates = prefs.getBoolean("autoInstallUpdates", false),
    )

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putBoolean("dynamic", s.dynamicColor)
            .putBoolean("pureBlack", s.pureBlack)
            .putBoolean("notify", s.notifyAlerts)
            .putString("notifyLevel", s.notifyLevel.name)
            .putBoolean("checkUpdates", s.checkUpdates)
            .putBoolean("autoInstallUpdates", s.autoInstallUpdates)
            .apply()
    }

    // App updates

    var lastUpdateCheck: Long
        get() = prefs.getLong("lastUpdateCheck", 0)
        set(value) = prefs.edit().putLong("lastUpdateCheck", value).apply()

    /** The build the user said "Later" to, so it isn't offered again automatically. */
    var dismissedUpdate: Int
        get() = prefs.getInt("dismissedUpdate", 0)
        set(value) = prefs.edit().putInt("dismissedUpdate", value).apply()

    /** The build a background check already notified about. */
    var notifiedUpdate: Int
        get() = prefs.getInt("notifiedUpdate", 0)
        set(value) = prefs.edit().putInt("notifiedUpdate", value).apply()

    /** The version this app is installing over itself, to say "updated" once it's done. */
    var pendingUpdate: String?
        get() = prefs.getString("pendingUpdate", null)
        set(value) = prefs.edit().putString("pendingUpdate", value).apply()

    /** Alerts already shown (in the app or as a notification), per server, so they don't notify again. */
    fun seenAlerts(serverId: String): Set<String> = prefs.getStringSet("seen_$serverId", emptySet()) ?: emptySet()

    fun setSeenAlerts(serverId: String, uuids: Set<String>) {
        prefs.edit().putStringSet("seen_$serverId", HashSet(uuids)).apply()
    }

    /** The user's own web addresses for apps on a server: app name → link label → URL. */
    fun appLinks(serverId: String): Map<String, Map<String, String>> {
        val raw = prefs.getString("links_$serverId", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { app ->
                val links = o.optJSONObject(app) ?: JSONObject()
                links.keys().asSequence().associateWith { links.optString(it) }.filterValues { it.isNotBlank() }
            }.filterValues { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveAppLinks(serverId: String, links: Map<String, Map<String, String>>) {
        val o = JSONObject()
        links.forEach { (app, m) -> if (m.isNotEmpty()) o.put(app, JSONObject(m)) }
        prefs.edit().putString("links_$serverId", o.toString()).apply()
    }

    /** Forgets what was saved for a server that was removed. */
    fun forgetServer(serverId: String) {
        prefs.edit().remove("links_$serverId").remove("seen_$serverId").apply()
    }
}
