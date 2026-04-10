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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brainplaner.phone.LocalStore
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
        AnchoredScore(5, "Flow state", "Lost track of time, deep in the work"),
        AnchoredScore(4, "Focused", "Mostly uninterrupted, few breaks"),
        AnchoredScore(3, "Moderate", "Some distractions but got work done"),
        AnchoredScore(2, "Scattered", "Started working but frequently drifted"),
        AnchoredScore(1, "Couldn't focus", "Kept picking up phone, couldn't settle"),
    )
    val drainAnchors = listOf(
        AnchoredScore(5, "Energized", "Could do another session right now"),
        AnchoredScore(4, "Mild fatigue", "Fine but wouldn't want a hard task next"),
        AnchoredScore(3, "Tired", "Need a real break before more work"),
        AnchoredScore(2, "Drained", "Brain feels slow, making mistakes"),
        AnchoredScore(1, "Depleted", "Done for the day, can't concentrate"),
        
        
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
    val boostPoints: Int,
    val duration: String,
)

@Composable
private fun RecoverySuggestionsScreen(
    drainScore: Int,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val actions = buildRecoveryActions(drainScore)
    var selectedIndex by remember { mutableIntStateOf(-1) }

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
            "Pick one — confirm when you're back",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(20.dp))

        actions.forEachIndexed { index, action ->
            val isSelected = selectedIndex == index
            Card(
                onClick = { selectedIndex = if (isSelected) -1 else index },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected)
                        BudgetGreen.copy(alpha = 0.15f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                ),
                border = if (isSelected)
                    androidx.compose.foundation.BorderStroke(2.dp, BudgetGreen)
                else null,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        if (isSelected) "✅" else action.emoji,
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
            onClick = {
                if (selectedIndex >= 0) {
                    val picked = actions[selectedIndex]
                    LocalStore.savePendingRecovery(
                        context,
                        type = picked.title,
                        emoji = picked.emoji,
                        boostPoints = picked.boostPoints,
                    )
                }
                onDone()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedIndex >= 0) BudgetGreen else MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                if (selectedIndex >= 0) "Start ${actions[selectedIndex].title} — Back to Home"
                else "Skip — Back to Home",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun buildRecoveryActions(drainScore: Int): List<RecoveryAction> {
    val walkBoost = when {
        drainScore >= 4 -> 12
        drainScore == 3 -> 8
        else -> 5
    }
    val eatBoost = when {
        drainScore >= 4 -> 10
        drainScore == 3 -> 7
        else -> 4
    }
    val trainBoost = when {
        drainScore >= 4 -> 18
        drainScore == 3 -> 14
        else -> 10
    }
    val napBoost = when {
        drainScore >= 4 -> 22
        drainScore == 3 -> 15
        else -> 8
    }

    return listOf(
        RecoveryAction(
            emoji = "🚶",
            title = "Walk",
            description = "Fresh air + light movement. Clears mental fog without taxing your body.",
            budgetBoost = "+$walkBoost budget",
            boostPoints = walkBoost,
            duration = "15–20 min",
        ),
        RecoveryAction(
            emoji = "🍽️",
            title = "Eat",
            description = "Balanced meal or snack. Glucose restores decision-making capacity.",
            budgetBoost = "+$eatBoost budget",
            boostPoints = eatBoost,
            duration = "20–30 min",
        ),
        RecoveryAction(
            emoji = "🏋️",
            title = "Train",
            description = "Moderate exercise. BDNF release boosts neuroplasticity and clears cortisol.",
            budgetBoost = "+$trainBoost budget",
            boostPoints = trainBoost,
            duration = "30–45 min",
        ),
        RecoveryAction(
            emoji = "😴",
            title = "Power Nap",
            description = "Short sleep resets working memory. Best ROI for high drain scores.",
            budgetBoost = "+$napBoost budget",
            boostPoints = napBoost,
            duration = "20–30 min",
        ),
    )
}
