package com.brainplaner.phone.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brainplaner.phone.ui.theme.BrainplanerTheme
import com.brainplaner.phone.ui.theme.BudgetGreen
import com.brainplaner.phone.ui.theme.BudgetRed
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel,
    onBack: () -> Unit,
) {
    val spacing = BrainplanerTheme.spacing
    val state by viewModel.state.collectAsState()

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
                text = "Progress",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(64.dp))
        }

        Spacer(modifier = Modifier.height(spacing.md))

        when {
            state.isLoading -> LoadingView()
            state.errorReason != null && state.streak == null -> ErrorView(state.errorReason!!)
            else -> {
                if (state.capacity != null) {
                    BrainBudgetChartCard(
                        capacity = state.capacity!!,
                        streak = state.streak,
                        productivity = state.productivity,
                    )
                    Spacer(modifier = Modifier.height(spacing.md))
                }

                ProductivityCard(state.productivity)
                Spacer(modifier = Modifier.height(spacing.md))

                EnduranceCard(state.endurance)
            }
        }

        Spacer(modifier = Modifier.height(spacing.xxl))
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(reason: String) {
    Card(
        shape = RoundedCornerShape(BrainplanerTheme.radius.lg),
        colors = CardDefaults.cardColors(containerColor = BrainplanerTheme.surfaceRoles.surface2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Could not load progress: $reason",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private enum class ChartPeriod { WEEKLY, MONTHLY }

private data class ChartPoint(val label: String, val value: Float?)

@Composable
private fun BrainBudgetChartCard(
    capacity: CapacitySummary,
    streak: StreakSummary?,
    productivity: ProductivitySummary?,
) {
    var period by remember { mutableStateOf(ChartPeriod.WEEKLY) }

    val rawPoints = when (period) {
        ChartPeriod.WEEKLY -> capacity.weekly.map {
            ChartPoint(label = shortDateLabel(it.weekStart, monthOnly = false), value = it.avgBrainbudget)
        }
        ChartPeriod.MONTHLY -> capacity.monthly.map {
            ChartPoint(label = shortDateLabel(it.monthStart, monthOnly = true), value = it.avgBrainbudget)
        }
    }
    // Trim leading nulls so the X-axis starts at first real data point.
    val points = rawPoints.dropWhile { it.value == null }
    val plotPoints = points.count { it.value != null }

    Card(
        shape = RoundedCornerShape(BrainplanerTheme.radius.lg),
        colors = CardDefaults.cardColors(containerColor = BrainplanerTheme.surfaceRoles.surface2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "BRAIN BUDGET",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                PeriodToggle(selected = period, onChange = { period = it })
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = "🧠", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = capacity.recent30dAvg?.let { "${it.toInt()} / 100" } ?: "—",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "avg brain budget · 30d",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (plotPoints < 2) {
                Text(
                    text = "Need a couple more morning check-ins to plot a trend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TrendLineChart(
                    points = points,
                    yMax = 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                ChartAxisLabels(points)
            }

            if (streak != null && streak.currentRun > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "🔥 ${streak.currentRun}-day streak",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Calibration verdict — surfaced here only when both capacity and
            // delivery deltas are known. The unique actionable line that used
            // to live in CalibrationCard.
            val verdict = calibrationVerdict(capacity.deltaPct, productivity?.deltaPct)
            if (verdict != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = verdict,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PeriodToggle(
    selected: ChartPeriod,
    onChange: (ChartPeriod) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(2.dp),
    ) {
        PeriodPill(text = "Weekly", selected = selected == ChartPeriod.WEEKLY) {
            onChange(ChartPeriod.WEEKLY)
        }
        PeriodPill(text = "Monthly", selected = selected == ChartPeriod.MONTHLY) {
            onChange(ChartPeriod.MONTHLY)
        }
    }
}

@Composable
private fun PeriodPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}

@Composable
private fun TrendLineChart(
    points: List<ChartPoint>,
    yMax: Float,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val fillColor = primaryColor.copy(alpha = 0.12f)
    val safeYMax = yMax.coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        val padX = 4.dp.toPx()
        val padY = 8.dp.toPx()
        val plotW = size.width - padX * 2
        val plotH = size.height - padY * 2

        // Y axis: 0..yMax. Brain budget passes 100; productivity passes a
        // data-driven ceiling. Values are coerced into the axis so an outlier
        // doesn't push the line off-canvas.
        val yFor = { v: Float -> padY + plotH * (1f - (v / safeYMax).coerceIn(0f, 1f)) }

        // Mid-line reference grid (visual guide, unlabeled).
        drawLine(
            color = gridColor,
            start = Offset(padX, yFor(safeYMax / 2f)),
            end = Offset(size.width - padX, yFor(safeYMax / 2f)),
            strokeWidth = 1.dp.toPx(),
        )

        if (points.size < 2) return@Canvas
        val stepX = plotW / (points.size - 1)
        fun xFor(i: Int) = padX + i * stepX

        // Walk points; null values break the line into separate segments
        // (gap months render as gaps, not interpolated).
        val linePath = Path()
        val fillPath = Path()
        var inSegment = false
        var segmentStartX = 0f
        var segmentLastX = 0f

        fun closeFillSegment() {
            if (!inSegment) return
            fillPath.lineTo(segmentLastX, yFor(0f))
            fillPath.lineTo(segmentStartX, yFor(0f))
            fillPath.close()
            inSegment = false
        }

        points.forEachIndexed { i, p ->
            val v = p.value
            if (v == null) {
                closeFillSegment()
                return@forEachIndexed
            }
            val x = xFor(i)
            val y = yFor(v)
            if (!inSegment) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, yFor(0f))
                fillPath.lineTo(x, y)
                inSegment = true
                segmentStartX = x
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            segmentLastX = x
        }
        closeFillSegment()

        drawPath(path = fillPath, color = fillColor)
        drawPath(
            path = linePath,
            color = primaryColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )

        // Dots at each known value; emphasize the most recent one.
        points.forEachIndexed { i, p ->
            val v = p.value ?: return@forEachIndexed
            val isLast = i == points.lastIndex
            drawCircle(
                color = primaryColor,
                radius = if (isLast) 5.dp.toPx() else 3.dp.toPx(),
                center = Offset(xFor(i), yFor(v)),
            )
        }
    }
}

@Composable
private fun ChartAxisLabels(points: List<ChartPoint>) {
    val first = points.firstOrNull()?.label.orEmpty()
    val last = points.lastOrNull()?.label.orEmpty()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = first,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = last,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val MONTH_DAY_FMT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val MONTH_FMT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

private fun shortDateLabel(iso: String, monthOnly: Boolean): String {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return ""
    return d.format(if (monthOnly) MONTH_FMT else MONTH_DAY_FMT)
}

private fun calibrationVerdict(capacityDelta: Float?, deliveryDelta: Float?): String? {
    if (capacityDelta == null || deliveryDelta == null) return null
    val capUp = capacityDelta >= 0f
    val delUp = deliveryDelta >= 0f
    val gap = capacityDelta - deliveryDelta
    return when {
        capUp && delUp && kotlin.math.abs(gap) < 10f ->
            "Capacity and delivery both holding or rising — well calibrated."
        !capUp && !delUp ->
            "Both trending down — consider a lighter week."
        capUp && !delUp ->
            "Capacity rising but delivery hasn't followed — try sessions matched to prescribed minutes."
        !capUp && delUp ->
            "Delivering above what capacity predicted — capacity may be under-recorded."
        else -> null
    }
}

@Composable
private fun ProductivityCard(productivity: ProductivitySummary?) {
    var period by remember { mutableStateOf(ChartPeriod.WEEKLY) }

    Card(
        shape = RoundedCornerShape(BrainplanerTheme.radius.lg),
        colors = CardDefaults.cardColors(containerColor = BrainplanerTheme.surfaceRoles.surface2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PRODUCTIVITY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (productivity != null && productivity.weekly.isNotEmpty()) {
                    PeriodToggle(selected = period, onChange = { period = it })
                }
            }

            // Names the relationship to the brain-budget card above: capacity
            // is what you have to spend; this is what you actually delivered.
            Text(
                text = "What you delivered with the brain budget you had.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (productivity == null || productivity.weekly.isEmpty()) {
                Text(
                    text = "Complete a few sessions to start tracking your productivity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val headlineMinutes = when (period) {
                ChartPeriod.WEEKLY -> productivity.currentWeekMinutes
                ChartPeriod.MONTHLY -> productivity.currentMonthMinutes
            }
            val headlineSubtitle = when (period) {
                ChartPeriod.WEEKLY -> "effective focused minutes this week"
                ChartPeriod.MONTHLY -> "effective focused minutes this month"
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = "📈", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${headlineMinutes.toInt()} min",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = headlineSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            val rawPoints = when (period) {
                ChartPeriod.WEEKLY -> productivity.weekly.map {
                    ChartPoint(
                        label = shortDateLabel(it.weekStart, monthOnly = false),
                        value = it.effectiveFocusedMinutes,
                    )
                }
                ChartPeriod.MONTHLY -> productivity.monthly.map {
                    ChartPoint(
                        label = shortDateLabel(it.monthStart, monthOnly = true),
                        value = it.effectiveFocusedMinutes,
                    )
                }
            }
            // Trim leading zero buckets so the chart starts at first activity.
            val points = rawPoints.dropWhile { (it.value ?: 0f) == 0f }

            if (points.size >= 2) {
                val maxObserved = points.maxOfOrNull { it.value ?: 0f } ?: 0f
                // Round up to next 30 with a touch of headroom; floor at 60 so a
                // mostly-empty week doesn't render as a flat ceiling.
                val yMax = (kotlin.math.ceil(maxObserved * 1.15f / 30f) * 30f)
                    .coerceAtLeast(60f)
                TrendLineChart(
                    points = points,
                    yMax = yMax,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                ChartAxisLabels(points)
            } else {
                Text(
                    text = "Need a couple more sessions to plot a trend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Delta line — only meaningful in weekly mode (server only computes
            // weekly baseline). In monthly mode we let the chart speak.
            if (period == ChartPeriod.WEEKLY) {
                val delta = productivity.deltaPct
                val baseline = productivity.baselineWeekMinutes.toInt()
                if (delta != null && baseline > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val arrow = if (delta >= 0) "↑" else "↓"
                    val deltaColor = if (delta >= 0) BudgetGreen else BudgetRed
                    Text(
                        text = "$arrow ${"%+.0f".format(delta)}% vs 4 weeks ago " +
                                "($baseline → ${productivity.currentWeekMinutes.toInt()} min)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = deltaColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun EnduranceCard(endurance: EnduranceSummary?) {
    Card(
        shape = RoundedCornerShape(BrainplanerTheme.radius.lg),
        colors = CardDefaults.cardColors(containerColor = BrainplanerTheme.surfaceRoles.surface2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ENDURANCE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))

            val sustainableCurrent = endurance?.sustainableMinutesCurrent
            if (endurance == null || sustainableCurrent == null) {
                Text(
                    text = "Complete 3+ sessions at capacity (≥85% of plan, low drain) " +
                            "to establish your sustainable session length.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val currentMin = sustainableCurrent.toInt()
            val baselineMin = endurance.sustainableMinutesBaseline?.toInt()

            // Headline: sustainable session length
            Row(verticalAlignment = Alignment.Bottom) {
                Text("💪", fontSize = 28.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$currentMin min",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "your sustainable session length right now",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Direction line — the prescription voice
            val directionText: String
            val directionColor: Color
            when (endurance.trend) {
                EnduranceTrend.GROWING -> {
                    directionText = if (baselineMin != null) {
                        "↑ Up from $baselineMin min a month ago — try ${endurance.nextTargetMinutes ?: currentMin} " +
                                "min on your next high-readiness morning."
                    } else {
                        "↑ Growing — try ${endurance.nextTargetMinutes ?: currentMin} min on your next " +
                                "high-readiness morning."
                    }
                    directionColor = BudgetGreen
                }
                EnduranceTrend.STABLE -> {
                    val target = endurance.nextTargetMinutes
                    directionText = if (target != null && target > currentMin) {
                        "Plateaued at $currentMin min — ready to try $target min on your next high-readiness " +
                                "morning."
                    } else {
                        "Hold at $currentMin min this week. Aim for focus score ≥ 4 before progressing."
                    }
                    directionColor = MaterialTheme.colorScheme.primary
                }
                EnduranceTrend.REGRESSING -> {
                    directionText = if (baselineMin != null) {
                        "↓ Down from $baselineMin min a month ago — stay at ${endurance.nextTargetMinutes ?: currentMin} " +
                                "min and rebuild gradually."
                    } else {
                        "↓ Trending down — hold at ${endurance.nextTargetMinutes ?: currentMin} min and rebuild."
                    }
                    directionColor = BudgetRed
                }
                EnduranceTrend.BUILDING -> {
                    directionText = "Baseline still forming — need ${3 - endurance.qualifyingSessionsCurrent} more " +
                            "capacity-matched sessions to prescribe progression."
                    directionColor = MaterialTheme.colorScheme.onSurfaceVariant
                }
            }
            Text(
                text = directionText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = directionColor,
            )

            // One supporting proof point
            endurance.supportingProof?.let { proof ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = proof,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

