package app.truenasmobile.api

import java.net.URI

/** A web address to open an app at. */
data class AppLink(
    val label: String,
    val url: String,
    /** The address TrueNAS reports for this link, if it has one. */
    val defaultUrl: String?,
    /** True when [url] is an address the user set. */
    val custom: Boolean,
)

/**
 * The links to an app's web interface. TrueNAS reports links like http://192.168.1.5:30013;
 * the user can replace each one with their own address (for example an HTTPS address from a
 * reverse proxy), or add one to an app that doesn't report any.
 */
object AppLinks {
    const val DEFAULT_LABEL = "Web UI"

    /** Portal links often point at 0.0.0.0; swap in the server's address. */
    fun portalUrl(url: String, host: String?): String {
        if (host == null) return url
        return url.replace("://0.0.0.0", "://$host").replace("://localhost", "://$host").replace("://127.0.0.1", "://$host")
    }

    /** TrueNAS's links with the user's own addresses in their place, then any links the user added. */
    fun resolve(portals: Map<String, String>, custom: Map<String, String>?, host: String?): List<AppLink> {
        val mine = custom.orEmpty()
        val reported = portals.map { (label, url) ->
            val default = portalUrl(url, host)
            val own = mine[label]
            AppLink(label, own ?: default, default, own != null)
        }
        val added = mine.filterKeys { it !in portals }.map { (label, url) -> AppLink(label, url, null, true) }
        return reported + added
    }

    /** Tidies an address the user typed (adding https:// if there's no scheme), or null if it isn't a web address. */
    fun normalize(input: String): String? {
        val t = input.trim()
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        val full = if (SCHEME.containsMatchIn(t)) t else "https://$t"
        val uri = runCatching { URI(full) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        val host = uri.host ?: uri.rawAuthority?.substringAfterLast('@')?.substringBefore(':')
        if (host.isNullOrBlank()) return null
        return full
    }

    /** The address without the scheme, for showing under a link. */
    fun display(url: String): String = url.substringAfter("://").removeSuffix("/")

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
}
