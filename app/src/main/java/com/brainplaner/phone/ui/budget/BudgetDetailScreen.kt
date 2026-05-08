package com.brainplaner.phone.ui.budget

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brainplaner.phone.LocalStore
import com.brainplaner.phone.ui.components.BrainBudgetGauge
import com.brainplaner.phone.ui.home.ConfirmedRecoveryAction
import com.brainplaner.phone.ui.home.HomeViewModel
import com.brainplaner.phone.ui.reflection.RecoveryAction
import com.brainplaner.phone.ui.reflection.buildRecoveryActions
import com.brainplaner.phone.ui.theme.BrainplanerTheme
import com.brainplaner.phone.ui.theme.BudgetGreen
import com.brainplaner.phone.ui.theme.BudgetRed
import com.brainplaner.phone.ui.theme.BudgetYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDetailScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
) {
    val spacing = BrainplanerTheme.spacing
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val score = state.readinessScore?.toIntOrNull() ?: 0

    var recoveryExpanded by remember { mutableStateOf(false) }
    var confirmedActions by remember { mutableStateOf<List<ConfirmedRecoveryAction>>(emptyList()) }
    var pickerOpen by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.refreshCloudData()
    }
    LaunchedEffect(refreshTick) {
        viewModel.fetchTodaysRecoveryActions { confirmedActions = it }
    }

    val breakdown = state.readinessBreakdown
    val pendingRecovery = LocalStore.getPendingRecovery(context)

    val sleepHoursAdj = breakdown["sleep_hours"] ?: 0f
    val sleepScoreAdj = breakdown["sleep_score"] ?: 0f
    val rhrAdj = breakdown["rhr"] ?: 0f
    val healthAdj = (sleepHoursAdj + sleepScoreAdj + rhrAdj).toInt()

    val loadSessionAdj = breakdown["session_load"] ?: 0f
    val loadDrainAdj = breakdown["drain_score"] ?: 0f
    val loadCooldownAdj = breakdown["cooldown_index"] ?: 0f
    val loadAdj = (loadSessionAdj + loadDrainAdj + loadCooldownAdj).toInt()

    val recoveryAdj = (breakdown["recovery_actions"] ?: 0f).toInt()
    val hasRecoveryBoost = breakdown.containsKey("recovery_actions")

    val healthSubFactors = buildList {
        if (breakdown.containsKey("sleep_hours")) add(SubFactor("Sleep hours", sleepHoursAdj.toInt()))
        if (breakdown.containsKey("sleep_score")) add(SubFactor("Sleep quality", sleepScoreAdj.toInt()))
        if (breakdown.containsKey("rhr")) add(SubFactor("Resting HR", rhrAdj.toInt()))
    }

    val loadSubFactors = buildList {
        if (breakdown.containsKey("session_load")) add(SubFactor("Session load", loadSessionAdj.toInt()))
        if (breakdown.containsKey("drain_score")) add(SubFactor("Drain score", loadDrainAdj.toInt()))
        if (breakdown.containsKey("cooldown_index")) add(SubFactor("Cooldown", loadCooldownAdj.toInt()))
    }

    val healthCategory = Category(
        emoji = "🌙",
        title = "Health",
        valueLabel = signedLabel(healthAdj),
        points = healthAdj,
        description = if (healthSubFactors.isEmpty()) "Complete your morning check-in" else "Sleep + sleep quality + RHR baseline impact",
        subFactors = healthSubFactors,
    )
    val loadCategory = Category(
        emoji = "⚡",
        title = "Load",
        valueLabel = signedLabel(loadAdj),
        points = loadAdj,
        description = state.planningAccuracyLine ?: "Session load + drain score + cooldown behavior impact",
        subFactors = loadSubFactors,
    )
    val recoveryCategory = Category(
        emoji = "💚",
        title = "Recovery",
        valueLabel = if (hasRecoveryBoost) signedLabel(recoveryAdj) else "TIP",
        points = recoveryAdj,
        description = when {
            hasRecoveryBoost -> "Confirmed recovery actions boosting today's budget"
            !state.readinessMessage.isNullOrBlank() -> state.readinessMessage.orEmpty()
            pendingRecovery != null -> "${pendingRecovery.type} available - confirm on home screen"
            else -> "Follow the readiness guidance and add recovery after demanding sessions"
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = spacing.lg, end = spacing.lg, top = 48.dp, bottom = spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("<- Back", color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = "Budget Detail",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(64.dp))
        }

        Spacer(modifier = Modifier.height(spacing.md))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            BrainBudgetGauge(
                score = score,
                modifier = Modifier.size(180.dp),
                scoreTextStyle = MaterialTheme.typography.displayMedium,
                useScoreColorForText = false,
            )
        }

        Spacer(modifier = Modifier.height(spacing.xs))

        Text(
            text = when {
                score >= 80 -> "Fully charged - great day for deep work"
                score >= 60 -> "Good capacity - pace yourself"
                score >= 40 -> "Moderate - lighter tasks recommended"
                score >= 20 -> "Low energy - protect what's left"
                else -> "Depleted - rest is the priority"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(spacing.xl))

        Text(
            text = "TODAY'S BREAKDOWN",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(spacing.sm))

        Card(
            shape = RoundedCornerShape(BrainplanerTheme.radius.lg),
            colors = CardDefaults.cardColors(containerColor = BrainplanerTheme.surfaceRoles.surface2),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                CategoryRow(category = healthCategory)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                )
                CategoryRow(category = loadCategory)
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                )
                CategoryRow(
                    category = recoveryCategory,
                    expandable = true,
                    expanded = recoveryExpanded,
                    onClick = { recoveryExpanded = !recoveryExpanded },
                )
                if (recoveryExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    RecoveryEditList(
                        actions = confirmedActions,
                        onRemove = { id ->
                            viewModel.deleteRecoveryAction(id) { ok ->
                                if (ok) refreshTick++
                            }
                        },
                        onAdd = { pickerOpen = true },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.md))

        Card(
            shape = RoundedCornerShape(BrainplanerTheme.radius.lg),
            colors = CardDefaults.cardColors(containerColor = BrainplanerTheme.surfaceRoles.surface2),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "How this works",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You start each day at 100. Sleep, session load, and recovery actions adjust the score. It guides how much deep work to plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xxl))
    }

    if (pickerOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // Scale boosts to today's last drain when known; otherwise medium tier.
        val drainForPicker = state.lastDrainScore ?: 3
        ModalBottomSheet(
            onDismissRequest = { pickerOpen = false },
            sheetState = sheetState,
            containerColor = BrainplanerTheme.surfaceRoles.surface2,
        ) {
            RecoveryAddPicker(
                options = buildRecoveryActions(drainForPicker),
                onPick = { picked ->
                    pickerOpen = false
                    viewModel.confirmRecoveryAction(
                        type = picked.title,
                        emoji = picked.emoji,
                        boostPoints = picked.boostPoints,
                        selectedAtMs = System.currentTimeMillis(),
                    ) { ok ->
                        if (ok) refreshTick++
                    }
                },
                onDismiss = { pickerOpen = false },
            )
        }
    }
}

