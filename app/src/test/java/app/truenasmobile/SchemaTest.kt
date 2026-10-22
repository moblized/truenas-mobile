package app.truenasmobile

import app.truenasmobile.api.Schema
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Install-form logic, using a trimmed copy of the Jellyfin catalog questions. */
class SchemaTest {

    private val detailsJson = """
    {"name": "jellyfin", "title": "Jellyfin", "description": "Media server", "icon_url": "https://media.sys.truenas.net/apps/jellyfin/icons/icon.png",
     "home": "https://jellyfin.org", "latest_version": "1.2.3", "screenshots": [],
     "versions": {
       "1.2.3": {"supported": true, "human_version": "10.10.7_1.2.3",
         "schema": {
           "groups": [{"name": "Jellyfin Configuration", "description": "Configure Jellyfin"},
                      {"name": "Network Configuration", "description": "Configure Network</br>for Jellyfin"},
                      {"name": "Storage Configuration", "description": "Storage"}],
           "questions": [
             {"variable": "TZ", "group": "Jellyfin Configuration", "label": "Timezone",
              "schema": {"type": "string", "default": "America/New_York", "required": true,
                         "enum": [{"value": "Etc/UTC", "description": "'Etc/UTC' timezone"}, {"value": "America/New_York", "description": "'America/New_York' timezone"}]}},
             {"variable": "network", "group": "Network Configuration", "label": "",
              "schema": {"type": "dict", "attrs": [
                {"variable": "host_network", "label": "Host Network", "schema": {"type": "boolean", "default": false}},
                {"variable": "web_port", "label": "WebUI Port", "schema": {"type": "dict", "show_if": [["host_network", "=", false]], "attrs": [
                  {"variable": "bind_mode", "label": "Port Bind Mode", "schema": {"type": "string", "default": "published",
                     "enum": [{"value": "published", "description": "Publish"}, {"value": "", "description": "None"}]}},
                  {"variable": "port_number", "label": "Port Number", "schema": {"type": "int", "default": 30013, "min": 1, "max": 65535, "required": true,
                     "show_if": [["bind_mode", "=", "published"]]}}
                ]}}
              ]}},
             {"variable": "storage", "group": "Storage Configuration", "label": "",
              "schema": {"type": "dict", "attrs": [
                {"variable": "additional_storage", "label": "Additional Storage", "schema": {"type": "list", "default": [], "items": [
                  {"variable": "storageEntry", "label": "Storage Entry", "schema": {"type": "dict", "attrs": [
                    {"variable": "type", "label": "Type", "schema": {"type": "string", "required": true, "default": "ix_volume",
                       "enum": [{"value": "host_path", "description": "Host Path"}, {"value": "ix_volume", "description": "ixVolume"}]}},
                    {"variable": "mount_path", "label": "Mount Path", "schema": {"type": "path", "required": true}},
                    {"variable": "host_path_config", "label": "Host Path Configuration", "schema": {"type": "dict", "show_if": [["type", "=", "host_path"]], "attrs": [
                      {"variable": "path", "label": "Host Path", "schema": {"type": "hostpath", "required": true}}
                    ]}}
                  ]}}
                ]}}
              ]}}
           ]
         },
         "values": {"TZ": "America/New_York", "network": {"host_network": false, "web_port": {"bind_mode": "published", "port_number": 30013}},
                    "storage": {"additional_storage": []}}
       },
       "1.3.0": {"supported": false, "human_version": "x", "schema": {"groups": [], "questions": []}, "values": {}}
     }}
    """.trimIndent()

    private val details = Schema.details(JSONObject(detailsJson), "community")!!

    @Test
    fun picksLatestSupportedVersionAndDefaults() {
        assertEquals("1.2.3", details.version)
        assertEquals("10.10.7_1.2.3", details.humanVersion)
        assertEquals(3, details.schema.grouped().size)
        assertEquals("Configure Network\nfor Jellyfin", details.schema.groups[1].description)
        assertEquals(30013L, Schema.getAt(details.defaults, listOf("network", "web_port", "port_number")))
        assertEquals("America/New_York", details.defaults["TZ"])
        // Enum labels lose the quotes the server adds
        assertEquals("Etc/UTC", details.schema.questions[0].schema.enum!![0].description)
        assertEquals("truenas_default (certificate)", Schema.enumLabel("'truenas_default' Certificate"))
    }

