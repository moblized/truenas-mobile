package app.truenasmobile

import app.truenasmobile.api.AutoUpdate
import app.truenasmobile.api.Cron
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUpdateTest {

    private fun job(config: AutoUpdate, id: Int = 7): JSONObject =
        JSONObject(config.toCronJob()).put("id", id).put("schedule", JSONObject(config.schedule.toMap()))

    @Test
    fun allAppsByDefault() {
        val c = AutoUpdate(enabled = true)
        val cmd = c.command()
        assertTrue(cmd.startsWith("""for a in $(midclt call app.query '[["upgrade_available","=",true]]' '{"select":["name"]}'"""))
        assertTrue("""midclt call -j app.upgrade "${'$'}a" '{"app_version":"latest","snapshot_hostpaths":false}'""" in cmd)
        assertTrue("app.pull_images" in cmd)
        assertFalse('\n' in cmd)
        assertEquals("Every day at 04:00 · all apps", c.summary())
        val m = c.toCronJob()
        assertEquals("root", m["user"])
        assertEquals(AutoUpdate.DESCRIPTION, m["description"])
    }

    @Test
    fun roundTripsThroughTheCronJob() {
        val chosen = AutoUpdate(
            enabled = true, schedule = Cron("30", "3", "*", "*", "0"), includeNew = false,
            only = setOf("navidrome", "jellyfin"), customImages = false, snapshot = true,
        )
        assertTrue("""["name","in",["jellyfin","navidrome"]]""" in chosen.command())
        assertEquals(chosen.copy(id = 7), AutoUpdate.parse(job(chosen)))
        assertEquals("Every Sunday at 03:30 · 2 apps", chosen.summary())

        val skipping = AutoUpdate(enabled = false, skip = setOf("immich"))
        assertTrue("""["name","nin",["immich"]]""" in skipping.command())
        assertEquals(skipping.copy(id = 7), AutoUpdate.parse(job(skipping)))

        val edited = AutoUpdate.parse(JSONObject().put("id", 3).put("command", "echo hi").put("enabled", true))
        assertTrue(edited.edited)
        assertTrue(edited.active)
    }

    @Test
    fun pickingApps() {
        val installed = listOf("jellyfin", "navidrome", "immich")
        var c = AutoUpdate(enabled = true).withApp("immich", false)
        assertEquals(setOf("immich"), c.skip)
        assertFalse(c.covers("immich"))
        assertTrue(c.covers("brand-new-app"))

        c = c.withIncludeNew(false, installed)
        assertEquals(setOf("jellyfin", "navidrome"), c.only)
        assertFalse(c.covers("brand-new-app"))
        c = c.withApp("navidrome", false)
        assertEquals(setOf("jellyfin"), c.only)

        c = c.withIncludeNew(true, installed)
        assertEquals(setOf("navidrome", "immich"), c.skip)
        assertTrue(c.covers("jellyfin"))
    }

    @Test
    fun oddNamesNeverReachTheShell() {
        val c = AutoUpdate(includeNew = false, only = setOf("jellyfin", "x'; rm -rf /", "Bad"))
        assertTrue("""["name","in",["jellyfin"]]""" in c.command())
        assertFalse("rm -rf" in c.command())
    }
}
