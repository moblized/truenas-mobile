package app.truenasmobile.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.api.resizeShell
import app.truenasmobile.api.shellToken
import app.truenasmobile.data.Connector
import app.truenasmobile.net.Http
import app.truenasmobile.net.ShellClient
import app.truenasmobile.ui.ConnState
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.ScreenScaffold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * A terminal on the server, like System → Shell in the web UI. With [appName] and [containerId]
 * it opens a shell inside that app's container instead.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ShellScreen(appName: String? = null, containerId: String? = null, title: String = "Shell") {
    val vm = LocalChrome.current.vm
    val active by vm.active.collectAsStateWithLifecycle()
    val conn by vm.conn.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }
    var status by remember { mutableStateOf<String?>("Connecting…") }
    var session by remember { mutableIntStateOf(0) }
    var fontSize by remember { mutableIntStateOf(13) }
    val web = remember { mutableStateOf<WebView?>(null) }
    val holder = remember { arrayOfNulls<ShellClient>(1) }
    val shellId = remember { arrayOfNulls<String>(1) }
    val size = remember { intArrayOf(80, 24) }
    var pageReady by remember { mutableStateOf(false) }

    fun js(code: String) = main.post { web.value?.evaluateJavascript(code, null) }

    fun write(bytes: ByteArray) {
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        js("window.termWrite('$b64')")
    }

    fun message(text: String) = js("window.termMessage(${org.json.JSONObject.quote(text)})")

    fun sendKeys(s: String) = holder[0]?.send(s.toByteArray(Charsets.UTF_8))

    // Open the shell once the page and the API connection are ready.
    DisposableEffect(session, pageReady, conn == ConnState.Connected) {
        val profile = active
        val endpoint = profile?.endpoint
        if (pageReady && conn == ConnState.Connected && profile != null && endpoint != null) {
            status = "Connecting…"
            scope.launch {
                try {
                    val token = vm.withApi { it.shellToken() }
                    val bundle = Http.build(profile.certPin)
                    val client = ShellClient(
                        endpoint.shellUrl,
                        bundle.client,
                        onConnected = { id ->
                            shellId[0] = id
                            main.post { status = null }
                            scope.launch { vm.withApi { it.resizeShell(id, size[0], size[1]) } }
                        },
                        onOutput = { write(it) },
                        onClosed = { reason ->
                            main.post { status = "Disconnected" + (reason?.let { ": $it" } ?: "") }
                            message("[disconnected${reason?.let { ": $it" } ?: ""}]")
                        },
                    )
                    holder[0] = client
                    val options = if (appName != null && containerId != null) {
                        mapOf("app_name" to appName, "container_id" to containerId, "command" to "/bin/sh")
                    } else {
                        emptyMap()
                    }
                    client.start(token, options)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    status = Connector.friendly(e)
                }
            }
        }
        onDispose {
            holder[0]?.close()
            holder[0] = null
        }
    }

    ScreenScaffold(
        title = title,
        subtitle = status ?: (appName?.let { "Inside $it" } ?: active?.displayName),
        showBack = true,
        actions = {
            IconButton(onClick = {
                fontSize = (fontSize - 1).coerceAtLeast(8)
                js("window.termFontSize($fontSize)")
            }) { Icon(Icons.Outlined.ZoomOut, contentDescription = "Smaller text") }
            IconButton(onClick = {
                fontSize = (fontSize + 1).coerceAtMost(22)
                js("window.termFontSize($fontSize)")
            }) { Icon(Icons.Outlined.ZoomIn, contentDescription = "Larger text") }
            IconButton(onClick = {
                holder[0]?.close()
                session++
            }) { Icon(Icons.Outlined.Refresh, contentDescription = "Reconnect") }
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0F1418)),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            setBackgroundColor(android.graphics.Color.parseColor("#0F1418"))
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = false
                            settings.allowContentAccess = false
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun input(data: String) {
                                        sendKeys(data)
                                    }

                                    @JavascriptInterface
                                    fun resize(cols: Int, rows: Int) {
                                        size[0] = cols
                                        size[1] = rows
                                        val id = shellId[0] ?: return
                                        scope.launch { vm.withApi { it.resizeShell(id, cols, rows) } }
                                    }

                                    @JavascriptInterface
                                    fun ready(cols: Int, rows: Int) {
                                        size[0] = cols
                                        size[1] = rows
                                        main.post { pageReady = true }
                                    }
                                },
                                "Bridge",
                            )
                            loadUrl("file:///android_asset/terminal/index.html")
                            web.value = this
                        }
                    },
                )
            }
            // Keys phone keyboards don't have.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val keys = listOf(
                    "Esc" to "\u001b", "Tab" to "\t", "Ctrl-C" to "\u0003", "Ctrl-D" to "\u0004", "Ctrl-Z" to "\u001a", "Ctrl-L" to "\u000c",
                    "↑" to "\u001b[A", "↓" to "\u001b[B", "←" to "\u001b[D", "→" to "\u001b[C",
                    "|" to "|", "/" to "/", "-" to "-", "~" to "~",
                )
                keys.forEach { (label, seq) ->
                    AssistChip(onClick = {
                        sendKeys(seq)
                        js("window.termFocus()")
                    }, label = { Text(label) })
                }
            }
        }
    }
}
