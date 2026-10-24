package app.truenasmobile

import app.truenasmobile.api.AppLink
import app.truenasmobile.api.AppLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLinksTest {

    @Test
    fun ownAddressReplacesTrueNasLink() {
        val portals = mapOf("Web UI" to "http://0.0.0.0:30013/", "Admin" to "http://0.0.0.0:30014/")
        val links = AppLinks.resolve(portals, mapOf("Web UI" to "https://jellyfin.example.com"), "192.168.1.5")
        assertEquals(
            listOf(
                AppLink("Web UI", "https://jellyfin.example.com", "http://192.168.1.5:30013/", true),
                AppLink("Admin", "http://192.168.1.5:30014/", "http://192.168.1.5:30014/", false),
            ),
            links,
        )
    }

    @Test
    fun addedLinkForAppWithoutPortals() {
        assertEquals(emptyList<AppLink>(), AppLinks.resolve(emptyMap(), null, "nas"))
        assertEquals(
            listOf(AppLink("Web UI", "https://audiomuse.example.com", null, true)),
            AppLinks.resolve(emptyMap(), mapOf("Web UI" to "https://audiomuse.example.com"), "nas"),
        )
    }

    @Test
    fun typedAddresses() {
        assertEquals("https://jellyfin.example.com", AppLinks.normalize("  jellyfin.example.com "))
        assertEquals("https://music.example.com:8443/app", AppLinks.normalize("music.example.com:8443/app"))
        assertEquals("http://100.64.0.2:4533", AppLinks.normalize("http://100.64.0.2:4533"))
        assertEquals("https://nas.tail1234.ts.net/navidrome/", AppLinks.normalize("https://nas.tail1234.ts.net/navidrome/"))
        assertEquals("https://my_app.lan", AppLinks.normalize("my_app.lan"))
        assertNull(AppLinks.normalize(""))
        assertNull(AppLinks.normalize("ftp://example.com"))
        assertNull(AppLinks.normalize("not a url"))
        assertNull(AppLinks.normalize("https://"))
        assertEquals("jellyfin.example.com", AppLinks.display("https://jellyfin.example.com/"))
    }
}
