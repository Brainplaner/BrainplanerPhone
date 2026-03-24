package com.brainplaner.phone.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DURATION_OPTIONS = listOf(15, 30, 45, 60)

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartSession: suspend (plannedMinutes: Int) -> Result<String>,
    onStopSession: suspend () -> Result<String>,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var plannedMinutes by remember { mutableIntStateOf(45) }
    var activePlannedMinutes by remember { mutableIntStateOf(45) }
    var isSessionActive by remember { mutableStateOf(false) }
    var sessionStartMs by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var isActionLoading by remember { mutableStateOf(false) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Brain Budget", style = MaterialTheme.typography.labelMedium)
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        } else {
            Text(
                state.readinessScore?.let { "$it / 100" } ?: "—",
                style = MaterialTheme.typography.displaySmall,
            )
            state.planningAccuracyLine?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider()

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            state.sessionSummary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            state.handoffNextAction?.let {
                Text(
                    "Next step: $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.sessionSummary == null && state.handoffNextAction == null) {
                Text(
                    "No previous session",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider()

        if (!isSessionActive) {
            Text("Planned duration", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DURATION_OPTIONS.forEach { min ->
                    FilterChip(
                        selected = plannedMinutes == min,
                        onClick = { plannedMinutes = min },
                        label = { Text("${min}m") },
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (isActionLoading) return@Button
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
                },
                enabled = !isActionLoading,
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .align(Alignment.CenterHorizontally),
            ) {
                if (isActionLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Start Session")
                }
            }
        } else {
            val plannedSeconds = activePlannedMinutes * 60L
            val remaining = plannedSeconds - elapsedSeconds

            Text("Session active", style = MaterialTheme.typography.labelMedium)
            Text(formatDuration(elapsedSeconds), style = MaterialTheme.typography.displaySmall)
            Text(
                "Planned: ${activePlannedMinutes}m",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (remaining >= 0) "Remaining: ${formatDuration(remaining)}" else "Overtime: +${formatDuration(-remaining)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (remaining >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )

            Spacer(modifier = Modifier.weight(1f))

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
                    .fillMaxWidth(0.82f)
                    .align(Alignment.CenterHorizontally),
            ) {
                if (isActionLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Stop Session")
                }
            }
        }

        actionMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        TextButton(
            onClick = onLogout,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Switch User / Logout", color = MaterialTheme.colorScheme.error)
        }
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

