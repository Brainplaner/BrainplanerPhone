package com.brainplaner.phone.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.brainplaner.phone.ui.theme.BudgetGreen
import com.brainplaner.phone.ui.theme.BudgetRed
import com.brainplaner.phone.ui.theme.BudgetYellow

/**
 * Circular "Brain Budget" gauge — 270° arc with animated fill. Shared between the
 * Home card and Budget Detail screen.
 *
 * @param colorScore drives the green/yellow/red status fill.
 * @param scoreTextStyle lets callers choose between displayLarge / displayMedium.
 * @param useScoreColorForText when true the numeric score adopts the status color
 *   (Home card behavior); when false it uses the default text color.
 */
@Composable
fun BrainBudgetGauge(
    score: Int,
    modifier: Modifier = Modifier,
    scoreTextStyle: TextStyle = MaterialTheme.typography.displayLarge,
    useScoreColorForText: Boolean = true,
) {
    val fraction = (score / 100f).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 800),
        label = "brain_budget_gauge",
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
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
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
                text = "$score",
                style = scoreTextStyle.copy(
                    color = if (useScoreColorForText) gaugeColor else Color.Unspecified,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "/ 100",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
