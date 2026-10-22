package app.truenasmobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.ui.LocalChrome
import app.truenasmobile.ui.ScreenScaffold
import app.truenasmobile.ui.components.LoadingBox
import app.truenasmobile.ui.theme.AppColors

@Composable
fun LogsScreen(app: String, containerId: String, title: String) {
    val chrome = LocalChrome.current
    val vm = chrome.vm
    var session by remember { mutableIntStateOf(0) }
    DisposableEffect(app, containerId, session) {
        vm.startLogs(app, containerId)
        onDispose { vm.stopLogs() }
    }
    val lines by vm.logLines.collectAsStateWithLifecycle()
    val status by vm.logStatus.collectAsStateWithLifecycle()
    var follow by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(lines.size, follow) {
        if (follow && lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    ScreenScaffold(
        title = title,
        subtitle = "Logs · $app",
        showBack = true,
        actions = {
            IconButton(onClick = { clipboard.setText(AnnotatedString(lines.joinToString("\n"))) }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy logs")
            }
            IconButton(onClick = { session++ }) { Icon(Icons.Outlined.Refresh, contentDescription = "Restart stream") }
        },
        fab = {
            if (!follow) {
                SmallFloatingActionButton(onClick = { follow = true }) {
                    Icon(Icons.Outlined.VerticalAlignBottom, contentDescription = "Follow new lines")
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.status.warn,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (lines.isEmpty() && status == null) {
                LoadingBox()
            }
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(lines) { _, line ->
                        Text(
                            line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            color = logColor(line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }

    // Scrolling up to read stops auto-follow; the button brings it back.
    LaunchedEffect(listState) {
        var lastIndex = 0
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex to listState.isScrollInProgress }
            .collect { (first, scrolling) ->
                if (scrolling && first < lastIndex) follow = false
                lastIndex = first
            }
    }
}

@Composable
private fun logColor(line: String): androidx.compose.ui.graphics.Color {
    val s = AppColors.status
    val l = line.lowercase()
    return when {
        "error" in l || "fatal" in l || "panic" in l || "exception" in l -> s.bad
        "warn" in l -> s.warn
        else -> MaterialTheme.colorScheme.onSurface
    }
}
