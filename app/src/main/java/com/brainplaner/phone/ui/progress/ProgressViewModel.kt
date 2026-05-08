package com.brainplaner.phone.ui.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class DayLabel { GREEN, YELLOW, RED, OFF;

    companion object {
        fun fromString(value: String?): DayLabel = when (value?.lowercase()) {
            "green" -> GREEN
            "yellow" -> YELLOW
            "red" -> RED
            else -> OFF
        }
    }
}

data class DailyStatus(
    val date: String,                   // ISO "yyyy-MM-dd"
    val label: DayLabel,
    val sessions: Int,
    val completionRatio: Float?,
    val overshootMinutes: Int,
    val avgDrainScore: Float?,
    val avgCooldownIndex: Float?,
)

data class StreakSummary(
    val currentLabel: DayLabel,
    val greenCount: Int,
    val yellowCount: Int,
    val redCount: Int,
    val offCount: Int,
)

data class WeeklyProductivity(
    val weekStart: String,
    val effectiveFocusedMinutes: Float,
    val rawFocusedMinutes: Float,
    val longestSessionMinutes: Float,
    val sessions: Int,
)

data class MonthlyProductivity(
    val monthStart: String,
    val effectiveFocusedMinutes: Float,
    val rawFocusedMinutes: Float,
    val longestSessionMinutes: Float,
    val sessions: Int,
)

data class ProductivitySummary(
    val weekly: List<WeeklyProductivity>,
    val monthly: List<MonthlyProductivity>,
    val currentWeekMinutes: Float,
    val currentMonthMinutes: Float,
    val baselineWeekMinutes: Float,
    val deltaPct: Float?,
    val longestSessionCurrentPeriod: Float,
    val longestSessionBaselinePeriod: Float,
)

data class WeeklyCapacity(
    val weekStart: String,
    val avgBrainbudget: Float?,
    val nDays: Int,
)

data class MonthlyCapacity(
    val monthStart: String,
    val avgBrainbudget: Float?,
    val nDays: Int,
)

data class CapacitySummary(
    val weekly: List<WeeklyCapacity>,
    val monthly: List<MonthlyCapacity>,
    val currentWeekAvg: Float?,
    val baselineWeekAvg: Float?,
    val deltaPct: Float?,
    val recent30dAvg: Float?,
    val nDays30d: Int,
)

enum class EnduranceTrend { GROWING, STABLE, REGRESSING, BUILDING;
    companion object {
        fun fromString(v: String?): EnduranceTrend = when (v?.lowercase()) {
            "growing" -> GROWING
            "stable" -> STABLE
            "regressing" -> REGRESSING
            else -> BUILDING
        }
    }
}

data class EnduranceSummary(
    val sustainableMinutesCurrent: Float?,
    val sustainableMinutesBaseline: Float?,
    val qualifyingSessionsCurrent: Int,
    val qualifyingSessionsBaseline: Int,
    val trend: EnduranceTrend,
    val nextTargetMinutes: Int?,
    val supportingProof: String?,
)

data class FollowThroughSummary(
    val rate: Float?,                // 0..1, null if no qualifying handoffs
    val followedCount: Int,
    val totalCount: Int,
    val currentStreak: Int,
    val windowHours: Float,
)

// One longitudinal pillar/mechanism trend line from /endurance/snapshot.
// Mirrors services/endurance_compute.py TrendLine.
data class TrendLine(
    val pillar: String,                  // "readiness" | "eqm" | "consolidation" | "mechanism"
    val mechanism: String,               // e.g. "consolidation_score", "sustained_attention_minutes_median"
    val currentAvg: Float?,
    val baselineAvg: Float?,
    val delta: Float?,
    val deltaPct: Float?,
    val direction: String,               // "improving" | "stable" | "declining" | "insufficient_data"
    val currentN: Int,
    val baselineN: Int,
)

// Meta-pillar snapshot: trend lines across the three primary pillars
// plus mechanism-named lines (see SCIENTIFIC_FOUNDATIONS §3 and §9).
data class EnduranceSnapshot(
    val windowDaysCurrent: Int,
    val windowDaysBaseline: Int,
    val pillars: List<TrendLine>,
    val mechanisms: List<TrendLine>,
) {
    fun pillar(name: String): TrendLine? = pillars.firstOrNull { it.pillar == name }
    fun mechanism(name: String): TrendLine? = mechanisms.firstOrNull { it.mechanism == name }
}

data class ProgressUiState(
    val isLoading: Boolean = true,
    val windowDays: Int = 30,
    val streak: StreakSummary? = null,
    val dailyStatus: List<DailyStatus> = emptyList(),
    val productivity: ProductivitySummary? = null,
    val capacity: CapacitySummary? = null,
    val endurance: EnduranceSummary? = null,
    val enduranceSnapshot: EnduranceSnapshot? = null,
    val followThrough: FollowThroughSummary? = null,
    val completionRatioTrend7d: Float? = null,
    val completionRatioTrend30d: Float? = null,
    val focusScoreAvg30d: Float? = null,
    val errorReason: String? = null,
)

