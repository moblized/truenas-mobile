package app.truenasmobile.net

/**
 * Turns whatever the user typed ("192.168.1.10", "nas.local:8443", "https://nas.example.com/ui")
 * into the TrueNAS WebSocket API address. HTTPS is assumed unless http:// is typed explicitly.
 */
data class Endpoint(
    val secure: Boolean,
    val host: String,
    val port: Int?,
) {
    private val hostPort: String get() = if (port != null) "$host:$port" else host

    /** JSON-RPC 2.0 WebSocket API, TrueNAS 25.04 and newer. */
    val wsUrl: String get() = "${if (secure) "wss" else "ws"}://$hostPort/api/current"

    /** The web shell (System → Shell in the web UI). */
    val shellUrl: String get() = "${if (secure) "wss" else "ws"}://$hostPort/websocket/shell/"

    /** The web UI, for "open in browser". */
    val webUrl: String get() = "${if (secure) "https" else "http"}://$hostPort"

    /** Host without IPv6 brackets, for building app web-portal links. */
    val bareHost: String get() = host.removePrefix("[").removeSuffix("]")

    companion object {
        private val scheme = Regex("^(https?|wss?)://", RegexOption.IGNORE_CASE)

        fun parse(input: String): Endpoint? {
            var s = input.trim()
            if (s.isEmpty() || s.any { it.isWhitespace() }) return null
            val insecure = s.startsWith("http://", ignoreCase = true) || s.startsWith("ws://", ignoreCase = true)
            s = s.replace(scheme, "")
            s = s.substringBefore('/').substringBefore('?').substringBefore('#')
            if (s.contains('@')) s = s.substringAfterLast('@')
            if (s.isEmpty()) return null

            val host: String
            val port: Int?
            when {
                s.startsWith("[") -> {
                    if (!s.contains(']')) return null
                    host = s.substringBefore(']') + "]"
                    val rest = s.substringAfter(']')
                    port = if (rest.isEmpty()) null else rest.removePrefix(":").toIntOrNull() ?: return null
                }
                s.count { it == ':' } == 1 -> {
                    host = s.substringBefore(':')
                    port = s.substringAfter(':').toIntOrNull() ?: return null
                }
                s.count { it == ':' } > 1 -> {
                    // Bare IPv6 address without a port
                    host = "[$s]"
                    port = null
                }
                else -> {
                    host = s
                    port = null
                }
            }
            if (host.isBlank() || host == "[]") return null
            if (port != null && port !in 1..65535) return null
            return Endpoint(secure = !insecure, host = host.lowercase(), port = port)
        }
    }
}
