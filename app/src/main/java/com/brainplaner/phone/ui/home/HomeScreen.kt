package com.brainplaner.phone.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brainplaner.phone.LocalStore
import com.brainplaner.phone.ui.theme.BrainTeal
import com.brainplaner.phone.ui.theme.BudgetGreen
import com.brainplaner.phone.ui.theme.BudgetRed
import com.brainplaner.phone.ui.theme.BudgetYellow
import androidx.compose.foundation.layout.Row
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

private val DURATION_OPTIONS = listOf(15, 30, 45, 60, 120)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartSession: suspend (plannedMinutes: Int) -> Result<String>,
    onStopSession: suspend () -> Result<String>,
    onBudgetDetail: () -> Unit,
){
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var plannedMinutes by remember { mutableIntStateOf(45) }
    var activePlannedMinutes by remember { mutableIntStateOf(45) }
    var isSessionActive by remember { mutableStateOf(false) }
    var sessionStartMs by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isActionLoading by remember { mutableStateOf(false) }
    var previousOffline by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val activeSession = LocalStore.getActiveSession(context) ?: return@LaunchedEffect
        activePlannedMinutes = activeSession.plannedMinutes
        sessionStartMs = activeSession.startMs
        isSessionActive = true
    }

    val startSessionAction: () -> Unit = {
        scope.launch {
            isActionLoading = true
            val result = onStartSession(plannedMinutes)
            actionMessage = result.fold(
                onSuccess = {
                    activePlannedMinutes = plannedMinutes
                    sessionStartMs = System.currentTimeMillis()
                    isSessionActive = true
                    it
                },
                onFailure = { e -> "Error: ${e.message ?: "Failed to start"}" },
            )
            isActionLoading = false
        }
    }

    LaunchedEffect(isSessionActive, sessionStartMs) {
        if (!isSessionActive || sessionStartMs <= 0L) {
            elapsedSeconds = 0L
            return@LaunchedEffect
        }
        while (isSessionActive) {
            elapsedSeconds = ((System.currentTimeMillis() - sessionStartMs) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }

    // Retry cloud enrichment periodically while on Home to handle Render cold starts.
    LaunchedEffect(Unit) {
        while (true) {
            delay(45_000L)
            viewModel.refreshCloudData()
        }
    }

    // Emit a simple reconnection message when cloud becomes reachable again.
    LaunchedEffect(state.isOffline) {
        if (previousOffline && !state.isOffline) {
            actionMessage = "✅ Reconnected — cloud sync updated"
        }
        previousOffline = state.isOffline
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── App header ──
        Text(
            "Brainplaner",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        if (state.isOffline) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "📴 Offline mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.cloudErrorReason?.let { reason ->
                        Text(
                            reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = { viewModel.refreshCloudData() }) {
                    Text("Retry now")
                }
            }
        } else {
            state.lastCloudSyncAtMs?.let { ts ->
                val minsAgo = max(0L, (System.currentTimeMillis() - ts) / 60_000L)
                Text(
                    "☁️ Synced ${minsAgo}m ago",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Brain Budget gauge card ──
        Card(
            onClick = onBudgetDetail,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "BRAIN BUDGET",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    val score = state.readinessScore?.toIntOrNull() ?: 0
                    BrainBudgetGauge(score = score, modifier = Modifier.size(160.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap for details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Pending recovery banner ──
        var pendingRecovery by remember {
            mutableStateOf(LocalStore.getPendingRecovery(context))
        }
        pendingRecovery?.let { recovery ->
            val minutesAgo = ((System.currentTimeMillis() - recovery.selectedAt) / 60_000).toInt()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = BudgetGreen.copy(alpha = 0.12f),
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    BudgetGreen.copy(alpha = 0.4f),
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${recovery.emoji} ${recovery.type} in progress",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            if (minutesAgo < 1) "just now" else "${minutesAgo}m ago",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "+${recovery.boostPoints} budget when confirmed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BudgetGreen,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                viewModel.applyRecoveryBoost(recovery.boostPoints)
                                LocalStore.clearPendingRecovery(context)
                                pendingRecovery = null
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BudgetGreen,
                            ),
                        ) {
                            Text("✅ Done!", style = MaterialTheme.typography.labelLarge)
                        }
                        TextButton(
                            onClick = {
                                LocalStore.clearPendingRecovery(context)
                                pendingRecovery = null
                            },
                        ) {
                            Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Continuity / handoff card ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "CONTINUITY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    state.sessionSummary?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    state.handoffNextAction?.let {
                        Text(
                            "▸ Next: $it",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (state.sessionSummary == null && state.handoffNextAction == null) {
                        Text(
                            "No previous session — start your first one!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Session controls ──
        if (!isSessionActive) {
            Text(
                "PLANNED DURATION",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DURATION_OPTIONS.forEach { min ->
                    FilterChip(
                        selected = plannedMinutes == min,
                        onClick = { plannedMinutes = min },
                        label = { Text("${min}m") },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isActionLoading) return@Button
                    startSessionAction()
                },
                enabled = !isActionLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (isActionLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Start Session", style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            // ── Active session with timer ring ──
            val plannedSeconds = activePlannedMinutes * 60L
            val remaining = plannedSeconds - elapsedSeconds
            val progress = (elapsedSeconds.toFloat() / plannedSeconds.toFloat()).coerceIn(0f, 2f)
            val isOvertime = remaining < 0

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (isOvertime) "OVERTIME" else "SESSION ACTIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOvertime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.sp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    SessionTimerRing(
                        progress = progress,
                        isOvertime = isOvertime,
                        modifier = Modifier.size(180.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                formatDuration(elapsedSeconds),
                                style = MaterialTheme.typography.displayMedium,
                            )
                            Text(
                                if (remaining >= 0) "-${formatDuration(remaining)}"
                                else "+${formatDuration(-remaining)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isOvertime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Planned: ${activePlannedMinutes}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (isActionLoading) return@Button
                    scope.launch {
                        isActionLoading = true
                        val result = onStopSession()
                        actionMessage = result.fold(
                            onSuccess = {
                                isSessionActive = false
                                sessionStartMs = 0L
                                viewModel.load()
                                it
                            },
                            onFailure = { e -> "Error: ${e.message ?: "Failed to stop"}" },
                        )
                        isActionLoading = false
                    }
                },
                enabled = !isActionLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                if (isActionLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Stop Session", style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        // ── Status / action messages ──
        actionMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ── Circular Brain Budget Gauge ──

@Composable
private fun BrainBudgetGauge(score: Int, modifier: Modifier = Modifier) {
    val fraction = (score / 100f).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 800),
        label = "gauge",
    )
    val gaugeColor = when {
        score >= 70 -> BudgetGreen
        score >= 40 -> BudgetYellow
        else -> BudgetRed
    }
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Track
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            // Fill
            drawArc(
                color = gaugeColor,
                startAngle = 135f,
                sweepAngle = 270f * animatedFraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score",
                style = MaterialTheme.typography.displayLarge.copy(
                    color = gaugeColor,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                "/ 100",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Session Timer Ring ──

@Composable
private fun SessionTimerRing(
    progress: Float,
    isOvertime: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val ringColor = if (isOvertime) BudgetRed else BrainTeal
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * clampedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3600L
    val minutes = (safeSeconds % 3600L) / 60L
    val seconds = safeSeconds % 60L
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
