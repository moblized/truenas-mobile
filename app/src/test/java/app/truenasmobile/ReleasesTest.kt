package app.truenasmobile

import app.truenasmobile.update.Releases
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {

    // Trimmed copy of GitHub's answer for this app's release v1.0.13.
    private val latest = """
    {"tag_name": "v1.0.13", "html_url": "https://github.com/moblized/truenas-mobile/releases/tag/v1.0.13",
     "draft": false, "prerelease": false,
     "assets": [{"name": "truenas-mobile-1.0.13.apk", "size": 2412981, "content_type": "application/vnd.android.package-archive",
       "browser_download_url": "https://github.com/moblized/truenas-mobile/releases/download/v1.0.13/truenas-mobile-1.0.13.apk"}],
     "body": "### Fix missing import for the Automatic updates screen\n\n\n### CI: show compiler and test errors on the run summary\n\n\n### Add automatic app updates\n\nTrueNAS can't update apps on its own, so the app sets up a cron job.\n\n\n\n---\n**Install:** on your phone, download `truenas-mobile-1.0.13.apk` below and open it."}
    """

    @Test
    fun readsTheLatestRelease() {
        val r = Releases.parse(JSONObject(latest))!!
        assertEquals("1.0.13", r.version)
        assertEquals(13, r.build)
        assertEquals(2412981L, r.apkSize)
        assertTrue(r.apkUrl.endsWith("/v1.0.13/truenas-mobile-1.0.13.apk"))
        assertEquals("• Fix missing import for the Automatic updates screen\n• Add automatic app updates", r.notes)
        assertTrue(Releases.isNewer(r, 12))
        assertFalse(Releases.isNewer(r, 13))
    }

    @Test
    fun skipsReleasesItCantInstall() {
        assertNull(Releases.parse(JSONObject(latest).put("assets", org.json.JSONArray())))
        assertNull(Releases.parse(JSONObject(latest).put("tag_name", "nightly")))
        assertNull(Releases.parse(JSONObject(latest).put("prerelease", true)))
        assertEquals("Plain notes", Releases.notes("Plain notes\n---\nfooter"))
    }
}
