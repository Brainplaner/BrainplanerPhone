package com.brainplaner.phone

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Calendar
import java.util.UUID

/**
 * Local-first storage using SharedPreferences.
 * Saves check-ins, sessions, and reflections locally so the app
 * never blocks on cloud availability. Cloud sync is best-effort.
 */
object LocalStore {
    private const val PREFS = "brainplaner_local"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun utcNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    // ── Daily check-in ──────────────────────────────────────────

    fun hasCheckedInToday(ctx: Context): Boolean =
        prefs(ctx).getBoolean("checkin_${todayKey()}", false)

    fun saveCheckIn(ctx: Context, sleepHours: Float, sleepScore: Int, rhr: Int?) {
        prefs(ctx).edit()
            .putBoolean("checkin_${todayKey()}", true)
            .putFloat("checkin_sleep_hours_${todayKey()}", sleepHours)
            .putInt("checkin_sleep_score_${todayKey()}", sleepScore)
            .apply {
                if (rhr != null) putInt("checkin_rhr_${todayKey()}", rhr)
            }
            .putBoolean("checkin_synced_${todayKey()}", false)
            .apply()
    }

    fun isCheckInSynced(ctx: Context): Boolean =
        prefs(ctx).getBoolean("checkin_synced_${todayKey()}", false)

    fun markCheckInSynced(ctx: Context) {
        prefs(ctx).edit().putBoolean("checkin_synced_${todayKey()}", true).apply()
    }

    fun getCheckInData(ctx: Context): Triple<Float, Int, Int?>? {
        val p = prefs(ctx)
        val key = todayKey()
        if (!p.getBoolean("checkin_$key", false)) return null
        val sleep = p.getFloat("checkin_sleep_hours_$key", 7f)
        val score = p.getInt("checkin_sleep_score_$key", 70)
        val rhr = if (p.contains("checkin_rhr_$key")) p.getInt("checkin_rhr_$key", 0) else null
        return Triple(sleep, score, rhr)
    }

    fun clearCheckIn(ctx: Context) {
        val key = todayKey()
        prefs(ctx).edit()
            .remove("checkin_$key")
            .remove("checkin_sleep_hours_$key")
            .remove("checkin_sleep_score_$key")
            .remove("checkin_rhr_$key")
            .remove("checkin_synced_$key")
            .apply()
    }

    // ── Local readiness estimate ────────────────────────────────

    /** Simple offline readiness score based on check-in data. */
    fun estimateReadiness(sleepHours: Float, sleepScore: Int): Int {
        // Weighted: sleep_score contributes 60%, sleep_hours 40%
        val hoursNorm = ((sleepHours - 4f) / 6f).coerceIn(0f, 1f) * 100f
        return ((sleepScore * 0.6f) + (hoursNorm * 0.4f)).toInt().coerceIn(0, 100)
    }

    // ── Session tracking ────────────────────────────────────────

    fun getActiveSession(ctx: Context): ActiveSession? {
        val p = prefs(ctx)
        val id = p.getString("session_id", null) ?: return null
        return ActiveSession(
            id = id,
            startMs = p.getLong("session_start_ms", 0L),
            plannedMinutes = p.getInt("session_planned_min", 45),
            cloudSynced = p.getBoolean("session_cloud_synced", false),
        )
    }

    fun saveSessionStart(ctx: Context, sessionId: String, plannedMinutes: Int, cloudSynced: Boolean) {
        prefs(ctx).edit()
            .putString("session_id", sessionId)
            .putLong("session_start_ms", System.currentTimeMillis())
            .putInt("session_planned_min", plannedMinutes)
            .putBoolean("session_cloud_synced", cloudSynced)
            .apply()
    }

    fun markSessionCloudSynced(ctx: Context, cloudSessionId: String) {
        prefs(ctx).edit()
            .putString("session_id", cloudSessionId)
            .putBoolean("session_cloud_synced", true)
            .apply()
    }

    fun clearActiveSession(ctx: Context) {
        prefs(ctx).edit()
            .remove("session_id")
            .remove("session_start_ms")
            .remove("session_planned_min")
            .remove("session_cloud_synced")
            .apply()
    }

    fun generateLocalSessionId(): String = "local-${UUID.randomUUID()}"

    // ── Last reflection handoff (for continuity display) ────────

    fun saveLastReflection(ctx: Context, nextAction: String, summary: String?) {
        prefs(ctx).edit()
            .putString("last_next_action", nextAction)
            .putString("last_summary", summary)
            .apply()
    }

    fun getLastNextAction(ctx: Context): String? =
        prefs(ctx).getString("last_next_action", null)

    fun getLastSummary(ctx: Context): String? =
        prefs(ctx).getString("last_summary", null)

    // ── Pending reflection (save locally, sync later) ───────────

    fun savePendingReflection(
        ctx: Context,
        sessionId: String,
        focusScore: Int,
        drainScore: Int,
        handoffNextAction: String,
        note: String?,
    ) {
        prefs(ctx).edit()
            .putString("pending_refl_session_id", sessionId)
            .putInt("pending_refl_focus", focusScore)
            .putInt("pending_refl_drain", drainScore)
            .putString("pending_refl_next_action", handoffNextAction)
            .putString("pending_refl_note", note)
            .putBoolean("pending_refl_exists", true)
            .apply()

        // Also update continuity display
        saveLastReflection(ctx, handoffNextAction, null)
    }

    fun hasPendingReflection(ctx: Context): Boolean =
        prefs(ctx).getBoolean("pending_refl_exists", false)

    data class PendingReflection(
        val sessionId: String,
        val focusScore: Int,
        val drainScore: Int,
        val handoffNextAction: String,
        val note: String?,
    )

    fun getPendingReflection(ctx: Context): PendingReflection? {
        val p = prefs(ctx)
        if (!p.getBoolean("pending_refl_exists", false)) return null
        return PendingReflection(
            sessionId = p.getString("pending_refl_session_id", "") ?: "",
            focusScore = p.getInt("pending_refl_focus", 0),
            drainScore = p.getInt("pending_refl_drain", 0),
            handoffNextAction = p.getString("pending_refl_next_action", "") ?: "",
            note = p.getString("pending_refl_note", null),
        )
    }

    fun clearPendingReflection(ctx: Context) {
        prefs(ctx).edit()
            .remove("pending_refl_session_id")
            .remove("pending_refl_focus")
            .remove("pending_refl_drain")
            .remove("pending_refl_next_action")
            .remove("pending_refl_note")
            .putBoolean("pending_refl_exists", false)
            .apply()
    }

    // ── Cognitive warm-up ────────────────────────────────────────

    fun isWarmupEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("warmup_enabled", false)

    fun setWarmupEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("warmup_enabled", enabled).apply()
    }

    fun saveWarmupResult(ctx: Context, medianMs: Int) {
        prefs(ctx).edit()
            .putInt("warmup_${todayKey()}", medianMs)
            .apply()
    }

    /** Median of last 14 days' warm-up results (excluding today). */
    fun getWarmupBaseline(ctx: Context): Int? {
        val p = prefs(ctx)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        val results = mutableListOf<Int>()
        for (i in 1..14) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val key = "warmup_${fmt.format(cal.time)}"
            if (p.contains(key)) {
                results.add(p.getInt(key, 0))
            }
        }
        if (results.isEmpty()) return null
        results.sort()
        val n = results.size
        return if (n % 2 == 0) (results[n / 2 - 1] + results[n / 2]) / 2 else results[n / 2]
    }

    data class ActiveSession(
        val id: String,
        val startMs: Long,
        val plannedMinutes: Int,
        val cloudSynced: Boolean,
    )
}
