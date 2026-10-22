package app.truenasmobile.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.truenasmobile.api.AlertLevel
import app.truenasmobile.api.App
import app.truenasmobile.api.Pool
import app.truenasmobile.ui.theme.AppColors

@Composable
fun poolColor(pool: Pool): Color {
    val s = AppColors.status
    return when {
        pool.status in setOf("FAULTED", "UNAVAIL", "OFFLINE", "REMOVED") -> s.bad
        !pool.healthy || pool.warning || pool.status != "ONLINE" -> s.warn
        else -> s.good
    }
}

@Composable
fun vdevColor(status: String): Color {
    val s = AppColors.status
    return when (status) {
        "ONLINE" -> s.good
        "DEGRADED" -> s.warn
        "AVAIL", "INUSE" -> s.info
        else -> s.bad
    }
}

@Composable
fun usageColor(fraction: Float): Color {
    val s = AppColors.status
    return when {
        fraction >= 0.9f -> s.bad
        fraction >= 0.8f -> s.warn
        else -> androidx.compose.material3.MaterialTheme.colorScheme.primary
    }
}

@Composable
fun appStateColor(app: App): Color {
    val s = AppColors.status
    return when (app.state) {
        "RUNNING" -> s.good
        "DEPLOYING", "STOPPING" -> s.info
        "CRASHED" -> s.bad
        else -> s.idle
    }
}

fun appStateLabel(state: String): String = when (state) {
    "RUNNING" -> "Running"
    "STOPPED" -> "Stopped"
    "DEPLOYING" -> "Deploying"
    "STOPPING" -> "Stopping"
    "CRASHED" -> "Crashed"
    else -> state.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
fun alertColor(level: AlertLevel): Color {
    val s = AppColors.status
    return when {
        level.rank >= AlertLevel.CRITICAL.rank -> s.bad
        level.rank >= AlertLevel.WARNING.rank -> s.warn
        level == AlertLevel.NOTICE -> s.info
        else -> s.idle
    }
}

@Composable
fun tempColor(celsius: Double, solidState: Boolean): Color {
    val s = AppColors.status
    val warnAt = if (solidState) 65.0 else 45.0
    val badAt = if (solidState) 75.0 else 55.0
    return when {
        celsius >= badAt -> s.bad
        celsius >= warnAt -> s.warn
        else -> s.good
    }
}

fun versionLabel(version: String): String =
    if (version.startsWith("TrueNAS", ignoreCase = true)) version.replace('-', ' ') else "TrueNAS $version"
