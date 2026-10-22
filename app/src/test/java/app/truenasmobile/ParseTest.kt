package app.truenasmobile

import app.truenasmobile.api.AlertLevel
import app.truenasmobile.api.Parse
import app.truenasmobile.ui.Format
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shapes taken from the TrueNAS 25.10 / 26 middleware API definitions. */
class ParseTest {

    @Test
    fun realtime() {
        val json = JSONObject(
            """
            {"cpu": {"cpu": {"usage": 12.5, "temp": null}, "cpu0": {"usage": 10.0, "temp": 41.0}, "cpu1": {"usage": 15.0, "temp": 44.0}},
             "disks": {"busy": 3.2, "read_bytes": 1048576, "write_bytes": 524288, "read_ops": 10, "write_ops": 5},
             "interfaces": {"eno1": {"link_state": "LINK_STATE_UP", "speed": 1000, "received_bytes_rate": 125000, "sent_bytes_rate": 2500},
                            "eno2": {"link_state": "LINK_STATE_DOWN", "speed": 0, "received_bytes_rate": 0, "sent_bytes_rate": 0}},
             "memory": {"arc_size": 8000000000, "arc_free_memory": 0, "arc_available_memory": 0,
                        "physical_memory_total": 32000000000, "physical_memory_available": 20000000000},
             "zfs": {"demand_data_hit_percentage": 97},
             "pools": {}}
            """.trimIndent(),
        )
        val r = Parse.realtime(json)!!
        assertEquals(12.5, r.cpuPercent, 0.01)
        assertEquals(44.0, r.cpuTemp!!, 0.01) // aggregate temp missing → hottest core
        assertEquals(2, r.coreUsage.size)
        assertEquals(125000.0, r.rxTotal, 0.1) // only links that are up
        assertEquals(4_000_000_000L, r.memServices)
        assertEquals(97, r.arcHitPercent)
        assertNull(Parse.realtime(JSONObject()))
    }

    @Test
    fun poolWithScanAndTopology() {
        val json = JSONObject(
            """
            {"id": 1, "name": "tank", "guid": "1", "status": "ONLINE", "path": "/mnt/tank", "healthy": true, "warning": false,
             "status_code": null, "status_detail": null, "size": 4000, "allocated": 1000, "free": 3000, "fragmentation": "3",
             "scan": {"function": "SCRUB", "state": "FINISHED", "start_time": {"${'$'}date": 1700000000000},
                      "end_time": {"${'$'}date": 1700003600000}, "percentage": 100.0, "errors": 0, "total_secs_left": null, "pause": null},
             "topology": {"data": [{"name": "mirror-0", "type": "MIRROR", "status": "ONLINE", "stats": {"read_errors": 0, "write_errors": 0, "checksum_errors": 0},
                           "children": [{"name": "a", "type": "DISK", "disk": "sda", "status": "ONLINE", "stats": {"read_errors": 0, "write_errors": 0, "checksum_errors": 2}, "children": []},
                                        {"name": "b", "type": "DISK", "disk": "sdb", "status": "ONLINE", "stats": {"read_errors": 0, "write_errors": 0, "checksum_errors": 0}, "children": []}]}],
                          "log": [], "cache": [], "spare": [], "special": [], "dedup": []}}
            """.trimIndent(),
        )
        val p = Parse.pool(json)
        assertEquals("tank", p.name)
        assertEquals(0.25f, p.usedFraction, 0.001f)
        assertEquals(1700003600000L, p.scan!!.endMillis)
        assertFalse(p.scan!!.paused)
        assertEquals(1, p.topology.size)
        assertEquals(2, p.diskCount)
        assertEquals("sda", p.topology[0].vdevs[0].children[0].disk)
        assertEquals(2L, p.topology[0].vdevs[0].children[0].errors)
    }

