package com.brainplaner.phone.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brainplaner.phone.LocalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class HomeUiState(
    val isLoading: Boolean = true,
    val sessionSummary: String? = null,
    val handoffNextAction: String? = null,
    val readinessScore: String? = null,
    val planningAccuracyLine: String? = null,
    val hasCheckedInToday: Boolean = false,
    val isCheckInSubmitting: Boolean = false,
    val checkInError: String? = null,
    val isOffline: Boolean = false,
)

class HomeViewModel(
    application: Application,
    private val userId: String,
    private val apiUrl: String,
    private val userToken: String,
) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            // Immediately show local data so the user is never blocked.
            val localCheckedIn = LocalStore.hasCheckedInToday(ctx)
            val localNextAction = LocalStore.getLastNextAction(ctx)
            val localSummary = LocalStore.getLastSummary(ctx)
            val localCheckinData = LocalStore.getCheckInData(ctx)
            val localScore = localCheckinData?.let { (sleep, score, _) ->
                LocalStore.estimateReadiness(sleep, score).toString()
            }

            _state.value = HomeUiState(
                isLoading = false,
                hasCheckedInToday = localCheckedIn,
                readinessScore = localScore,
                handoffNextAction = localNextAction,
                sessionSummary = localSummary,
                isOffline = false,
            )

            // Try to enrich with cloud data in background (non-blocking).
            launch { trySyncPendingCheckIn() }
            launch { tryEnrichFromCloud() }
        }
    }

    /** Try to sync any pending check-in to cloud (fire-and-forget). */
    private suspend fun trySyncPendingCheckIn() = withContext(Dispatchers.IO) {
        if (LocalStore.isCheckInSynced(ctx)) return@withContext
        val data = LocalStore.getCheckInData(ctx) ?: return@withContext
        val (sleep, score, rhr) = data
        val synced = postCheckInToCloud(sleep, score, rhr)
        if (synced) LocalStore.markCheckInSynced(ctx)
    }

    /** Try to fetch cloud readiness/brief and update state. */
    private suspend fun tryEnrichFromCloud() {
        val brief = withContext(Dispatchers.IO) { fetchBrief() }
        val readinessData = withContext(Dispatchers.IO) { fetchReadinessData() }

        val (cloudScore, cloudCheckedIn, planningLine) = readinessData
        val current = _state.value

        _state.value = current.copy(
            readinessScore = cloudScore ?: current.readinessScore,
            hasCheckedInToday = cloudCheckedIn || current.hasCheckedInToday,
            planningAccuracyLine = planningLine ?: current.planningAccuracyLine,
            sessionSummary = brief.sessionSummary ?: current.sessionSummary,
            handoffNextAction = brief.handoffNextAction ?: current.handoffNextAction,
            isOffline = cloudScore == null && current.readinessScore != null,
        )
    }

    /** Save check-in locally first, then try cloud sync. Never blocks user. */
    fun submitCheckIn(sleepHours: Float, sleepScore: Int, rhr: Int?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isCheckInSubmitting = true, checkInError = null)

            // Save locally immediately.
            LocalStore.saveCheckIn(ctx, sleepHours, sleepScore, rhr)
            val localScore = LocalStore.estimateReadiness(sleepHours, sleepScore)

            _state.value = _state.value.copy(
                isCheckInSubmitting = false,
                hasCheckedInToday = true,
                readinessScore = localScore.toString(),
            )

            // Fire-and-forget cloud sync.
            launch(Dispatchers.IO) {
                val synced = postCheckInToCloud(sleepHours, sleepScore, rhr)
                if (synced) LocalStore.markCheckInSynced(ctx)
            }
        }
    }

    /** Post check-in to cloud. Returns true on success. */
    private suspend fun postCheckInToCloud(sleepHours: Float, sleepScore: Int, rhr: Int?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val rhrJson = if (rhr != null) ""","rhr":$rhr""" else ""
                val json = """{"sleep_hours":$sleepHours,"sleep_score":$sleepScore$rhrJson}"""
                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$apiUrl/readiness/checkin")
                    .post(body)
                    .addHeader("Authorization", "Bearer $userToken")
                    .addHeader("X-User-ID", userId)
                    .addHeader("Content-Type", "application/json")
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            } catch (_: Exception) {
                false
            }
        }
    }

    private suspend fun fetchBrief(): HomeUiState = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$apiUrl/reflection/next-brief")
                .get()
                .addHeader("Authorization", "Bearer $userToken")
                .addHeader("X-User-ID", userId)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    HomeUiState(
                        isLoading = false,
                        sessionSummary = extractString(body, "session_summary"),
                        handoffNextAction = extractString(body, "handoff_next_action"),
                    )
                } else {
                    HomeUiState(isLoading = false)
                }
            }
        } catch (_: Exception) {
            HomeUiState(isLoading = false)
        }
    }

    private fun extractString(json: String, key: String): String? =
        Regex(""""$key"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)

    /** Returns (scoreString, hasCheckinToday, planningAccuracyLine). Best-effort, no retries. */
    private suspend fun fetchReadinessData(): Triple<String?, Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$apiUrl/readiness")
                .get()
                .addHeader("Authorization", "Bearer $userToken")
                .addHeader("X-User-ID", userId)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext Triple(null, false, null)
                    val score = Regex(""""score"\s*:\s*(\d+)""").find(body)?.groupValues?.get(1)
                    val hasCheckin = Regex(""""has_checkin_today"\s*:\s*(true|false)""")
                        .find(body)?.groupValues?.get(1) == "true"
                    val line = buildPlanningAccuracyLine(body)
                    return@withContext Triple(score, hasCheckin, line)
                }
            }
        } catch (_: Exception) { }
        Triple(null, false, null)
    }

    private fun buildPlanningAccuracyLine(json: String): String? {
        val planned = Regex(""""yesterday_planned_minutes"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val actual = Regex(""""yesterday_actual_minutes"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val diff = actual - planned
        val verdict = when {
            diff > 5  -> "overrun +${diff}m"
            diff < -5 -> "underrun ${diff}m"
            else      -> "on target"
        }
        return "Yesterday: ${planned}m planned → ${actual}m actual ($verdict)"
    }

    companion object {
        fun factory(userId: String, apiUrl: String, userToken: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    // This factory is used from composables where we don't have direct Application access.
                    // The AndroidViewModel variant needs the Application, which is provided by the
                    // default ViewModelProvider mechanism once we use the correct overload.
                    throw UnsupportedOperationException("Use appFactory instead")
                }
            }

        fun appFactory(application: Application, userId: String, apiUrl: String, userToken: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(application, userId, apiUrl, userToken) as T
            }
    }
}
