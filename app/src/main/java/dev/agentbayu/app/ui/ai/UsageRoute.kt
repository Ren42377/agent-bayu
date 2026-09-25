package dev.agentbayu.app.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.agentbayu.app.AppGraph
import dev.agentbayu.app.R
import dev.agentbayu.app.ai.ConnectionTestResult
import dev.agentbayu.app.ai.UsageStats
import dev.agentbayu.app.ai.accountEmailOf
import dev.agentbayu.app.ai.planTypeOf
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

@Composable
fun AiUsageRoute(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionsStore = remember(context) { AppGraph.connections(context) }
    val catalogRepository = remember(context) { AppGraph.catalogRepository(context) }
    val catalog by catalogRepository.catalog.collectAsState()
    val credentials = remember(context) { AppGraph.credentials(context) }
    val usage = remember(context) { AppGraph.usage(context) }
    val quota = remember(context) { AppGraph.quota(context) }
    val tester = remember(context) { AppGraph.connectionTester(context) }
    val scope = rememberCoroutineScope()

    val connections by connectionsStore.connections.collectAsState()
    val snapshots by quota.snapshots.collectAsState()
    val stats by usage.stats.collectAsState()

    var refreshing by remember { mutableStateOf(false) }

    val rows = remember(connections, snapshots, stats, catalog) {
        connections.map { connection ->
            val provider = catalog.find(connection.providerId)
            UsageRowState(
                connectionId = connection.id,
                label = connection.label,
                providerLabel = provider?.label ?: connection.providerId,
                model = connection.model,
                plan = planTypeOf(credentials.credential(connection.id), provider),
                email = accountEmailOf(credentials.credential(connection.id)),
                quota = snapshots[connection.id],
                stats = stats[connection.id] ?: UsageStats()
            )
        }
    }

    val refreshedMessage = stringResource(R.string.usage_refreshed)
    val failedTemplate = stringResource(R.string.usage_refresh_failed)
    val resetTemplate = stringResource(R.string.usage_reset)
    val resetNowText = stringResource(R.string.usage_reset_now)
    val minutesTemplate = stringResource(R.string.usage_minutes)
    val hoursTemplate = stringResource(R.string.usage_hours)
    val daysTemplate = stringResource(R.string.usage_days)

    fun resetText(resetAtMillis: Long): String {
        val remaining = resetAtMillis - System.currentTimeMillis()
        if (remaining <= 0L) return resetNowText
        val minutes = max(1, (remaining / MINUTE_MILLIS).toInt())
        val text = when {
            minutes < MINUTES_PER_HOUR -> minutesTemplate.format(minutes)
            minutes < MINUTES_PER_DAY -> hoursTemplate.format(minutes / MINUTES_PER_HOUR)
            else -> daysTemplate.format(minutes / MINUTES_PER_DAY)
        }
        return resetTemplate.format(text)
    }

    val formatter = remember {
        DateTimeFormatter.ofPattern(UPDATED_PATTERN, Locale.getDefault())
    }
    val zone = remember { ZoneId.systemDefault() }

    fun updatedText(millis: Long): String =
        formatter.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun refresh() {
        if (refreshing) return
        refreshing = true
        scope.launch {
            var failures = 0
            connections.forEach { connection ->
                val provider = catalog.find(connection.providerId)
                if (provider?.authKind?.isOAuth != true) return@forEach
                when (val result = tester.test(connection)) {
                    is ConnectionTestResult.Success -> Unit
                    is ConnectionTestResult.Failure -> failures += 1
                }
            }
            refreshing = false
            if (failures == 0) {
                onMessage(refreshedMessage)
            } else {
                onMessage(failedTemplate.format(failures.toString()))
            }
        }
    }

    UsageScreen(
        rows = rows,
        onBack = onBack,
        onRefresh = { refresh() },
        refreshing = refreshing,
        updatedText = { millis -> updatedText(millis) },
        resetText = { millis -> resetText(millis) },
        modifier = modifier
    )
}

private const val MINUTE_MILLIS = 60_000L
private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * 60
private const val UPDATED_PATTERN = "yyyy-MM-dd HH:mm"