    @Test
    fun appWithPortsAndPortal() {
        val json = JSONObject(
            """
            {"name": "jellyfin", "id": "jellyfin", "state": "RUNNING", "upgrade_available": true, "latest_version": "1.2.0",
             "image_updates_available": false, "custom_app": false, "migrated": false, "human_version": "10.10.7_1.1.0", "version": "1.1.0",
             "metadata": {"title": "Jellyfin", "icon": "https://media.sys.truenas.net/apps/jellyfin/icons/icon.svg", "description": "Media server"},
             "active_workloads": {"containers": 1, "used_ports": [], "used_host_ips": [], "volumes": [], "images": [], "networks": [],
               "container_details": [{"id": "abc", "service_name": "jellyfin", "image": "jellyfin/jellyfin:10.10.7", "state": "running",
                 "port_config": [{"container_port": 8096, "protocol": "tcp", "host_ports": [{"host_port": 30013, "host_ip": "0.0.0.0"}, {"host_port": 30013, "host_ip": "::"}]}],
                 "volume_mounts": []}]},
             "notes": null, "portals": {"Web UI": "http://0.0.0.0:30013/"}, "version_details": null, "config": null}
            """.trimIndent(),
        )
        val a = Parse.app(json)
        assertEquals("Jellyfin", a.title)
        assertTrue(a.isRunning)
        assertTrue(a.hasUpdate)
        assertEquals(listOf("30013→8096/tcp"), a.containers[0].ports)
        assertEquals("http://0.0.0.0:30013/", a.portals["Web UI"])
        assertNull(a.notes)
    }

    @Test
    fun appStatsAndAlerts() {
        val stats = Parse.appStats(
            JSONArray("""[{"app_name": "plex", "cpu_usage": 7, "memory": 1024, "networks": [{"interface_name": "eth0", "rx_bytes": 10, "tx_bytes": 20}], "blkio": {"read": 0, "write": 0}}]"""),
        )
        assertEquals(7, stats["plex"]!!.cpuPercent)
        assertEquals(20L, stats["plex"]!!.txBytesPerSec)

        val alert = Parse.alert(
            JSONObject("""{"uuid": "u1", "level": "CRITICAL", "klass": "ZpoolCapacity", "formatted": "Space usage for pool tank is 91%.", "dismissed": false, "datetime": {"${'$'}date": 1700000000000}, "one_shot": false}"""),
        )
        assertEquals(AlertLevel.CRITICAL, alert.level)
        assertEquals(1700000000000L, alert.datetimeMillis)
    }

    @Test
    fun datasetProperties() {
        val d = Parse.dataset(
            JSONObject("""{"id": "tank/media", "type": "FILESYSTEM", "name": "tank/media", "pool": "tank", "encrypted": false, "locked": false,
                "used": {"parsed": 2048, "rawvalue": "2048", "value": "2K"}, "available": {"parsed": 4096}, "mountpoint": "/mnt/tank/media",
                "compressratio": {"value": "1.20x", "parsed": "1.20"}}"""),
        )
        assertEquals(2048L, d.used)
        assertEquals(4096L, d.available)
        assertEquals("/mnt/tank/media", d.mountpoint)
        assertEquals("1.20x", d.compressRatio)
        assertEquals(1, d.depth)
        assertEquals("media", d.shortName)
    }

    @Test
    fun temperaturesAndFormat() {
        val t = Parse.temperatures(JSONObject("""{"sda": 34, "sdb": null, "nvme0n1": 41.5}"""))
        assertEquals(34.0, t["sda"]!!, 0.01)
        assertNull(t["sdb"])
        assertNotNull(t["nvme0n1"])

        assertEquals("1.00 KiB", Format.bytes(1024))
        assertEquals("3.64 TiB", Format.bytes(4_000_000_000_000))
        assertEquals("8.00 Mb/s", Format.bitsPerSec(1_000_000.0))
        assertEquals("2d 3h", Format.duration(2 * 86400 + 3 * 3600 + 59))
    }
}
