package com.brainplaner.phone.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brainplaner.phone.ui.theme.BrainplanerTheme
import com.brainplaner.phone.ui.theme.BudgetGreen
import com.brainplaner.phone.ui.theme.BudgetRed
import com.brainplaner.phone.ui.theme.BudgetYellow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel,
) {
    val spacing = BrainplanerTheme.spacing
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = spacing.lg, end = spacing.lg, top = 48.dp, bottom = spacing.lg),
    ) {
        Text(
            text = "Progress",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(spacing.md))

        when {
            state.isLoading -> LoadingView()
            state.errorReason != null && state.streak == null -> ErrorView(state.errorReason!!)
            else -> {
                if (state.capacity != null) {
                    BrainBudgetChartCard(
                        capacity = state.capacity!!,
                        productivity = state.productivity,
                    )
                    Spacer(modifier = Modifier.height(spacing.md))
                }

                if (state.streak != null) {
                    CapacityMatchingCard(streak = state.streak!!)
                    Spacer(modifier = Modifier.height(spacing.md))
                }

                ProductivityCard(state.productivity)
                Spacer(modifier = Modifier.height(spacing.md))

                ConsolidationCard(state.enduranceSnapshot)
                Spacer(modifier = Modifier.height(spacing.md))

                EnduranceCompactSummary(
                    snapshot = state.enduranceSnapshot,
                    legacy = state.endurance,
                )
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

// Horizontal background band for TrendLineChart. Used by the brain-budget chart
// to paint Yerkes-Dodson zones behind the line so the chart reads as
// state-location, not slope.
private data class ZoneBand(val from: Float, val to: Float, val color: Color)

@Composable
private fun BrainBudgetChartCard(
    capacity: CapacitySummary,
    productivity: ProductivitySummary?,
) {
    Card(
        shape = RoundedCornerShape(BrainplanerTheme.radius.lg),
        colors = CardDefaults.cardColors(containerColor = BrainplanerTheme.surfaceRoles.surface2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "BRAIN BUDGET",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Zone-first headline. Brain budget is a state readout ("where am
            // I living?"), not an improvement metric — so the Yerkes-Dodson
            // zone (§6) is the headline and the 30d avg sits as a small
            // supporting number.
            val avg = capacity.recent30dAvg
            val zone = avg?.toInt()?.let { budgetZoneLabel(it) }
            if (zone != null) {
                Text(
                    text = zone.first.replaceFirstChar { it.titlecase(Locale.ENGLISH) },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = zone.second,
                )
            } else {
                Text(
                    text = "Brain budget",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🧠", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = avg?.let { "30d avg · ${it.toInt()}" } ?: "30d avg · —",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Zone meter: a 0-100 horizontal bar with Yerkes-Dodson zones
            // colored in, and a marker at the 30d avg. One look tells you
            // where you live. No fake trajectory, no compressed axis games —
            // the question this card answers is *location*, not *slope*.
            if (avg != null) {
                ZoneMeter(
                    value = avg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                )
            } else {
                Text(
                    text = "Need a couple more morning check-ins to plot your zone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

// SCIENTIFIC_FOUNDATIONS §8: capacity-matched load is the behavior we want
// to reward, not raw presence. Lives in its own card so the brain-budget
// state readout above can speak unambiguously — a green "Optimal zone"
// headline and a red 3-of-28 streak in the same card was the contradiction
// that made the old Progress screen unreadable.
@Composable
private fun CapacityMatchingCard(streak: StreakSummary) {
    val capacityMatched = streak.greenCount
    val activeDays = streak.greenCount + streak.yellowCount + streak.redCount
    if (activeDays == 0) return

    val ratio = capacityMatched.toFloat() / activeDays
    val color = when {
        ratio >= 0.6f -> BudgetGreen
        ratio >= 0.3f -> MaterialTheme.colorScheme.primary
        else -> BudgetRed
    }

    Card(
        shape = RoundedCornerShape(BrainplanerTheme.radius.lg),
        colors = CardDefaults.cardColors(containerColor = BrainplanerTheme.surfaceRoles.surface2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "CAPACITY MATCHING",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "✓ $capacityMatched of $activeDays days at capacity",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
            Text(
                text = "Days where sessions stayed within prescribed load.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// 0-100 horizontal zone meter with a triangular marker at `value`. Replaces
// the line-chart-in-a-green-box pattern, which read as "trajectory" even
// when the data sat entirely inside one zone. Thresholds match
// budgetZoneLabel / BrainBudgetGauge so the headline, the meter, and the
// live home-screen gauge all speak the same vocabulary.
@Composable
private fun ZoneMeter(
    value: Float,
    modifier: Modifier = Modifier,
) {
    val redColor = BudgetRed
    val yellowColor = BudgetYellow
    val greenColor = BudgetGreen
    val markerColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)

    Canvas(modifier = modifier) {
        val padX = 2.dp.toPx()
        val labelH = labelStyle.fontSize.toPx() + 4.dp.toPx()
        val barH = 10.dp.toPx()
        val markerH = 8.dp.toPx()
        val markerGap = 3.dp.toPx()
        // Layout: [marker] [bar] [tick labels]
        val barTop = markerH + markerGap
        val barBottom = barTop + barH
        val barLeft = padX
        val barRight = size.width - padX
        val barWidth = barRight - barLeft
        val xFor = { v: Float -> barLeft + barWidth * (v.coerceIn(0f, 100f) / 100f) }

        // Draw zone segments. Sharp boundaries — no rounding between zones,
        // because the boundary itself carries meaning.
        val redEnd = xFor(40f)
        val yellowEnd = xFor(70f)
        val barAlpha = 0.85f
        drawRect(
            color = redColor.copy(alpha = barAlpha),
            topLeft = Offset(barLeft, barTop),
            size = Size(redEnd - barLeft, barH),
        )
        drawRect(
            color = yellowColor.copy(alpha = barAlpha),
            topLeft = Offset(redEnd, barTop),
            size = Size(yellowEnd - redEnd, barH),
        )
        drawRect(
            color = greenColor.copy(alpha = barAlpha),
            topLeft = Offset(yellowEnd, barTop),
            size = Size(barRight - yellowEnd, barH),
        )

        // Marker — a downward-pointing triangle that sits just above the bar
        // and points at the value's position. Drawn last so it stays above
        // the colored segments.
        val markerX = xFor(value)
        val markerW = 10.dp.toPx()
        val tri = Path().apply {
            moveTo(markerX, barTop - 1.dp.toPx())
            lineTo(markerX - markerW / 2f, barTop - markerH)
            lineTo(markerX + markerW / 2f, barTop - markerH)
            close()
        }
        drawPath(tri, markerColor)

        // Tick labels under the bar at the boundary positions (0, 40, 70, 100).
        // These anchor the meter without the user having to know what the
        // colors mean numerically.
        val ticks = listOf(0, 40, 70, 100)
        ticks.forEach { t ->
            val layout = textMeasurer.measure(text = t.toString(), style = labelStyle)
            val tx = (xFor(t.toFloat()) - layout.size.width / 2f)
                .coerceIn(barLeft, barRight - layout.size.width)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(tx, barBottom + 2.dp.toPx()),
            )
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
    // Lower bound of the Y axis. Default 0 keeps under-line area fills anchored
    // at the chart floor (productivity-style). Brain budget passes a tightened
    // floor (e.g. 80) so a high, low-variance series doesn't render as a flat
    // line stuck to the top of an 0–100 axis.
    yMin: Float = 0f,
    valueFormat: (Float) -> String = { "${it.toInt()}" },
    // When provided, draws a second (upper) line and shades the gap between it
    // and `points`. Used on the productivity card to show clocked vs effective
    // minutes — the gap *is* the story (lost focus). Must be the same length
    // as `points`; null values are tolerated per-index in either series.
    secondaryPoints: List<ChartPoint>? = null,
    dualTooltipFormat: ((primary: Float, secondary: Float, label: String) -> String)? = null,
    // Horizontal background bands painted behind the line. Used by brain budget
    // to show Yerkes-Dodson zones, so the chart reads as state-location rather
    // than slope. When non-null, the single-series under-line area fill is
    // suppressed — the bands carry the meaning, and a fill on top of them
    // would muddy the zone color the eye is supposed to read.
    zoneBands: List<ZoneBand>? = null,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val singleFillColor = primaryColor.copy(alpha = 0.12f)
    val gapFillColor = primaryColor.copy(alpha = 0.10f)
    val secondaryLineColor = primaryColor.copy(alpha = 0.45f)
    val tooltipBg = MaterialTheme.colorScheme.surface
    val tooltipFg = MaterialTheme.colorScheme.onSurface
    val tooltipBorder = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    // Defensive: clamp yMax above yMin and ensure span > 0 so yFor() never divides by zero.
    val safeYMin = yMin
    val safeYMax = yMax.coerceAtLeast(safeYMin + 1f)
    val ySpan = safeYMax - safeYMin
    val hasSecondary = secondaryPoints != null && secondaryPoints.size == points.size

    val textMeasurer = rememberTextMeasurer()
    val tooltipStyle = MaterialTheme.typography.labelSmall.copy(
        color = tooltipFg,
        fontWeight = FontWeight.SemiBold,
    )

    // Reset selection when the underlying data shape changes (e.g. period toggle).
    var selectedIndex by remember(points.size) { mutableStateOf<Int?>(null) }
    val safeSelected = selectedIndex?.takeIf { it in points.indices && points[it].value != null }

    Canvas(
        modifier = modifier.pointerInput(points) {
            detectTapGestures { offset ->
                if (points.size < 2) return@detectTapGestures
                val padX = 4.dp.toPx()
                val plotW = size.width - padX * 2
                val stepX = plotW / (points.size - 1)
                var bestIdx: Int? = null
                var bestDist = Float.MAX_VALUE
                points.forEachIndexed { i, p ->
                    if (p.value == null) return@forEachIndexed
                    val px = padX + i * stepX
                    val d = kotlin.math.abs(px - offset.x)
                    if (d < bestDist) {
                        bestDist = d
                        bestIdx = i
                    }
                }
                // Tap anywhere along a column selects that dot; tap far from any
                // dot dismisses the tooltip.
                selectedIndex = if (bestIdx != null && bestDist < 40.dp.toPx()) bestIdx else null
            }
        },
    ) {
        val padX = 4.dp.toPx()
        val padY = 8.dp.toPx()
        val plotW = size.width - padX * 2
        val plotH = size.height - padY * 2

        // Y axis: safeYMin..safeYMax. Brain budget passes a tight, data-aware
        // window; productivity passes 0..ceiling. Values are coerced into the
        // axis so an outlier doesn't push the line off-canvas.
        val yFor = { v: Float -> padY + plotH * (1f - ((v - safeYMin) / ySpan).coerceIn(0f, 1f)) }

        // Zone bands (background). Each band is clipped to the visible y-window
        // and skipped if it falls entirely outside; alpha is low so the line
        // and dots remain the dominant visual element.
        if (zoneBands != null) {
            zoneBands.forEach { band ->
                val lo = kotlin.math.max(band.from, safeYMin)
                val hi = kotlin.math.min(band.to, safeYMax)
                if (hi <= lo) return@forEach
                val topY = yFor(hi)
                val bottomY = yFor(lo)
                drawRect(
                    color = band.color.copy(alpha = 0.14f),
                    topLeft = Offset(padX, topY),
                    size = Size(size.width - padX * 2, bottomY - topY),
                )
            }
        }

        // Mid-line reference grid (visual guide, unlabeled). Skipped when zone
        // bands are present — the band edges already give the eye a reference,
        // and a mid-line on top of a colored band reads as noise.
        if (zoneBands == null) {
            val midY = (safeYMin + safeYMax) / 2f
            drawLine(
                color = gridColor,
                start = Offset(padX, yFor(midY)),
                end = Offset(size.width - padX, yFor(midY)),
                strokeWidth = 1.dp.toPx(),
            )
        }

        if (points.size < 2) return@Canvas
        val stepX = plotW / (points.size - 1)
        fun xFor(i: Int) = padX + i * stepX

        // Build a connected line path for one series, segmenting around nulls
        // (gap months render as gaps, not interpolated).
        fun buildLinePath(series: List<ChartPoint>): Path {
            val p = Path()
            var inSegment = false
            series.forEachIndexed { i, pt ->
                val v = pt.value
                if (v == null) {
                    inSegment = false
                    return@forEachIndexed
                }
                val x = xFor(i)
                val y = yFor(v)
                if (!inSegment) {
                    p.moveTo(x, y)
                    inSegment = true
                } else {
                    p.lineTo(x, y)
                }
            }
            return p
        }

        if (hasSecondary) {
            val sec = secondaryPoints!!
            // Shade the gap between the upper series (sec) and the lower series
            // (points) only across contiguous runs where both have values.
            var runStart = -1
            val runs = mutableListOf<Pair<Int, Int>>()
            points.forEachIndexed { i, p ->
                val both = p.value != null && sec[i].value != null
                if (both) {
                    if (runStart < 0) runStart = i
                } else if (runStart >= 0) {
                    runs.add(runStart to i - 1)
                    runStart = -1
                }
            }
            if (runStart >= 0) runs.add(runStart to points.lastIndex)

            runs.forEach { (from, to) ->
                if (to <= from) return@forEach
                val gp = Path()
                for (i in from..to) {
                    val v = points[i].value!!
                    val x = xFor(i)
                    val y = yFor(v)
                    if (i == from) gp.moveTo(x, y) else gp.lineTo(x, y)
                }
                for (i in to downTo from) {
                    val v = sec[i].value!!
                    gp.lineTo(xFor(i), yFor(v))
                }
                gp.close()
                drawPath(gp, gapFillColor)
            }

            // Upper line (clocked) — thinner, dashed, muted.
            drawPath(
                path = buildLinePath(sec),
                color = secondaryLineColor,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                ),
            )
        } else if (zoneBands == null) {
            // Single-series mode: classic under-line area fill, anchored at the
            // axis floor (safeYMin) — not 0 — so a tightened axis still fills
            // cleanly to the bottom of the plot. Suppressed when zone bands are
            // present so the band color stays the dominant background signal.
            val floorY = yFor(safeYMin)
            val fillPath = Path()
            var inSegment = false
            var segmentStartX = 0f
            var segmentLastX = 0f

            fun closeFillSegment() {
                if (!inSegment) return
                fillPath.lineTo(segmentLastX, floorY)
                fillPath.lineTo(segmentStartX, floorY)
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
                    fillPath.moveTo(x, floorY)
                    fillPath.lineTo(x, y)
                    inSegment = true
                    segmentStartX = x
                } else {
                    fillPath.lineTo(x, y)
                }
                segmentLastX = x
            }
            closeFillSegment()
            drawPath(path = fillPath, color = singleFillColor)
        }

        // Primary line (effective / brain budget) — bold solid stroke, drawn last
        // so it sits above the gap fill or area fill.
        drawPath(
            path = buildLinePath(points),
            color = primaryColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )

        // Dots at each known primary value; emphasize the most recent one and any
        // currently-selected one. (Secondary line is intentionally dot-less so the
        // gap reads as the feature, not two competing series.)
        points.forEachIndexed { i, p ->
            val v = p.value ?: return@forEachIndexed
            val isLast = i == points.lastIndex
            val isSelected = i == safeSelected
            val r = when {
                isSelected -> 6.dp.toPx()
                isLast -> 5.dp.toPx()
                else -> 3.dp.toPx()
            }
            drawCircle(
                color = primaryColor,
                radius = r,
                center = Offset(xFor(i), yFor(v)),
            )
        }

        // Tooltip for the tapped dot.
        safeSelected?.let { idx ->
            val v = points[idx].value ?: return@let
            val cx = xFor(idx)
            val cy = yFor(v)
            val label = points[idx].label
            val secV = if (hasSecondary) secondaryPoints!![idx].value else null
            val text = when {
                hasSecondary && secV != null && dualTooltipFormat != null ->
                    dualTooltipFormat(v, secV, label)
                hasSecondary && secV != null ->
                    "${valueFormat(v)} / ${valueFormat(secV)}" +
                            if (label.isNotEmpty()) " · $label" else ""
                label.isNotEmpty() -> "${valueFormat(v)} · $label"
                else -> valueFormat(v)
            }
            val layout = textMeasurer.measure(text = text, style = tooltipStyle)
            val padH = 8.dp.toPx()
            val padV = 4.dp.toPx()
            val boxW = layout.size.width + padH * 2
            val boxH = layout.size.height + padV * 2
            val boxX = (cx - boxW / 2).coerceIn(0f, size.width - boxW)
            // Prefer above the dot; flip below if there isn't room.
            val above = cy - boxH - 8.dp.toPx()
            val boxY = if (above >= 0f) above else cy + 8.dp.toPx()
            val corner = CornerRadius(6.dp.toPx())
            drawRoundRect(
                color = tooltipBg,
                topLeft = Offset(boxX, boxY),
                size = Size(boxW, boxH),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = tooltipBorder,
                topLeft = Offset(boxX, boxY),
                size = Size(boxW, boxH),
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(boxX + padH, boxY + padV),
            )
        }
    }
}

// Tiny inline legend for the dual-series productivity chart — explains what
// the solid vs. dashed line mean. Drawn as small Canvas swatches so it stays
// truthful to the chart's actual stroke style.
@Composable
private fun DualSeriesLegend(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = primary.copy(alpha = 0.45f)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.width(14.dp).height(8.dp)) {
            drawLine(
                color = primary,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "effective",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Canvas(modifier = Modifier.width(14.dp).height(8.dp)) {
            drawLine(
                color = muted,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "clocked",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

// Yerkes-Dodson zone tag for the headline (SCIENTIFIC_FOUNDATIONS §6).
// Thresholds match BrainBudgetGauge so the live readiness card and the 30d
// average speak the same vocabulary.
private fun budgetZoneLabel(score: Int): Pair<String, Color> = when {
    score >= 70 -> "optimal zone" to BudgetGreen
    score >= 40 -> "approaching threshold" to BudgetYellow
    else -> "depleted zone" to BudgetRed
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

            // Pair effective + raw clocked into two series the chart renders
            // together. Trim by raw==0 so leading inactive buckets fall away
            // (raw==0 implies effective==0 by construction); a bucket where
            // raw>0 but effective==0 is meaningful and stays visible as a
            // wide gap with the effective line on the floor.
            val pairedPoints = when (period) {
                ChartPeriod.WEEKLY -> productivity.weekly.map {
                    val label = shortDateLabel(it.weekStart, monthOnly = false)
                    ChartPoint(label, it.effectiveFocusedMinutes) to
                            ChartPoint(label, it.rawFocusedMinutes)
                }
                ChartPeriod.MONTHLY -> productivity.monthly.map {
                    val label = shortDateLabel(it.monthStart, monthOnly = true)
                    ChartPoint(label, it.effectiveFocusedMinutes) to
                            ChartPoint(label, it.rawFocusedMinutes)
                }
            }.dropWhile { (_, raw) -> (raw.value ?: 0f) == 0f }
            val points = pairedPoints.map { it.first }
            val rawPoints = pairedPoints.map { it.second }

            if (points.size >= 2) {
                // yMax sized to the larger (raw) series so the upper line
                // doesn't clip; same rounding rule as before.
                val maxObserved = rawPoints.maxOfOrNull { it.value ?: 0f } ?: 0f
                val yMax = (kotlin.math.ceil(maxObserved * 1.15f / 30f) * 30f)
                    .coerceAtLeast(60f)
                TrendLineChart(
                    points = points,
                    secondaryPoints = rawPoints,
                    yMax = yMax,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    valueFormat = { "${it.toInt()} min" },
                    dualTooltipFormat = { eff, clocked, label ->
                        val pct = if (clocked > 0f) (eff / clocked * 100f).toInt() else 0
                        val datePart = if (label.isNotEmpty()) " · $label" else ""
                        "${eff.toInt()} of ${clocked.toInt()} min · ${pct}%$datePart"
                    },
                )
                Spacer(modifier = Modifier.height(6.dp))
                ChartAxisLabels(points)
                Spacer(modifier = Modifier.height(4.dp))
                DualSeriesLegend()
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
                        text = "$arrow ${"%+.0f".format(delta)}% vs same point 4 weeks ago " +
                                "($baseline → ${productivity.currentWeekMinutes.toInt()} min)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = deltaColor,
                    )
                }
            }
        }
    }
}

// ── Consolidation Quality (Pillar 3) ─────────────────────────────────────────
//
// Post-session retention: did the brain get to keep what it just worked on?
// Sourced from /endurance/snapshot's `consolidation` pillar (current_avg of
// the 0–100 consolidation_score over the 14-day window). Surfaced here as a
// first-class card — not a buried distraction metric — per the four-pillar
// model (SCIENTIFIC_FOUNDATIONS §3, §8).

@Composable
private fun ConsolidationCard(snapshot: EnduranceSnapshot?) {
    val pillar = snapshot?.pillar("consolidation")

    Card(
        shape = RoundedCornerShape(BrainplanerTheme.radius.lg),
        colors = CardDefaults.cardColors(containerColor = BrainplanerTheme.surfaceRoles.surface2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "CONSOLIDATION",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "How well you protected the post-session memory window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            val current = pillar?.currentAvg
            if (current == null || pillar.currentN == 0) {
                Text(
                    text = "Complete a few sessions with cooldown tracking on " +
                            "to start measuring consolidation quality.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text("🛡️", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${current.toInt()} / 100",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = consolidationLabel(current.toInt()) + " · last 14d",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Direction line, modelled after the productivity delta voice.
            val baseline = pillar.baselineAvg?.toInt()
            val deltaPct = pillar.deltaPct
            if (deltaPct != null && baseline != null && pillar.baselineN >= 3) {
                Spacer(modifier = Modifier.height(10.dp))
                val arrow = when {
                    pillar.direction == "improving" -> "↑"
                    pillar.direction == "declining" -> "↓"
                    else -> "→"
                }
                val color = when (pillar.direction) {
                    "improving" -> BudgetGreen
                    "declining" -> BudgetRed
                    else -> MaterialTheme.colorScheme.primary
                }
                Text(
                    text = "$arrow ${"%+.0f".format(deltaPct)}% vs same point 4 weeks ago " +
                            "($baseline → ${current.toInt()})",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = color,
                )
            } else if (pillar.baselineN < 3) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Baseline still forming (need 3+ days of cooldown data " +
                            "from 2–4 weeks ago).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Data-quality hint when phone tracking has been thin. The
            // snapshot tells us how many sessions actually contributed.
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Based on ${pillar.currentN} sessions in the last 14d.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// 0-100 score → human label; mirrors consolidation_compute label thresholds.
private fun consolidationLabel(score: Int): String = when {
    score >= 85 -> "strong protection"
    score >= 70 -> "protected"
    score >= 50 -> "partial"
    else -> "disrupted"
}

// ── Endurance — meta-pillar compact summary ──────────────────────────────────
//
// Endurance has no per-session value of its own (see SCIENTIFIC_FOUNDATIONS
// §3, §9). This row condenses the longitudinal signal across the three
// primary pillars and the named mechanisms, with a slot reserved for the
// fNIRS validation overlay used in demos.

@Composable
private fun EnduranceCompactSummary(
    snapshot: EnduranceSnapshot?,
    legacy: EnduranceSummary?,
) {
    Card(
        shape = RoundedCornerShape(BrainplanerTheme.radius.lg),
        colors = CardDefaults.cardColors(containerColor = BrainplanerTheme.surfaceRoles.surface2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ENDURANCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (snapshot != null) "${snapshot.windowDaysCurrent}d vs prior ${snapshot.windowDaysBaseline}d"
                    else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Trend signal across the three primary pillars.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (snapshot == null) {
                Text(
                    text = "Snapshot unavailable — pull to refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            // Three pillar trend rows.
            TrendRow("Readiness", snapshot.pillar("readiness"))
            TrendRow("Productivity (EQM)", snapshot.pillar("eqm"))
            TrendRow("Consolidation", snapshot.pillar("consolidation"))

            Spacer(modifier = Modifier.height(10.dp))

            // Mechanism callout: longest contiguous active stretch per
            // session, cut by both pauses and phone unlocks. Combines the
            // depth-of-focus and context-switch signals into one metric.
            val sustained = snapshot.mechanism("sustained_attention_minutes_median")
            if (sustained?.currentAvg != null) {
                MechanismRow(
                    label = "Longest focus block",
                    valueText = "${sustained.currentAvg.toInt()} min",
                    direction = sustained.direction,
                    deltaPct = sustained.deltaPct,
                )
            } else if (legacy?.sustainableMinutesCurrent != null) {
                MechanismRow(
                    label = "Longest focus block",
                    valueText = "${legacy.sustainableMinutesCurrent.toInt()} min",
                    direction = null,
                    deltaPct = null,
                )
            }

            // fNIRS validation slot — placeholder for the demo extension.
            // Reserves the row so the demo overlay drops in without
            // restructuring the card.
            Spacer(modifier = Modifier.height(10.dp))
            FnirsValidationSlot()
        }
    }
}

@Composable
private fun TrendRow(label: String, line: TrendLine?) {
    val direction = line?.direction ?: "insufficient_data"
    val deltaPct = line?.deltaPct
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        DirectionBadge(direction)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatDelta(direction, deltaPct),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = directionColor(direction),
        )
    }
}

@Composable
private fun MechanismRow(
    label: String,
    valueText: String,
    direction: String?,
    deltaPct: Float?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (direction != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = formatDelta(direction, deltaPct),
                style = MaterialTheme.typography.labelSmall,
                color = directionColor(direction),
            )
        }
    }
}

@Composable
private fun DirectionBadge(direction: String) {
    val symbol = when (direction) {
        "improving" -> "↑"
        "declining" -> "↓"
        "stable" -> "→"
        else -> "·"
    }
    Text(
        text = symbol,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = directionColor(direction),
    )
}

@Composable
private fun directionColor(direction: String): Color = when (direction) {
    "improving" -> BudgetGreen
    "declining" -> BudgetRed
    "stable" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatDelta(direction: String, deltaPct: Float?): String = when {
    direction == "insufficient_data" -> "thin data"
    deltaPct == null -> "—"
    else -> "%+.0f%%".format(deltaPct)
}

// Reserved row for fNIRS validation — wired up at demo time. Keeping it
// here (rather than as a future addition) so the layout above doesn't
// shift when we drop the validation overlay in.
@Composable
private fun FnirsValidationSlot() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🧠", fontSize = 14.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "fNIRS validation",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "pending",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