class ProgressViewModel(
    application: Application,
    private val userId: String,
    private val apiUrl: String,
    private val userToken: String,
) : AndroidViewModel(application) {

    // Longer timeouts than other screens: /progress is a heavy aggregation
    // and Render free-tier cold starts can take 60+ seconds to wake up.
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state

    init {
        load(days = 30)
    }

    fun load(days: Int = 30) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorReason = null)
            val base = withContext(Dispatchers.IO) { fetch(days) }
            // Endurance snapshot is a separate endpoint. Fetch it after the
            // primary /progress call so a partial failure on the snapshot
            // doesn't blank out the whole screen.
            val snapshot = if (base.errorReason == null) {
                withContext(Dispatchers.IO) { fetchEnduranceSnapshot() }
            } else null
            _state.value = base.copy(enduranceSnapshot = snapshot)
        }
    }

    private fun fetch(days: Int): ProgressUiState {
        return try {
            val request = Request.Builder()
                .url("$apiUrl/progress?days=$days")
                .get()
                .addHeader("Authorization", "Bearer $userToken")
                .addHeader("X-User-ID", userId)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return ProgressUiState(
                        isLoading = false,
                        windowDays = days,
                        errorReason = "progress HTTP ${response.code}",
                    )
                }
                val body = response.body?.string().orEmpty()
                parse(body, days)
            }
        } catch (e: Exception) {
            ProgressUiState(
                isLoading = false,
                windowDays = days,
                errorReason = "progress ${e.javaClass.simpleName}",
            )
        }
    }

    private fun fetchEnduranceSnapshot(): EnduranceSnapshot? {
        return try {
            val request = Request.Builder()
                .url("$apiUrl/endurance/snapshot")
                .get()
                .addHeader("Authorization", "Bearer $userToken")
                .addHeader("X-User-ID", userId)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                parseEnduranceSnapshot(JSONObject(body))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseEnduranceSnapshot(root: JSONObject): EnduranceSnapshot {
        fun parseTrendLines(key: String): List<TrendLine> {
            val arr = root.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        TrendLine(
                            pillar = o.optString("pillar"),
                            mechanism = o.optString("mechanism"),
                            currentAvg = o.optNullableDouble("current_avg")?.toFloat(),
                            baselineAvg = o.optNullableDouble("baseline_avg")?.toFloat(),
                            delta = o.optNullableDouble("delta")?.toFloat(),
                            deltaPct = o.optNullableDouble("delta_pct")?.toFloat(),
                            direction = o.optString("direction", "insufficient_data"),
                            currentN = o.optInt("current_n", 0),
                            baselineN = o.optInt("baseline_n", 0),
                        )
                    )
                }
            }
        }
        return EnduranceSnapshot(
            windowDaysCurrent = root.optInt("window_days_current", 14),
            windowDaysBaseline = root.optInt("window_days_baseline", 28),
            pillars = parseTrendLines("pillars"),
            mechanisms = parseTrendLines("mechanisms"),
        )
    }

    private fun parse(body: String, requestedDays: Int): ProgressUiState {
        val root = JSONObject(body)
        val streakJson = root.optJSONObject("streak")
        val streak = streakJson?.let {
            StreakSummary(
                currentLabel = DayLabel.fromString(it.optString("current_label")),
                greenCount = it.optInt("green_count", 0),
                yellowCount = it.optInt("yellow_count", 0),
                redCount = it.optInt("red_count", 0),
                offCount = it.optInt("off_count", 0),
            )
        }

        val daysArr = root.optJSONArray("daily_status")
        val days = buildList {
            if (daysArr != null) {
                for (i in 0 until daysArr.length()) {
                    val d = daysArr.optJSONObject(i) ?: continue
                    add(
                        DailyStatus(
                            date = d.optString("date"),
                            label = DayLabel.fromString(d.optString("label")),
                            sessions = d.optInt("sessions", 0),
                            completionRatio = d.optNullableDouble("completion_ratio")?.toFloat(),
                            overshootMinutes = d.optInt("overshoot_minutes", 0),
                            avgDrainScore = d.optNullableDouble("avg_drain_score")?.toFloat(),
                            avgCooldownIndex = d.optNullableDouble("avg_cooldown_index")?.toFloat(),
                        )
                    )
                }
            }
        }

        val productivity = root.optJSONObject("productivity")?.let { p ->
            val weeklyArr = p.optJSONArray("weekly")
            val weekly = buildList {
                if (weeklyArr != null) {
                    for (i in 0 until weeklyArr.length()) {
                        val w = weeklyArr.optJSONObject(i) ?: continue
                        add(
                            WeeklyProductivity(
                                weekStart = w.optString("week_start"),
                                effectiveFocusedMinutes = w.optDouble("effective_focused_minutes", 0.0).toFloat(),
                                rawFocusedMinutes = w.optDouble("raw_focused_minutes", 0.0).toFloat(),
                                longestSessionMinutes = w.optDouble("longest_session_minutes", 0.0).toFloat(),
                                sessions = w.optInt("sessions", 0),
                            )
                        )
                    }
                }
            }
            val monthlyArr = p.optJSONArray("monthly")
            val monthly = buildList {
                if (monthlyArr != null) {
                    for (i in 0 until monthlyArr.length()) {
                        val m = monthlyArr.optJSONObject(i) ?: continue
                        add(
                            MonthlyProductivity(
                                monthStart = m.optString("month_start"),
                                effectiveFocusedMinutes = m.optDouble("effective_focused_minutes", 0.0).toFloat(),
                                rawFocusedMinutes = m.optDouble("raw_focused_minutes", 0.0).toFloat(),
                                longestSessionMinutes = m.optDouble("longest_session_minutes", 0.0).toFloat(),
                                sessions = m.optInt("sessions", 0),
                            )
                        )
                    }
                }
            }
            ProductivitySummary(
                weekly = weekly,
                monthly = monthly,
                currentWeekMinutes = p.optDouble("current_week_minutes", 0.0).toFloat(),
                currentMonthMinutes = p.optDouble("current_month_minutes", 0.0).toFloat(),
                baselineWeekMinutes = p.optDouble("baseline_week_minutes", 0.0).toFloat(),
                deltaPct = p.optNullableDouble("delta_pct")?.toFloat(),
                longestSessionCurrentPeriod = p.optDouble("longest_session_current_period", 0.0).toFloat(),
                longestSessionBaselinePeriod = p.optDouble("longest_session_baseline_period", 0.0).toFloat(),
            )
        }

        val capacity = root.optJSONObject("capacity")?.let { c ->
            val weeklyArr = c.optJSONArray("weekly")
            val weekly = buildList {
                if (weeklyArr != null) {
                    for (i in 0 until weeklyArr.length()) {
                        val w = weeklyArr.optJSONObject(i) ?: continue
                        add(
                            WeeklyCapacity(
                                weekStart = w.optString("week_start"),
                                avgBrainbudget = w.optNullableDouble("avg_brainbudget")?.toFloat(),
                                nDays = w.optInt("n_days", 0),
                            )
                        )
                    }
                }
            }
            val monthlyArr = c.optJSONArray("monthly")
            val monthly = buildList {
                if (monthlyArr != null) {
                    for (i in 0 until monthlyArr.length()) {
                        val m = monthlyArr.optJSONObject(i) ?: continue
                        add(
                            MonthlyCapacity(
                                monthStart = m.optString("month_start"),
                                avgBrainbudget = m.optNullableDouble("avg_brainbudget")?.toFloat(),
                                nDays = m.optInt("n_days", 0),
                            )
                        )
                    }
                }
            }
            CapacitySummary(
                weekly = weekly,
                monthly = monthly,
                currentWeekAvg = c.optNullableDouble("current_week_avg")?.toFloat(),
                baselineWeekAvg = c.optNullableDouble("baseline_week_avg")?.toFloat(),
                deltaPct = c.optNullableDouble("delta_pct")?.toFloat(),
                recent30dAvg = c.optNullableDouble("recent_30d_avg")?.toFloat(),
                nDays30d = c.optInt("n_days_30d", 0),
            )
        }

        val endurance = root.optJSONObject("endurance")?.let { e ->
            EnduranceSummary(
                sustainableMinutesCurrent = e.optNullableDouble("sustainable_minutes_current")?.toFloat(),
                sustainableMinutesBaseline = e.optNullableDouble("sustainable_minutes_baseline")?.toFloat(),
                qualifyingSessionsCurrent = e.optInt("qualifying_sessions_current", 0),
                qualifyingSessionsBaseline = e.optInt("qualifying_sessions_baseline", 0),
                trend = EnduranceTrend.fromString(e.optString("trend")),
                nextTargetMinutes = if (e.isNull("next_target_minutes")) null else e.optInt("next_target_minutes").takeIf { it > 0 },
                supportingProof = if (e.isNull("supporting_proof")) null else e.optString("supporting_proof").takeIf { it.isNotBlank() },
            )
        }

        val followThrough = root.optJSONObject("follow_through")?.let { f ->
            FollowThroughSummary(
                rate = f.optNullableDouble("rate")?.toFloat(),
                followedCount = f.optInt("followed_count", 0),
                totalCount = f.optInt("total_count", 0),
                currentStreak = f.optInt("current_streak", 0),
                windowHours = f.optDouble("window_hours", 48.0).toFloat(),
            )
        }

        return ProgressUiState(
            isLoading = false,
            windowDays = root.optInt("window_days", requestedDays),
            streak = streak,
            dailyStatus = days,
            productivity = productivity,
            capacity = capacity,
            endurance = endurance,
            followThrough = followThrough,
            completionRatioTrend7d = root.optNullableDouble("completion_ratio_trend_7d")?.toFloat(),
            completionRatioTrend30d = root.optNullableDouble("completion_ratio_trend_30d")?.toFloat(),
            focusScoreAvg30d = root.optNullableDouble("focus_score_avg_30d")?.toFloat(),
            errorReason = null,
        )
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeIf { !it.isNaN() }

    companion object {
        fun factory(application: Application, userId: String, apiUrl: String, userToken: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProgressViewModel(application, userId, apiUrl, userToken) as T
            }
    }
}
