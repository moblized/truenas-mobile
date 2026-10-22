package app.truenasmobile.ui

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Format {
    private val binaryUnits = listOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")

    fun bytes(value: Long?): String {
        if (value == null) return "—"
        var v = value.toDouble()
        var i = 0
        while (abs(v) >= 1024 && i < binaryUnits.lastIndex) {
            v /= 1024
            i++
        }
        return if (i == 0) "${value} B" else "${number(v)} ${binaryUnits[i]}"
    }

    fun bytesPerSec(value: Double): String = bytes(value.toLong()) + "/s"

    /** Network speeds read better in bits: "312 Mb/s". */
    fun bitsPerSec(bytesPerSec: Double): String {
        var v = bytesPerSec * 8
        val units = listOf("b/s", "kb/s", "Mb/s", "Gb/s", "Tb/s")
        var i = 0
        while (v >= 1000 && i < units.lastIndex) {
            v /= 1000
            i++
        }
        return "${number(v)} ${units[i]}"
    }

    /** 3 significant digits: 1.23, 12.3, 123 */
    fun number(v: Double): String = when {
        abs(v) >= 100 -> String.format(Locale.US, "%.0f", v)
        abs(v) >= 10 -> String.format(Locale.US, "%.1f", v)
        else -> String.format(Locale.US, "%.2f", v)
    }

    fun percent(v: Double): String = "${v.toInt().coerceIn(0, 100)}%"

    fun duration(seconds: Long): String {
        if (seconds < 60) return "${seconds}s"
        val d = seconds / 86400
        val h = (seconds % 86400) / 3600
        val m = (seconds % 3600) / 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    fun ago(millis: Long?, now: Long = System.currentTimeMillis()): String {
        if (millis == null) return ""
        val s = (now - millis) / 1000
        return when {
            s < 0 -> dateTime(millis)
            s < 60 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            s < 86400 -> "${s / 3600} h ago"
            s < 86400 * 30 -> "${s / 86400} d ago"
            else -> date(millis)
        }
    }

    fun dateTime(millis: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

    fun date(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

    fun temp(c: Double): String = "${c.toInt()}°C"

    private val serviceNames = mapOf(
        "cifs" to "SMB",
        "nfs" to "NFS",
        "ssh" to "SSH",
        "ftp" to "FTP",
        "iscsitarget" to "iSCSI",
        "ups" to "UPS",
        "snmp" to "SNMP",
        "smartd" to "S.M.A.R.T.",
        "nvmet" to "NVMe-oF",
        "webshare" to "WebShare",
        "rsync" to "Rsync",
        "lldp" to "LLDP",
        "truecommand" to "TrueCommand",
        "openvpn_client" to "OpenVPN client",
        "openvpn_server" to "OpenVPN server",
        "dynamicdns" to "Dynamic DNS",
        "s3" to "S3",
        "kmip" to "KMIP",
        "docker" to "Apps (Docker)",
        "incus" to "Containers",
        "libvirtd" to "Virtual machines",
        "nfsd" to "NFS",
        "mdns" to "mDNS",
        "netbios" to "NetBIOS",
        "wsd" to "WS-Discovery",
    )

    fun serviceName(id: String): String = serviceNames[id] ?: id.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** "ONLINE" → "Online" */
    fun title(s: String): String = s.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
