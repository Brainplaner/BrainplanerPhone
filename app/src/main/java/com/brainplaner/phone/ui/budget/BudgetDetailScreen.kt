package com.brainplaner.phone.ui.budget

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brainplaner.phone.LocalStore
import com.brainplaner.phone.ui.home.HomeViewModel
import com.brainplaner.phone.ui.theme.BudgetGreen
import com.brainplaner.phone.ui.theme.BudgetRed
import com.brainplaner.phone.ui.theme.BudgetYellow

@Composable
fun BudgetDetailScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val score = state.readinessScore?.toIntOrNull() ?: 0

    val checkInData = LocalStore.getCheckInData(context)
    val sleepHours = checkInData?.first
    val sleepScore = checkInData?.second
    val pendingRecovery = LocalStore.getPendingRecovery(context)

    // ── 3 simple categories ──

    // HEALTH: summarize sleep into one line
    val healthAdj: Int
    val healthDesc: String
    if (sleepHours != null && sleepScore != null) {
        val sleepHoursAdj = when {
            sleepHours < 8f -> maxOf(-25f, (sleepHours - 8f) * 6.25f)
            sleepHours > 10f -> -5f
            else -> 0f
        }
        val sleepScoreAdj = ((sleepScore - 70) * 0.15f).coerceIn(-15f, 15f)
        healthAdj = (sleepHoursAdj + sleepScoreAdj).toInt()
        healthDesc = "%.1fh sleep, quality %d/100".format(sleepHours, sleepScore)
    } else {
        healthAdj = 0
        healthDesc = "Complete your morning check-in"
    }

    // LOAD: from cloud breakdown or pending
    val loadAdj: Int
    val loadDesc: String
    val cloudLoad = state.readinessBreakdown["session_load"]
    if (cloudLoad != null) {
        loadAdj = cloudLoad.toInt()
        loadDesc = state.planningAccuracyLine ?: "Yesterday's session impact"
    } else {
        loadAdj = 0
        loadDesc = "No session data yet"
    }

    // RECOVERY: pending recovery action
    val recoveryDesc: String
    val recoveryLabel: String
    if (pendingRecovery != null) {
        recoveryLabel = "${pendingRecovery.emoji} +${pendingRecovery.boostPoints}"
        recoveryDesc = "${pendingRecovery.type} — confirm on home screen"
    } else {
        recoveryLabel = "—"
        recoveryDesc = "Pick a recovery action after your next session"
    }

    data class Category(
        val emoji: String,
        val title: String,
        val valueLabel: String,
        val points: Int,
        val description: String,
    )

    val categories = listOf(
        Category("🌙", "Health", if (healthAdj >= 0) "+$healthAdj" else "$healthAdj", healthAdj, healthDesc),
        Category("⚡", "Load", if (loadAdj >= 0) "+$loadAdj" else "$loadAdj", loadAdj, loadDesc),
        Category("💚", "Recovery", recoveryLabel, 0, recoveryDesc),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 48.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("← Back", color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Budget Detail",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(64.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            BudgetGauge(score = score, modifier = Modifier.size(180.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            when {
                score >= 80 -> "Fully charged — great day for deep work"
                score >= 60 -> "Good capacity — pace yourself"
                score >= 40 -> "Moderate — lighter tasks recommended"
                score >= 20 -> "Low energy — protect what's left"
                else -> "Depleted — rest is the priority"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "TODAY'S BREAKDOWN",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                categories.forEachIndexed { index, cat ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(cat.emoji, fontSize = 28.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cat.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                cat.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val color = when {
                            cat.points > 0 -> BudgetGreen
                            cat.points < 0 -> BudgetRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            cat.valueLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = color,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "💡 How Brain Budget Works",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "You start each day at 100. Sleep, session load, " +
                            "and recovery actions adjust the score. " +
                            "It guides how much deep work to plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun BudgetGauge(score: Int, modifier: Modifier = Modifier) {
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
    val trackColor = Color.Gray.copy(alpha = 0.2f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            drawArc(
                color = trackColor, startAngle = 135f, sweepAngle = 270f,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = gaugeColor, startAngle = 135f,
                sweepAngle = 270f * animatedFraction,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                "/ 100",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
