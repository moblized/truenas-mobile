package app.truenasmobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.truenasmobile.data.ThemeMode
import app.truenasmobile.ui.AppRoot
import app.truenasmobile.ui.AppViewModel
import app.truenasmobile.ui.theme.AppTheme
import app.truenasmobile.work.AlertWorker
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.svg.SvgDecoder

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** Bumped when a notification asks to open the Alerts tab. */
    private var openAlertsSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(SvgDecoder.Factory()) }
                .crossfade(true)
                .build()
        }
        AlertWorker.ensureChannel(this)
        if (intent?.getBooleanExtra(EXTRA_OPEN_ALERTS, false) == true) openAlertsSignal++

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            AppTheme(
                mode = settings.theme,
                dynamicColor = settings.dynamicColor,
                pureBlack = settings.pureBlack && settings.theme != ThemeMode.LIGHT,
            ) {
                AppRoot(vm = vm, openAlertsSignal = openAlertsSignal)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_ALERTS, false)) openAlertsSignal++
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        super.onStop()
        vm.onBackground()
    }

    companion object {
        const val EXTRA_OPEN_ALERTS = "open_alerts"
    }
}
