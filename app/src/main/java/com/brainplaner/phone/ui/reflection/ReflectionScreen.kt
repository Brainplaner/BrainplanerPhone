package com.brainplaner.phone.ui.reflection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brainplaner.phone.ui.theme.BudgetGreen
import com.brainplaner.phone.ui.theme.BudgetYellow

@Composable
fun ReflectionScreen(
    viewModel: ReflectionViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showRecovery by remember { mutableStateOf(false) }
    var submittedDrainScore by remember { mutableIntStateOf(3) }

    if (showRecovery) {
        RecoverySuggestionsScreen(
            drainScore = submittedDrainScore,
            onDone = onDone,
        )
        return
    }

    var focusScore by remember { mutableIntStateOf(0) }
    var drainScore by remember { mutableIntStateOf(0) }
    var handoffNextAction by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    data class AnchoredScore(val score: Int, val label: String, val anchor: String)

    val focusAnchors = listOf(
        AnchoredScore(1, "Couldn't focus", "Kept picking up phone, couldn't settle"),
        AnchoredScore(2, "Scattered", "Started working but frequently drifted"),
        AnchoredScore(3, "Moderate", "Some distractions but got work done"),
        AnchoredScore(4, "Focused", "Mostly uninterrupted, few breaks"),
        AnchoredScore(5, "Flow state", "Lost track of time, deep in the work"),
    )
    val drainAnchors = listOf(
        AnchoredScore(1, "Energized", "Could do another session right now"),
        AnchoredScore(2, "Mild fatigue", "Fine but wouldn't want a hard task next"),
        AnchoredScore(3, "Tired", "Need a real break before more work"),
        AnchoredScore(4, "Drained", "Brain feels slow, making mistakes"),
        AnchoredScore(5, "Depleted", "Done for the day, can't concentrate"),
    )

    val canSubmit = focusScore > 0 && drainScore > 0 && handoffNextAction.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Session Reflection",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Primary validator: Focus (1–5)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "HOW FOCUSED WERE YOU?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                focusAnchors.forEach { item ->
                    FilterChip(
                        selected = focusScore == item.score,
                        onClick = { focusScore = item.score },
                        label = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("${item.score}. ${item.label}", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    item.anchor,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Secondary validator: Mental drain (1–5)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "HOW MENTALLY DRAINED DO YOU FEEL?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                drainAnchors.forEach { item ->
                    FilterChip(
                        selected = drainScore == item.score,
                        onClick = { drainScore = item.score },
                        label = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("${item.score}. ${item.label}", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    item.anchor,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Next step + note card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "HANDOFF",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp,
                )
                OutlinedTextField(
                    value = handoffNextAction,
                    onValueChange = { handoffNextAction = it.take(200) },
                    label = { Text("First action next time *") },
                    placeholder = { Text("e.g. Open file X and write the intro paragraph") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    supportingText = { Text("${handoffNextAction.length}/200") },
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(280) },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("Anything else worth capturing?") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    supportingText = { Text("${note.length}/280") },
                )
            }
        }

        if (state.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Error: ${state.error}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                viewModel.submit(
                    focusScore = focusScore,
                    drainScore = drainScore,
                    handoffNextAction = handoffNextAction,
                    note = note.takeIf { it.isNotBlank() },
                )
                submittedDrainScore = drainScore
                showRecovery = true
            },
            enabled = canSubmit && !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Submit Reflection", style = MaterialTheme.typography.titleLarge)
            }
        }

        TextButton(onClick = onDone) {
            Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Recovery Suggestions Screen ──

private data class RecoveryAction(
    val emoji: String,
    val title: String,
    val description: String,
    val budgetBoost: String,
    val duration: String,
)

@Composable
private fun RecoverySuggestionsScreen(
    drainScore: Int,
    onDone: () -> Unit,
) {
    val actions = buildRecoveryActions(drainScore)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Recovery Plan",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Suggested actions to recharge your brain budget",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(20.dp))

        actions.forEach { action ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        action.emoji,
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            action.title,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            action.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                action.budgetBoost,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = BudgetGreen,
                            )
                            Text(
                                action.duration,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "💡",
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    when {
                        drainScore >= 4 -> "You're running on empty. Prioritize rest — tomorrow's budget depends on it."
                        drainScore == 3 -> "Good session! A short break will help you bounce back faster."
                        else -> "Great energy! You could start another session or bank recovery for later."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text("Done — Back to Home", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun buildRecoveryActions(drainScore: Int): List<RecoveryAction> {
    val walkBoost = when {
        drainScore >= 4 -> "+12"
        drainScore == 3 -> "+8"
        else -> "+5"
    }
    val eatBoost = when {
        drainScore >= 4 -> "+10"
        drainScore == 3 -> "+7"
        else -> "+4"
    }
    val trainBoost = when {
        drainScore >= 4 -> "+18"
        drainScore == 3 -> "+14"
        else -> "+10"
    }
    val napBoost = when {
        drainScore >= 4 -> "+22"
        drainScore == 3 -> "+15"
        else -> "+8"
    }

    return listOf(
        RecoveryAction(
            emoji = "🚶",
            title = "Walk",
            description = "Fresh air + light movement. Clears mental fog without taxing your body.",
            budgetBoost = "$walkBoost budget",
            duration = "15–20 min",
        ),
        RecoveryAction(
            emoji = "🍽️",
            title = "Eat",
            description = "Balanced meal or snack. Glucose restores decision-making capacity.",
            budgetBoost = "$eatBoost budget",
            duration = "20–30 min",
        ),
        RecoveryAction(
            emoji = "🏋️",
            title = "Train",
            description = "Moderate exercise. BDNF release boosts neuroplasticity and clears cortisol.",
            budgetBoost = "$trainBoost budget",
            duration = "30–45 min",
        ),
        RecoveryAction(
            emoji = "😴",
            title = "Power Nap",
            description = "Short sleep resets working memory. Best ROI for high drain scores.",
            budgetBoost = "$napBoost budget",
            duration = "20–30 min",
        ),
    )
}
