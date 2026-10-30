package app.truenasmobile.update

import org.json.JSONObject

/** A published version of this app (a GitHub Release with its APK). */
data class Release(
    /** "1.0.14" */
    val version: String,
    /** The build number, which is the APK's versionCode. */
    val build: Int,
    /** Short "what's new" text. */
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
)

/** Reads this app's releases from GitHub (moblized/truenas-mobile). */
object Releases {
    const val REPO = "moblized/truenas-mobile"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    const val PAGE_URL = "https://github.com/$REPO/releases"

    private val TAG = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

    /** The release in GitHub's JSON, or null if it has no APK or an unexpected tag. */
    fun parse(o: JSONObject): Release? {
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val tag = o.optString("tag_name")
        val m = TAG.matchEntire(tag.trim()) ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) } ?: return null
        val url = apk.optString("browser_download_url").takeIf { it.startsWith("https://") } ?: return null
        return Release(
            version = "${m.groupValues[1]}.${m.groupValues[2]}.${m.groupValues[3]}",
            build = m.groupValues[3].toIntOrNull() ?: return null,
            notes = notes(o.optString("body")),
            apkUrl = url,
            apkSize = apk.optLong("size", -1),
            pageUrl = o.optString("html_url").takeIf { it.startsWith("https://") } ?: PAGE_URL,
        )
    }

    /**
     * Release notes are the commits since the last release ("### title" + details), then an
     * install footer. Keeps one line per change: the titles, without CI-only changes.
     */
    fun notes(body: String): String {
        val main = body.replace("\r\n", "\n").substringBefore("\n---\n").trim()
        val titles = main.lineSequence()
            .filter { it.startsWith("### ") }
            .map { it.removePrefix("### ").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("CI:", ignoreCase = true) }
            .toList()
        return if (titles.isNotEmpty()) titles.take(8).joinToString("\n") { "• $it" } else main.take(600)
    }

    fun isNewer(release: Release, currentBuild: Int): Boolean = release.build > currentBuild
}
