package com.brainplaner.phone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brainplaner.phone.LocalStore
import com.brainplaner.phone.ui.components.BrainCard
import com.brainplaner.phone.ui.components.BrainChoiceChip
import com.brainplaner.phone.ui.components.BrainDangerButton
import com.brainplaner.phone.ui.components.BrainPrimaryButton
import com.brainplaner.phone.ui.theme.BrainplanerPhoneTheme
import com.brainplaner.phone.ui.theme.BrainplanerTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onResetCheckIn: () -> Unit,
    onRunWarmup: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val spacing = BrainplanerTheme.spacing
    var goalTier by remember { mutableStateOf(LocalStore.getGoalTier(context)) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset today's check-in?") },
            text = { Text("This clears today's check-in so you can re-enter sleep data.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    LocalStore.clearCheckIn(context)
                    onResetCheckIn()
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log out?") },
            text = { Text("You'll need to sign in again to continue.") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onLogout()
                }) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = spacing.lg, end = spacing.lg, bottom = spacing.lg, top = spacing.xxl),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.height(spacing.xl))

        // ── Focus goal ──
        Text(
            "FOCUS GOAL",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp,
        )
        Spacer(modifier = Modifier.height(spacing.xs))

        BrainCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(spacing.md)) {
                Text("Where are you right now?", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Each tier builds on the previous. Brainplaner surfaces the insights that match your current level — you can level up later as your data matures.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    BrainChoiceChip(
                        selected = goalTier == LocalStore.GOAL_TIER_EFFICIENCY,
                        onClick = {
                            goalTier = LocalStore.GOAL_TIER_EFFICIENCY
                            LocalStore.setGoalTier(context, goalTier)
                        },
                        label = {
                            Text(
                                "1 · Efficiency",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                    BrainChoiceChip(
                        selected = goalTier == LocalStore.GOAL_TIER_CAPACITY,
                        onClick = {
                            goalTier = LocalStore.GOAL_TIER_CAPACITY
                            LocalStore.setGoalTier(context, goalTier)
                        },
                        label = {
                            Text(
                                "2 · Capacity",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                    BrainChoiceChip(
                        selected = goalTier == LocalStore.GOAL_TIER_GROWTH,
                        onClick = {
                            goalTier = LocalStore.GOAL_TIER_GROWTH
                            LocalStore.setGoalTier(context, goalTier)
                        },
                        label = {
                            Text(
                                "3 · Growth",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
                Spacer(modifier = Modifier.height(spacing.sm))
                val (tierTitle, tierBody) = when (goalTier) {
                    LocalStore.GOAL_TIER_CAPACITY -> "Same time, more done" to
                        "Builds on Efficiency. Adds Brain Budget so recovery expands what you can finish in the same hours."
                    LocalStore.GOAL_TIER_GROWTH -> "Less time, more done" to
                        "Builds on Capacity. Tracks endurance over weeks so heavier sessions stay sustainable as you load up."
                    else -> "Same done, less time" to
                        "Cuts phone leakage during/after sessions, sharpens reflection habit, closes the planned-vs-executed gap."
                }
                Text(tierTitle, style = MaterialTheme.typography.bodyMedium)
                Text(
                    tierBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xl))

        // ── Features ──
        Text(
            "FEATURES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp,
        )
        Spacer(modifier = Modifier.height(spacing.xs))

        BrainCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(spacing.md)) {
                Text("Cognitive Warm-up", style = MaterialTheme.typography.titleSmall)
                Text(
                    "5-tap reaction time test. Run it whenever you want to log a cognitive baseline for today.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                BrainPrimaryButton(
                    text = "Run Reaction Test",
                    onClick = onRunWarmup,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xl))

        // ── Demo / Debug ──
        Text(
            "DEMO",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp,
        )
        Spacer(modifier = Modifier.height(spacing.xs))

        BrainCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(spacing.md)) {
                Text(
                    "Reset daily check-in",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Clears today's check-in so you can re-enter sleep data. Useful for demos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                BrainPrimaryButton(
                    text = "↩ Reset Check-in",
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xl))

        // ── Account ──
        Text(
            "ACCOUNT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp,
        )
        Spacer(modifier = Modifier.height(spacing.xs))

        BrainCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(spacing.md)) {
                BrainDangerButton(
                    text = "Logout",
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.xxl))
    }
}

@Preview(name = "Settings Light", showBackground = true)
@Composable
private fun SettingsPreviewLight() {
    BrainplanerPhoneTheme(darkTheme = false) {
        SettingsScreen(onResetCheckIn = {}, onRunWarmup = {}, onLogout = {})
    }
}

@Preview(name = "Settings Dark", showBackground = true)
@Composable
private fun SettingsPreviewDark() {
    BrainplanerPhoneTheme(darkTheme = true) {
        SettingsScreen(onResetCheckIn = {}, onRunWarmup = {}, onLogout = {})
    }
}

@Preview(name = "Settings Font 1.3x", showBackground = true, fontScale = 1.3f)
@Composable
private fun SettingsPreviewFontScale() {
    BrainplanerPhoneTheme(darkTheme = false) {
        SettingsScreen(onResetCheckIn = {}, onRunWarmup = {}, onLogout = {})
    }
}
