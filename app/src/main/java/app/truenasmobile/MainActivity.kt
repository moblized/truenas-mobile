package app.truenasmobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
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

    /** Whether the app is showing its dark theme, so the home-screen icon can match. */
    private var darkTheme: Boolean? = null

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
            val systemDark = isSystemInDarkTheme()
            SideEffect {
                darkTheme = when (settings.theme) {
                    ThemeMode.SYSTEM -> systemDark
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            }
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
        // Switch to the dark (or light) app icon once the app is out of sight.
        if (!isChangingConfigurations) darkTheme?.let { AppIcon.sync(this, it) }
    }

    companion object {
        const val EXTRA_OPEN_ALERTS = "open_alerts"
    }
}
