package com.brainplaner.phone.ui.reflection

data class RecoveryAction(
    val emoji: String,
    val title: String,
    val description: String,
    val budgetBoost: String,
    val boostPoints: Int,
    val duration: String,
)

// Boost values are calibrated to the readiness formula (0-100 scale).
// Heavy session load costs -12 to -18 pts; drain 4-5 costs -4 to -8 pts.
// Recovery should offset 20-65% of combined depletion, not fully cancel it.
fun buildRecoveryActions(drainScore: Int): List<RecoveryAction> {
    val walkBoost = when {
        drainScore >= 4 -> 5
        drainScore == 3 -> 3
        else -> 2
    }
    val eatBoost = when {
        drainScore >= 4 -> 4
        drainScore == 3 -> 3
        else -> 2
    }
    val trainBoost = when {
        drainScore >= 4 -> 10
        drainScore == 3 -> 7
        else -> 4
    }
    val napBoost = when {
        drainScore >= 4 -> 12
        drainScore == 3 -> 8
        else -> 5
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
