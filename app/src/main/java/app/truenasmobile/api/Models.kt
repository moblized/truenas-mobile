package app.truenasmobile.api

data class SystemInfo(
    val hostname: String,
    val version: String,
    val cpuModel: String,
    val cores: Int,
    val physicalCores: Int,
    val physmem: Long,
    val uptimeSeconds: Double,
    val loadavg: List<Double>,
    val ecc: Boolean,
    val product: String?,
    val manufacturer: String?,
    val timezone: String?,
    /** Phone clock when this was fetched, to keep the uptime counter ticking locally. */
    val fetchedAt: Long = System.currentTimeMillis(),
)

data class IfaceStat(
    val name: String,
    val up: Boolean,
    val speedMbps: Double,
    val rxBytesPerSec: Double,
    val txBytesPerSec: Double,
)

data class Realtime(
    val cpuPercent: Double,
    val cpuTemp: Double?,
    val coreUsage: List<Double>,
    val memTotal: Long,
    val memAvailable: Long,
    val arcSize: Long,
    val interfaces: List<IfaceStat>,
    val diskReadBps: Double,
    val diskWriteBps: Double,
    val diskBusy: Double,
    val arcHitPercent: Int?,
) {
    /** Memory used by services/apps (not counting the ZFS cache, which is given back on demand). */
    val memServices: Long get() = (memTotal - memAvailable - arcSize).coerceAtLeast(0)
    val memFree: Long get() = (memTotal - memServices - arcSize).coerceAtLeast(0)
    val rxTotal: Double get() = countedLinks.sumOf { it.rxBytesPerSec }
    val txTotal: Double get() = countedLinks.sumOf { it.txBytesPerSec }

    /**
     * Links whose traffic is added up. When a bridge or bond is up, its member NICs carry the
     * same packets, so only the bridge/bond is counted.
     */
    val countedLinks: List<IfaceStat>
        get() {
            val up = interfaces.filter { it.up }
            val aggregates = up.filter { it.name.startsWith("br") || it.name.startsWith("bond") }
            return aggregates.ifEmpty { up }
        }
}

data class ScanInfo(
    /** "SCRUB" or "RESILVER". */
    val function: String?,
    /** "SCANNING", "FINISHED" or "CANCELED". */
    val state: String?,
    val percentage: Double?,
    val errors: Long?,
    val startMillis: Long?,
    val endMillis: Long?,
    val secondsLeft: Long?,
    val paused: Boolean,
) {
    val running: Boolean get() = state == "SCANNING"
}

data class Vdev(
    val name: String,
    val type: String,
    val status: String,
    val disk: String?,
    val readErrors: Long,
    val writeErrors: Long,
    val checksumErrors: Long,
    val children: List<Vdev>,
) {
    val errors: Long get() = readErrors + writeErrors + checksumErrors
}

data class VdevGroup(val role: String, val vdevs: List<Vdev>)

data class Pool(
    val id: Int,
    val name: String,
    val status: String,
    val healthy: Boolean,
    val warning: Boolean,
    val statusDetail: String?,
    val size: Long?,
    val allocated: Long?,
    val free: Long?,
    val fragmentation: String?,
    val scan: ScanInfo?,
    val topology: List<VdevGroup>,
) {
    val usedFraction: Float
        get() = if (size != null && size > 0 && allocated != null) (allocated.toDouble() / size).toFloat().coerceIn(0f, 1f) else 0f
    val diskCount: Int
        get() = topology.sumOf { g -> g.vdevs.sumOf { v -> if (v.children.isEmpty()) 1 else v.children.size } }
}

data class Dataset(
    val id: String,
    val pool: String,
    val type: String,
    val used: Long?,
    val available: Long?,
    val mountpoint: String?,
    val encrypted: Boolean,
    val locked: Boolean,
    val compressRatio: String?,
) {
    val depth: Int get() = id.count { it == '/' }
    val shortName: String get() = id.substringAfterLast('/')
}

data class Snapshot(
    val id: String,
    val name: String,
    val dataset: String,
    val createdMillis: Long?,
    val used: Long?,
    val referenced: Long?,
    val createTxg: Long,
)

data class Disk(
    val name: String,
    val serial: String,
    val model: String?,
    val size: Long?,
    val type: String?,
    val pool: String?,
    val temperature: Double?,
) {
    val isSolidState: Boolean get() = type == "SSD" || name.startsWith("nvme")
}

data class AppContainer(
    val id: String,
    val serviceName: String,
    val image: String,
    val state: String,
    val ports: List<String>,
)

data class App(
    val name: String,
    val state: String,
    val version: String,
    val humanVersion: String,
    val latestVersion: String?,
    val upgradeAvailable: Boolean,
    val imageUpdatesAvailable: Boolean,
    val customApp: Boolean,
    val title: String,
    val description: String?,
    val iconUrl: String?,
    val portals: Map<String, String>,
    val containers: List<AppContainer>,
    val notes: String?,
) {
    val isRunning: Boolean get() = state == "RUNNING"
    val isStopped: Boolean get() = state == "STOPPED" || state == "CRASHED"
    val hasUpdate: Boolean get() = upgradeAvailable || imageUpdatesAvailable
}

data class AppStat(
    val cpuPercent: Int,
    val memory: Long,
    val rxBytesPerSec: Long,
    val txBytesPerSec: Long,
)

enum class AlertLevel(val rank: Int, val label: String) {
    INFO(0, "Info"),
    NOTICE(1, "Notice"),
    WARNING(2, "Warning"),
    ERROR(3, "Error"),
    CRITICAL(4, "Critical"),
    ALERT(5, "Alert"),
    EMERGENCY(6, "Emergency");

    companion object {
        fun of(s: String?): AlertLevel = entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: INFO
    }
}

data class Alert(
    val uuid: String,
    val level: AlertLevel,
    val klass: String,
    val text: String,
    val dismissed: Boolean,
    val datetimeMillis: Long?,
    val oneShot: Boolean,
)

data class ServiceEntry(
    val id: Int,
    val service: String,
    val enable: Boolean,
    val state: String,
) {
    val running: Boolean get() = state == "RUNNING"
}

data class SmbShare(val id: Int, val name: String, val path: String, val enabled: Boolean, val comment: String?)

data class NfsShare(val id: Int, val path: String, val enabled: Boolean, val comment: String?)

data class JobProgress(val percent: Int?, val description: String?)

sealed interface LoginResult {
    data object Success : LoginResult
    data object OtpRequired : LoginResult
    data class Failed(val message: String) : LoginResult
}
