package app.truenasmobile.api

import app.truenasmobile.net.arr
import app.truenasmobile.net.bool
import app.truenasmobile.net.dateMillis
import app.truenasmobile.net.double
import app.truenasmobile.net.doubles
import app.truenasmobile.net.int
import app.truenasmobile.net.jsonOrNull
import app.truenasmobile.net.keyList
import app.truenasmobile.net.long
import app.truenasmobile.net.obj
import app.truenasmobile.net.objects
import app.truenasmobile.net.str
import org.json.JSONArray
import org.json.JSONObject

/** JSON → model conversion. Lenient on purpose: field shapes drift between TrueNAS releases. */
object Parse {

    fun list(v: Any?): List<JSONObject> = (v as? JSONArray)?.objects() ?: emptyList()

    fun systemInfo(o: JSONObject) = SystemInfo(
        hostname = o.str("hostname") ?: "TrueNAS",
        version = o.str("version") ?: "",
        cpuModel = o.str("model") ?: "",
        cores = o.int("cores") ?: 0,
        physicalCores = o.int("physical_cores") ?: 0,
        physmem = o.long("physmem") ?: 0,
        uptimeSeconds = o.double("uptime_seconds") ?: 0.0,
        loadavg = o.arr("loadavg")?.doubles() ?: emptyList(),
        ecc = o.bool("ecc_memory"),
        product = o.str("system_product"),
        manufacturer = o.str("system_manufacturer"),
        timezone = o.str("timezone"),
    )

    fun realtime(o: JSONObject): Realtime? {
        val memory = o.obj("memory") ?: return null
        val cpu = o.obj("cpu") ?: JSONObject()
        val aggregate = cpu.obj("cpu") ?: cpu.obj("average")
        val cores = cpu.keyList()
            .filter { it.length > 3 && it.startsWith("cpu") && it.substring(3).all(Char::isDigit) }
            .sortedBy { it.substring(3).toInt() }
            .mapNotNull { cpu.obj(it) }
        val coreUsage = cores.map { it.double("usage") ?: 0.0 }
        val cpuPercent = aggregate?.double("usage") ?: if (coreUsage.isNotEmpty()) coreUsage.average() else 0.0
        val coreTemps = cores.mapNotNull { it.double("temp") }.filter { it > 0 }
        val cpuTemp = aggregate?.double("temp")?.takeIf { it > 0 } ?: coreTemps.maxOrNull()

        val ifaces = o.obj("interfaces")?.let { all ->
            all.keyList().mapNotNull { name ->
                val i = all.obj(name) ?: return@mapNotNull null
                IfaceStat(
                    name = name,
                    up = i.str("link_state") == "LINK_STATE_UP",
                    speedMbps = i.double("speed") ?: 0.0,
                    rxBytesPerSec = i.double("received_bytes_rate") ?: 0.0,
                    txBytesPerSec = i.double("sent_bytes_rate") ?: 0.0,
                )
            }
        } ?: emptyList()
        val disks = o.obj("disks")
        val zfs = o.obj("zfs")
        return Realtime(
            cpuPercent = cpuPercent.coerceIn(0.0, 100.0),
            cpuTemp = cpuTemp,
            coreUsage = coreUsage,
            memTotal = memory.long("physical_memory_total") ?: 0,
            memAvailable = memory.long("physical_memory_available") ?: 0,
            arcSize = memory.long("arc_size") ?: 0,
            interfaces = ifaces,
            diskReadBps = disks?.double("read_bytes") ?: 0.0,
            diskWriteBps = disks?.double("write_bytes") ?: 0.0,
            diskBusy = disks?.double("busy") ?: 0.0,
            arcHitPercent = zfs?.int("demand_data_hit_percentage"),
        )
    }

