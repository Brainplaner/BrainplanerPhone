package com.brainplaner.phone.ui.warmup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brainplaner.phone.ui.theme.BrainDeep
import com.brainplaner.phone.ui.theme.BudgetGreen
import com.brainplaner.phone.ui.theme.BudgetRed
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val TOTAL_TRIALS = 5

private enum class WarmupPhase {
    COUNTDOWN, WAITING, GO, TOO_EARLY
}

@Composable
fun CognitiveWarmupScreen(
    baselineMs: Int?,
    onComplete: (medianMs: Int) -> Unit,
    onSkip: () -> Unit,
) {
    BackHandler { onSkip() }

    var phase by remember { mutableStateOf(WarmupPhase.COUNTDOWN) }
    var currentTrial by remember { mutableIntStateOf(0) }
    val reactionTimes = remember { mutableStateListOf<Long>() }
    var goTimestamp by remember { mutableLongStateOf(0L) }
    var countdownValue by remember { mutableIntStateOf(3) }

    // Phase-driven transitions
    LaunchedEffect(phase) {
        when (phase) {
            WarmupPhase.COUNTDOWN -> {
                for (i in 3 downTo 1) {
                    countdownValue = i
                    delay(700L)
                }
                phase = WarmupPhase.WAITING
            }
            WarmupPhase.WAITING -> {
                val waitMs = 1200L + Random.nextLong(1800L)
                delay(waitMs)
                goTimestamp = System.nanoTime()
                phase = WarmupPhase.GO
            }
            WarmupPhase.TOO_EARLY -> {
                delay(1200L)
                phase = WarmupPhase.WAITING
            }
            else -> {}
        }
    }

    // Auto-complete after all trials
    LaunchedEffect(currentTrial) {
        if (currentTrial >= TOTAL_TRIALS && reactionTimes.isNotEmpty()) {
            val sorted = reactionTimes.sorted()
            val median = if (sorted.size % 2 == 0) {
                ((sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2).toInt()
            } else {
                sorted[sorted.size / 2].toInt()
            }
            onComplete(median)
        }
    }

    val bgColor = when (phase) {
        WarmupPhase.COUNTDOWN -> MaterialTheme.colorScheme.background
        WarmupPhase.WAITING -> BrainDeep
        WarmupPhase.GO -> BudgetGreen
        WarmupPhase.TOO_EARLY -> BudgetRed
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        when (phase) {
            WarmupPhase.COUNTDOWN -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🧠", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Cognitive Warm-up",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Warming up while we sync your data…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        "$countdownValue",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 96.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Tap the green screen as fast as you can",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(onClick = onSkip) {
                        Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            WarmupPhase.WAITING -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { phase = WarmupPhase.TOO_EARLY },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Warm up your brain while we sync your data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                        Text(
                            "5 quick taps — establishes your daily baseline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.35f),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Wait...",
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Trial ${currentTrial + 1} of $TOTAL_TRIALS",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                }
            }

            WarmupPhase.GO -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            val reactionMs = (System.nanoTime() - goTimestamp) / 1_000_000
                            if (reactionMs < 100) {
                                // Too fast — likely anticipatory tap, treat as false start
                                phase = WarmupPhase.TOO_EARLY
                            } else {
                                reactionTimes.add(reactionMs)
                                currentTrial++
                                if (currentTrial < TOTAL_TRIALS) {
                                    phase = WarmupPhase.WAITING
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Warm up your brain while we sync your data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                        Text(
                            "5 quick taps — establishes your daily baseline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.35f),
                        )
                    }
                    Text(
                        "TAP!",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 72.sp,
                        ),
                        color = Color.White,
                    )
                }
            }

            WarmupPhase.TOO_EARLY -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Warm up your brain while we sync your data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                        Text(
                            "5 quick taps — establishes your daily baseline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.35f),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Too early!",
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Wait for the green screen...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}