    @Test
    fun showIfFollowsSiblingValues() {
        val net = details.schema.questions[1].schema.attrs
        val webPort = net[1]
        assertTrue(Schema.visible(webPort.schema.showIf, mapOf("host_network" to false)))
        assertFalse(Schema.visible(webPort.schema.showIf, mapOf("host_network" to true)))
        assertTrue(Schema.visible(listOf(listOf("OR", listOf(listOf("a", "=", 1L), listOf("b", "=", 2L)))), mapOf("a" to 0L, "b" to 2)))
        assertTrue(Schema.visible(listOf(listOf("x", "in", listOf("a", "b"))), mapOf("x" to "b")))
        assertFalse(Schema.visible(listOf(listOf("n", ">", 5L)), mapOf("n" to 3L)))
    }

    @Test
    fun defaultsValidateAndHiddenFieldsAreSkipped() {
        assertTrue(Schema.validate(details.schema.questions, details.defaults).isEmpty())

        // Port out of range is caught...
        @Suppress("UNCHECKED_CAST")
        val badPort = Schema.setAt(details.defaults, listOf("network", "web_port", "port_number"), 70000L) as Map<String, Any?>
        val problems = Schema.validate(details.schema.questions, badPort)
        assertEquals(1, problems.size)
        assertEquals(listOf<Any>("network", "web_port", "port_number"), problems[0].path)

        // ...but not when host networking hides the whole port section.
        @Suppress("UNCHECKED_CAST")
        val hostNet = Schema.setAt(badPort, listOf("network", "host_network"), true) as Map<String, Any?>
        assertTrue(Schema.validate(details.schema.questions, hostNet).isEmpty())
    }

    @Test
    fun listItemsGetDefaultsAndConditionalRequiredFields() {
        val listQ = details.schema.questions[2].schema.attrs[0]
        val itemQ = listQ.schema.items[0]
        val newItem = Schema.defaultFor(itemQ) as Map<*, *>
        assertEquals("ix_volume", newItem["type"])
        assertEquals("", newItem["mount_path"])

        @Suppress("UNCHECKED_CAST")
        var values = Schema.setAt(details.defaults, listOf("storage", "additional_storage"), listOf(newItem)) as Map<String, Any?>
        // mount path is required
        assertEquals(1, Schema.validate(details.schema.questions, values).size)

        @Suppress("UNCHECKED_CAST")
        values = Schema.setAt(values, listOf("storage", "additional_storage", 0, "mount_path"), "/media") as Map<String, Any?>
        assertTrue(Schema.validate(details.schema.questions, values).isEmpty())

        // Switching to a host path makes its path required
        @Suppress("UNCHECKED_CAST")
        values = Schema.setAt(values, listOf("storage", "additional_storage", 0, "type"), "host_path") as Map<String, Any?>
        val problems = Schema.validate(details.schema.questions, values)
        assertEquals(1, problems.size)
        assertEquals(listOf<Any>("storage", "additional_storage", 0, "host_path_config", "path"), problems[0].path)

        // Removing the item clears it
        val removed = Schema.removeAt(values, listOf("storage", "additional_storage"), 0) as Map<*, *>
        assertEquals(0, (Schema.getAt(removed, listOf("storage", "additional_storage")) as List<*>).size)
    }

    @Test
    fun appNames() {
        assertEquals("jellyfin", Schema.suggestName("jellyfin", emptySet()))
        assertEquals("jellyfin-2", Schema.suggestName("jellyfin", setOf("jellyfin")))
        assertNull(Schema.appNameProblem("my-app1", emptySet()))
        assertNotNull(Schema.appNameProblem("1app", emptySet()))
        assertNotNull(Schema.appNameProblem("app-", emptySet()))
        assertNotNull(Schema.appNameProblem("plex", setOf("plex")))
    }

    @Test
    fun versionsAndText() {
        assertTrue(Schema.compareVersions("1.10.0", "1.9.9") > 0)
        assertEquals("Line one\nLine two", Schema.cleanText("Line one</br>\nLine <b>two</b>"))
        assertNull(Schema.cleanText("  "))
    }
}