    fun pool(o: JSONObject): Pool {
        val topo = o.obj("topology")
        val groups = listOf(
            "data" to "Data",
            "special" to "Metadata",
            "dedup" to "Dedup",
            "log" to "Log",
            "cache" to "Cache",
            "spare" to "Spare",
        ).mapNotNull { (key, label) ->
            val vdevs = topo?.arr(key)?.objects()?.map(::vdev) ?: emptyList()
            if (vdevs.isEmpty()) null else VdevGroup(label, vdevs)
        }
        return Pool(
            id = o.int("id") ?: 0,
            name = o.str("name") ?: "?",
            status = o.str("status") ?: "UNKNOWN",
            healthy = o.bool("healthy", default = true),
            warning = o.bool("warning"),
            statusDetail = o.str("status_detail"),
            size = o.long("size"),
            allocated = o.long("allocated"),
            free = o.long("free"),
            fragmentation = o.str("fragmentation"),
            scan = o.obj("scan")?.let(::scan),
            topology = groups,
        )
    }

    fun scan(o: JSONObject): ScanInfo? {
        if (o.str("function") == null && o.str("state") == null) return null
        return ScanInfo(
            function = o.str("function"),
            state = o.str("state"),
            percentage = o.double("percentage"),
            errors = o.long("errors"),
            startMillis = o.dateMillis("start_time"),
            endMillis = o.dateMillis("end_time"),
            secondsLeft = o.long("total_secs_left"),
            paused = !o.isNull("pause"),
        )
    }

    fun vdev(o: JSONObject): Vdev {
        val stats = o.obj("stats")
        return Vdev(
            name = o.str("name") ?: "",
            type = o.str("type") ?: "",
            status = o.str("status") ?: "UNKNOWN",
            disk = o.str("disk"),
            readErrors = stats?.long("read_errors") ?: 0,
            writeErrors = stats?.long("write_errors") ?: 0,
            checksumErrors = stats?.long("checksum_errors") ?: 0,
            children = o.arr("children")?.objects()?.map(::vdev) ?: emptyList(),
        )
    }

    /** ZFS properties come as {"value", "rawvalue", "parsed", ...}; plain values are accepted too. */
    private fun propLong(o: JSONObject, key: String): Long? = when (val v = o.opt(key).jsonOrNull()) {
        is JSONObject -> v.long("parsed") ?: v.long("rawvalue")
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }

    private fun propString(o: JSONObject, key: String): String? = when (val v = o.opt(key).jsonOrNull()) {
        is JSONObject -> v.str("value") ?: v.str("parsed")
        null -> null
        else -> v.toString()
    }

    fun dataset(o: JSONObject): Dataset {
        val id = o.str("id") ?: o.str("name") ?: ""
        return Dataset(
            id = id,
            pool = o.str("pool") ?: id.substringBefore('/'),
            type = o.str("type") ?: "FILESYSTEM",
            used = propLong(o, "used"),
            available = propLong(o, "available"),
            mountpoint = propString(o, "mountpoint"),
            encrypted = o.bool("encrypted"),
            locked = o.bool("locked"),
            compressRatio = propString(o, "compressratio"),
        )
    }

    fun snapshot(o: JSONObject): Snapshot {
        val id = o.str("id") ?: o.str("name") ?: ""
        val props = o.obj("properties") ?: JSONObject()
        val creation = props.obj("creation")
        val created = creation?.dateMillis("parsed")
            ?: creation?.long("rawvalue")?.let { it * 1000 }
        return Snapshot(
            id = id,
            name = o.str("snapshot_name") ?: id.substringAfter('@'),
            dataset = o.str("dataset") ?: id.substringBefore('@'),
            createdMillis = created,
            used = propLong(props, "used"),
            referenced = propLong(props, "referenced"),
            createTxg = o.long("createtxg") ?: 0,
        )
    }

    fun disk(o: JSONObject, temps: Map<String, Double>): Disk {
        val name = o.str("name") ?: o.str("devname") ?: ""
        return Disk(
            name = name,
            serial = o.str("serial") ?: "",
            model = o.str("model"),
            size = o.long("size"),
            type = o.str("type"),
            pool = o.str("pool"),
            temperature = temps[name],
        )
    }

    /** disk.temperatures returns {"sda": 34, ...}, or per-disk objects on some versions. */
    fun temperatures(v: Any?): Map<String, Double> {
        val o = v as? JSONObject ?: return emptyMap()
        return o.keyList().mapNotNull { name ->
            val t = when (val raw = o.opt(name).jsonOrNull()) {
                is Number -> raw.toDouble()
                is JSONObject -> raw.double("temperature") ?: raw.double("temp")
                else -> null
            }
            if (t == null) null else name to t
        }.toMap()
    }

