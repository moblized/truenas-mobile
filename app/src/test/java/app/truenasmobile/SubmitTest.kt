package app.truenasmobile

import app.truenasmobile.api.Schema
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the install form sends to the server, using the catalog's (normalized) storage questions. */
class SubmitTest {

    private val questions = Schema.appSchema(JSONObject(STORAGE)).questions

    @Test
    fun automaticStorageDoesNotSendFolderSettings() {
        // The form starts with every branch filled in, including the hidden folder settings
        // (host_path_config.acl.path = ""), which the server rejects with "Empty value not allowed".
        val values = Schema.buildDefaults(questions)
        assertEquals("", Schema.getAt(values, listOf("storage", "data", "host_path_config", "acl", "path")))

        val sent = Schema.forSubmit(questions, values)
        val data = Schema.getAt(sent, listOf("storage", "data")) as Map<*, *>
        assertEquals("ix_volume", data["type"])
        assertFalse("host_path_config" in data)
        val ix = data["ix_volume_config"] as Map<*, *>
        assertEquals("data", ix["dataset_name"]) // hidden fields still go (the server needs them)
        assertFalse("acl_entries" in ix) // ACL off
        assertFalse("bind_ip" in sent) // empty optional IP address left out
        assertEquals("UTC", sent["TZ"])
    }

    @Test
    fun chosenFolderSendsOnlyThePath() {
        @Suppress("UNCHECKED_CAST")
        val entry = Schema.getAt(Schema.buildDefaults(questions), listOf("storage", "data")) as Map<String, Any?>
        val values = Schema.setAt(
            Schema.buildDefaults(questions), listOf("storage", "data"), Schema.useHostPath(entry, "/mnt/tank/apps/data"),
        ) as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val sent = Schema.forSubmit(questions, values as Map<String, Any?>)
        val data = Schema.getAt(sent, listOf("storage", "data")) as Map<*, *>
        assertEquals("host_path", data["type"])
        assertFalse("ix_volume_config" in data)
        assertEquals(mapOf("acl_enable" to false, "path" to "/mnt/tank/apps/data"), data["host_path_config"])
    }

    @Test
    fun listEntriesArePrunedToo() {
        val item = mapOf(
            "type" to "ix_volume",
            "mount_path" to "/media",
            "ix_volume_config" to mapOf("acl_enable" to false, "dataset_name" to "storage_entry"),
            "host_path_config" to mapOf("acl_enable" to false, "acl" to mapOf("path" to ""), "path" to ""),
        )
        val values = Schema.setAt(Schema.buildDefaults(questions), listOf("storage", "additional_storage"), listOf(item))
        @Suppress("UNCHECKED_CAST")
        val sent = Schema.forSubmit(questions, values as Map<String, Any?>)
        val first = (Schema.getAt(sent, listOf("storage", "additional_storage")) as List<*>)[0] as Map<*, *>
        assertFalse("host_path_config" in first)
        assertEquals("/media", first["mount_path"])
    }

    @Test
    fun serverErrorsUseTheFormLabels() {
        assertEquals(
            "Data Storage › Host Path: Empty value not allowed",
            Schema.describeErrors(questions, "values.storage.data.host_path_config.acl.path: Empty value not allowed"),
        )
        assertEquals(
            "Additional Storage 1 › Mount Path: Field required",
            Schema.describeErrors(questions, "[EINVAL] app_create.values.storage.additional_storage.0.mount_path: Field required"),
        )
        assertEquals("Timezone: Bad", Schema.describeErrors(questions, "values.TZ: Bad"))
        assertEquals("Something else broke", Schema.describeErrors(questions, "Something else broke"))
        assertNull(Schema.labelFor(questions, listOf("nope")))
        assertTrue(Schema.validate(questions, Schema.buildDefaults(questions)).isEmpty())
    }

    private companion object {
        val ACL = """
            {"variable": "acl", "label": "ACL Configuration", "schema": {"type": "dict", "show_if": [["acl_enable", "=", true]], "attrs": [
              {"variable": "path", "label": "Host Path", "schema": {"type": "hostpath", "required": true}},
              {"variable": "entries", "label": "ACL Entries", "schema": {"type": "list", "default": [], "items": []}},
              {"variable": "options", "label": "ACL Options", "schema": {"type": "dict", "attrs": [
                {"variable": "force", "label": "Force Flag", "schema": {"type": "boolean", "default": false}}]}}]}}
        """

        fun entry(dataset: String) = """
            {"variable": "type", "label": "Type", "schema": {"type": "string", "required": true, "default": "ix_volume", "enum": [
              {"value": "host_path", "description": "Host Path"}, {"value": "ix_volume", "description": "ixVolume"}]}},
            {"variable": "ix_volume_config", "label": "ixVolume Configuration", "schema": {"type": "dict", "show_if": [["type", "=", "ix_volume"]], "attrs": [
              {"variable": "acl_enable", "label": "Enable ACL", "schema": {"type": "boolean", "default": false}},
              {"variable": "dataset_name", "label": "Dataset Name", "schema": {"type": "string", "required": true, "hidden": true, "default": "$dataset"}},
              {"variable": "acl_entries", "label": "ACL Configuration", "schema": {"type": "dict", "show_if": [["acl_enable", "=", true]], "attrs": [
                {"variable": "path", "label": "Path", "schema": {"type": "string", "hidden": true, "default": ""}}]}}]}},
            {"variable": "host_path_config", "label": "Host Path Configuration", "schema": {"type": "dict", "show_if": [["type", "=", "host_path"]], "attrs": [
              {"variable": "acl_enable", "label": "Enable ACL", "schema": {"type": "boolean", "default": false}},
              $ACL,
              {"variable": "path", "label": "Host Path", "schema": {"type": "hostpath", "show_if": [["acl_enable", "=", false]], "required": true}}]}}
        """

        val STORAGE = """
        {"groups": [{"name": "Storage Configuration"}], "questions": [
          {"variable": "TZ", "label": "Timezone", "group": "Storage Configuration", "schema": {"type": "string", "default": "UTC", "required": true,
            "enum": [{"value": "UTC", "description": "'UTC' timezone"}]}},
          {"variable": "bind_ip", "label": "Bind IP", "group": "Storage Configuration", "schema": {"type": "ipaddr", "default": ""}},
          {"variable": "storage", "label": "", "group": "Storage Configuration", "schema": {"type": "dict", "attrs": [
            {"variable": "data", "label": "Data Storage", "schema": {"type": "dict", "attrs": [${entry("data")}]}},
            {"variable": "additional_storage", "label": "Additional Storage", "schema": {"type": "list", "default": [], "items": [
              {"variable": "storageEntry", "label": "Storage Entry", "schema": {"type": "dict", "attrs": [
                {"variable": "mount_path", "label": "Mount Path", "schema": {"type": "path", "required": true}},
                ${entry("storage_entry")}]}}]}}]}}
        ]}
        """
    }
}
