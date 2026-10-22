package app.truenasmobile.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

/**
 * The TrueNAS web shell: a WebSocket at /websocket/shell/. After sending a session token, the
 * server streams terminal output as binary frames and takes keystrokes the same way.
 */
class ShellClient(
    private val url: String,
    private val http: OkHttpClient,
    private val onConnected: (shellId: String) -> Unit,
    private val onOutput: (ByteArray) -> Unit,
    private val onClosed: (reason: String?) -> Unit,
) {
    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var connected = false

    fun start(token: String, options: Map<String, Any?> = emptyMap()) {
        socket = http.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(JSONObject().put("token", token).put("options", Json.obj(options)).toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (connected) {
                        onOutput(text.toByteArray(Charsets.UTF_8))
                        return
                    }
                    val msg = try {
                        JSONObject(text)
                    } catch (_: Exception) {
                        return
                    }
                    when (msg.optString("msg")) {
                        "connected" -> {
                            connected = true
                            onConnected(msg.optString("id"))
                        }
                        "failed" -> {
                            val reason = msg.optJSONObject("error")?.optString("reason")
                            onClosed(reason?.takeIf { it.isNotBlank() } ?: "The server refused the shell")
                            webSocket.close(1000, null)
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    onOutput(bytes.toByteArray())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    onClosed(reason.ifBlank { null })
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    onClosed(t.message ?: "Connection lost")
                }
            },
        )
    }

    /** Keystrokes must go as binary frames; the server writes them straight to the terminal. */
    fun send(data: ByteArray) {
        socket?.send(data.toByteString())
    }

    fun close() {
        socket?.close(1000, null)
        socket = null
    }
}