    fun app(o: JSONObject): App {
        val meta = o.obj("metadata") ?: JSONObject()
        val portals = o.obj("portals")?.let { p ->
            p.keyList().mapNotNull { k -> p.str(k)?.let { k to it } }.toMap()
        } ?: emptyMap()
        val workloads = o.obj("active_workloads")
        val containers = workloads?.arr("container_details")?.objects()?.map { c ->
            val ports = c.arr("port_config")?.objects()?.flatMap { pc ->
                val containerPort = pc.str("container_port") ?: ""
                val proto = pc.str("protocol") ?: "tcp"
                val hostPorts = pc.arr("host_ports")?.objects()?.mapNotNull { it.str("host_port") }?.distinct()
                if (hostPorts.isNullOrEmpty()) listOf("$containerPort/$proto")
                else hostPorts.map { hp -> if (hp == containerPort) "$hp/$proto" else "$hp→$containerPort/$proto" }
            } ?: emptyList()
            AppContainer(
                id = c.str("id") ?: "",
                serviceName = c.str("service_name") ?: "",
                image = c.str("image") ?: "",
                state = c.str("state") ?: "",
                ports = ports.distinct(),
            )
        } ?: emptyList()
        val name = o.str("name") ?: o.str("id") ?: "?"
        return App(
            name = name,
            state = o.str("state") ?: "UNKNOWN",
            version = o.str("version") ?: "",
            humanVersion = o.str("human_version") ?: o.str("version") ?: "",
            latestVersion = o.str("latest_version"),
            upgradeAvailable = o.bool("upgrade_available"),
            imageUpdatesAvailable = o.bool("image_updates_available"),
            customApp = o.bool("custom_app"),
            title = meta.str("title")?.takeIf { it.isNotBlank() } ?: name,
            description = meta.str("description"),
            iconUrl = meta.str("icon")?.takeIf { it.startsWith("http") },
            portals = portals,
            containers = containers,
            notes = o.str("notes"),
        )
    }

    fun appStats(fields: Any?): Map<String, AppStat> {
        val items = when (fields) {
            is JSONArray -> fields.objects()
            is JSONObject -> fields.arr("result")?.objects() ?: emptyList()
            else -> emptyList()
        }
        return items.associate { s ->
            val nets = s.arr("networks")?.objects() ?: emptyList()
            (s.str("app_name") ?: "") to AppStat(
                cpuPercent = s.int("cpu_usage") ?: 0,
                memory = s.long("memory") ?: 0,
                rxBytesPerSec = nets.sumOf { it.long("rx_bytes") ?: 0 },
                txBytesPerSec = nets.sumOf { it.long("tx_bytes") ?: 0 },
            )
        }
    }

    fun alert(o: JSONObject) = Alert(
        uuid = o.str("uuid") ?: o.str("id") ?: "",
        level = AlertLevel.of(o.str("level")),
        klass = o.str("klass") ?: "",
        text = (o.str("formatted") ?: o.str("text") ?: "").trim(),
        dismissed = o.bool("dismissed"),
        datetimeMillis = o.dateMillis("datetime") ?: o.dateMillis("last_occurrence"),
        oneShot = o.bool("one_shot"),
    )

    fun service(o: JSONObject) = ServiceEntry(
        id = o.int("id") ?: 0,
        service = o.str("service") ?: "",
        enable = o.bool("enable"),
        state = o.str("state") ?: "UNKNOWN",
    )

    fun smbShare(o: JSONObject) = SmbShare(
        id = o.int("id") ?: 0,
        name = o.str("name") ?: "",
        path = o.str("path") ?: "",
        enabled = o.bool("enabled", default = true),
        comment = o.str("comment")?.takeIf { it.isNotBlank() },
    )

    fun nfsShare(o: JSONObject) = NfsShare(
        id = o.int("id") ?: 0,
        path = o.str("path") ?: o.arr("paths")?.optString(0) ?: "",
        enabled = o.bool("enabled", default = true),
        comment = o.str("comment")?.takeIf { it.isNotBlank() },
    )
}
