package app.truenasmobile

import app.truenasmobile.net.Endpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointTest {
    @Test
    fun bareIpDefaultsToHttps() {
        val e = Endpoint.parse("192.168.1.10")!!
        assertTrue(e.secure)
        assertEquals("wss://192.168.1.10/api/current", e.wsUrl)
        assertEquals("https://192.168.1.10", e.webUrl)
    }

    @Test
    fun explicitHttpIsInsecure() {
        val e = Endpoint.parse("http://truenas.local")!!
        assertFalse(e.secure)
        assertEquals("ws://truenas.local/api/current", e.wsUrl)
    }

    @Test
    fun keepsPortAndDropsPath() {
        val e = Endpoint.parse("https://NAS.example.com:8443/ui/dashboard")!!
        assertEquals("wss://nas.example.com:8443/api/current", e.wsUrl)
        assertEquals(8443, e.port)
    }

    @Test
    fun ipv6() {
        assertEquals("wss://[fd00::10]/api/current", Endpoint.parse("fd00::10")!!.wsUrl)
        assertEquals("wss://[fd00::10]:444/api/current", Endpoint.parse("[fd00::10]:444")!!.wsUrl)
        assertEquals("fd00::10", Endpoint.parse("[fd00::10]")!!.bareHost)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(Endpoint.parse(""))
        assertNull(Endpoint.parse("nas local"))
        assertNull(Endpoint.parse("nas:99999"))
        assertNull(Endpoint.parse("https://"))
    }
}
