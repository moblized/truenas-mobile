package app.truenasmobile.net

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** An error returned by the TrueNAS middleware for a call. */
class RpcException(
    val code: Int,
    message: String,
    val errname: String? = null,
    val errno: Int? = null,
) : Exception(message) {
    val isMethodNotFound: Boolean get() = code == METHOD_NOT_FOUND
    val isNotAuthenticated: Boolean get() = errname == "ENOTAUTHENTICATED" || errname == "EACCES"

    companion object {
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
    }
}

class DisconnectedException(message: String = "Connection to the server was lost", cause: Throwable? = null) :
    IOException(message, cause)

class EndpointNotFoundException :
    IOException("This server has no /api/current endpoint. TrueNAS SCALE 25.04 or newer is required.")

/** A subscription update ("collection_update") pushed by the server. */
data class RpcEvent(
    val collection: String,
    /** "added", "changed", "removed", or "unsubscribed". */
    val msg: String,
    val id: Any?,
    val fields: Any?,
)

/**
 * JSON-RPC 2.0 over a WebSocket, the API TrueNAS SCALE 25.04+ exposes at /api/current
 * (and the only one left in TrueNAS 26, which removed REST).
 */
class RpcClient(
    private val url: String,
    private val http: OkHttpClient,
    private val hostnameVerifier: PinningHostnameVerifier? = null,
) {
    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Any?>>()
    private val _events = MutableSharedFlow<RpcEvent>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<RpcEvent> = _events

    private val _closed = CompletableDeferred<Throwable?>()

    /** Completes when the connection ends; the value is the error, or null for a normal close. */
    val closed: Deferred<Throwable?> get() = _closed

    @Volatile
    private var socket: WebSocket? = null

    val isOpen: Boolean get() = socket != null && !_closed.isCompleted

    suspend fun connect(timeoutMs: Long = 12_000) {
        val opened = CompletableDeferred<Unit>()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handle(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                val err = DisconnectedException(
                    if (reason.isNotBlank()) "Server closed the connection: $reason" else "Server closed the connection",
                )
                opened.completeExceptionally(err)
                shutdown(err)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                shutdown(DisconnectedException())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val err = mapFailure(t, response)
                opened.completeExceptionally(err)
                shutdown(err)
            }
        }
        socket = http.newWebSocket(Request.Builder().url(url).build(), listener)
        try {
            withTimeout(timeoutMs) { opened.await() }
        } catch (e: TimeoutCancellationException) {
            close()
            throw IOException("The server didn't answer in time")
        }
    }

    /**
     * Calls a middleware method. Params are positional, as in the TrueNAS API docs.
     * Returns org.json values (JSONObject, JSONArray, String, Number, Boolean) or null.
     */
    suspend fun call(method: String, params: List<Any?> = emptyList(), timeoutMs: Long = 60_000): Any? {
        val ws = socket ?: throw DisconnectedException("Not connected")
        if (_closed.isCompleted) throw DisconnectedException()
        val id = ids.getAndIncrement()
        val request = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", Json.array(params))
        val result = CompletableDeferred<Any?>()
        pending[id] = result
        if (!ws.send(request.toString())) {
            pending.remove(id)
            throw DisconnectedException()
        }
        try {
            return withTimeout(timeoutMs) { result.await() }
        } catch (e: TimeoutCancellationException) {
            throw IOException("The server took too long to answer ($method)")
        } finally {
            pending.remove(id)
        }
    }

    fun close() {
        socket?.close(1000, null)
        shutdown(null)
    }

    private fun shutdown(error: Throwable?) {
        if (_closed.complete(error)) {
            val err = error ?: DisconnectedException()
            for (d in pending.values) d.completeExceptionally(err)
            pending.clear()
        }
    }

    private fun handle(text: String) {
        val msg = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return
        }
        if (msg.has("method")) {
            val params = msg.optJSONObject("params") ?: return
            when (msg.optString("method")) {
                "collection_update" -> _events.tryEmit(
                    RpcEvent(
                        collection = params.optString("collection"),
                        msg = params.optString("msg"),
                        id = params.opt("id").jsonOrNull(),
                        fields = params.opt("fields").jsonOrNull(),
                    ),
                )
                "notify_unsubscribed" -> _events.tryEmit(
                    RpcEvent(
                        collection = params.optString("collection"),
                        msg = "unsubscribed",
                        id = null,
                        fields = params.opt("error").jsonOrNull(),
                    ),
                )
            }
            return
        }
        val id = when (val raw = msg.opt("id")) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        } ?: return
        val deferred = pending.remove(id) ?: return
        val error = msg.optJSONObject("error")
        if (error != null) {
            deferred.completeExceptionally(parseError(error))
        } else {
            deferred.complete(msg.opt("result").jsonOrNull())
        }
    }

    private fun parseError(error: JSONObject): RpcException {
        val code = error.optInt("code")
        val data = error.optJSONObject("data")
        var message = error.optString("message", "Request failed")
        var errname: String? = null
        var errno: Int? = null
        if (data != null) {
            errname = data.str("errname")
            errno = data.int("error")
            val reason = data.str("reason")
            val extra = data.optJSONArray("extra")
            val details = extra?.let { formatValidation(it) }
            message = when {
                !details.isNullOrBlank() -> details
                !reason.isNullOrBlank() -> reason
                else -> message
            }
        }
        if (code == RpcException.METHOD_NOT_FOUND) message = "Method does not exist"
        return RpcException(code, message.trim().removePrefix("[EINVAL] ").removePrefix("[EFAULT] "), errname, errno)
    }

    /** Validation errors arrive as [[attribute, message, errno], ...]. */
    private fun formatValidation(extra: JSONArray): String? {
        val lines = (0 until extra.length()).mapNotNull { i ->
            val item = extra.optJSONArray(i) ?: return@mapNotNull null
            // "app_create.values.storage.config.host_path_config.path" → "host_path_config › path"
            val attr = item.optString(0).split('.')
                .filter { it.isNotBlank() && it !in setOf("app_create", "values") && it.toIntOrNull() == null }
                .takeLast(2)
                .joinToString(" › ")
            val text = item.optString(1)
            if (text.isBlank()) null else if (attr.isBlank()) text else "$attr: $text"
        }
        return lines.joinToString("\n").ifBlank { null }
    }

    private fun mapFailure(t: Throwable, response: Response?): Throwable {
        var c: Throwable? = t
        while (c != null) {
            if (c is UntrustedCertificateException) return c
            c = c.cause
        }
        if (t is SSLPeerUnverifiedException) {
            val cert = hostnameVerifier?.lastRejected
            if (cert != null) {
                return UntrustedCertificateException(
                    CertInfo.of(cert, changed = false, reason = "The certificate is for a different name than the address you entered"),
                    t,
                )
            }
        }
        if (response != null) {
            return when (response.code) {
                404 -> EndpointNotFoundException()
                in 300..399 -> IOException(
                    "The server redirected the connection (HTTP ${response.code}). Try https:// or the exact address you use in a browser.",
                )
                else -> IOException("The server refused the connection (HTTP ${response.code})")
            }
        }
        return t
    }
}