@Composable
private fun RecoveryEditList(
    actions: List<ConfirmedRecoveryAction>,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (actions.isEmpty()) {
            Text(
                text = "No recovery actions logged today.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 40.dp, bottom = 4.dp),
            )
        } else {
            actions.forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(action.emoji ?: "💚", fontSize = 18.sp)
                    Text(
                        text = prettyType(action.type),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "+${action.boostPoints}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = BudgetGreen,
                    )
                    IconButton(
                        onClick = { onRemove(action.id) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp, top = 6.dp)
                .clickable(onClick = onAdd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Add recovery action",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RecoveryAddPicker(
    options: List<RecoveryAction>,
    onPick: (RecoveryAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = BrainplanerTheme.spacing
    Column(modifier = Modifier.padding(spacing.lg)) {
        Text(
            text = "Add recovery action",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        options.forEach { opt ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(opt) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(opt.emoji, fontSize = 24.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(opt.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        opt.duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "+${opt.boostPoints}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = BudgetGreen,
                )
            }
        }
        Spacer(modifier = Modifier.height(spacing.xs))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

private fun prettyType(type: String): String =
    type.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun CategoryRow(
    category: Category,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(category.emoji, fontSize = 28.sp)

        Column(modifier = Modifier.weight(1f)) {
            Text(category.title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(2.dp))

            if (category.subFactors.isNotEmpty()) {
                category.subFactors.forEach { subFactor ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = subFactor.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = signedLabel(subFactor.points),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = pointsColor(subFactor.points),
                        )
                    }
                }
            } else {
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = category.valueLabel,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = pointsColor(category.points),
        )
        if (expandable) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private data class SubFactor(
    val label: String,
    val points: Int,
)

private data class Category(
    val emoji: String,
    val title: String,
    val valueLabel: String,
    val points: Int,
    val description: String,
    val subFactors: List<SubFactor> = emptyList(),
)

@Composable
private fun pointsColor(points: Int): Color {
    return when {
        points > 0 -> BudgetGreen
        points < 0 -> BudgetRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun signedLabel(value: Int): String {
    return if (value >= 0) "+$value" else "$value"
}
