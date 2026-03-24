package com.brainplaner.phone.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    val coachWhisper: String? = null,
    val handoffNextAction: String? = null,
    val readinessScore: String? = null,
    val hasCheckedInToday: Boolean = false,
    val isCheckInSubmitting: Boolean = false,
)

class HomeViewModel(
    private val userId: String,
    private val apiUrl: String,
    private val userToken: String,
) : ViewModel() {

    // Reuse the same OkHttp pattern already established in MainActivity.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState(isLoading = true)
            // Fetch brief, readiness, and today's check-in status in parallel.
            val brief = async { fetchBrief() }
            val readiness = async { fetchReadiness() }
            val checkedIn = async { fetchCheckedInToday() }
            _state.value = brief.await().copy(
                readinessScore = readiness.await(),
                hasCheckedInToday = checkedIn.await(),
            )
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
                        coachWhisper = extractString(body, "coach_whisper"),
                        handoffNextAction = extractString(body, "handoff_next_action"),
                    )
                } else {
                    // Degrade gracefully — user is not blocked if the endpoint is unavailable.
                    HomeUiState(isLoading = false)
                }
            }
        } catch (e: Exception) {
            HomeUiState(isLoading = false)
        }
    }

    private fun extractString(json: String, key: String): String? =
        Regex(""""$key"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)

    // Fetches the user's readiness score from the backend. Returns null when the
    // /readiness endpoint isn't available yet — HomeScreen shows "—" in that case.
    private suspend fun fetchReadiness(): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$apiUrl/readiness")
                .get()
                .addHeader("Authorization", "Bearer $userToken")
                .addHeader("X-User-ID", userId)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use null
                    // score is a JSON number, e.g. "score": 74
                    Regex(""""score"\s*:\s*(\d+)""").find(body)?.groupValues?.get(1)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Returns true if there is already a daily_inputs row for today (UTC date).
    // Checked via the /readiness endpoint — has_checkin_today is included in the response.
    private suspend fun fetchCheckedInToday(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$apiUrl/readiness")
                .get()
                .addHeader("Authorization", "Bearer $userToken")
                .addHeader("X-User-ID", userId)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use false
                    Regex(""""has_checkin_today"\s*:\s*(true|false)""").find(body)
                        ?.groupValues?.get(1) == "true"
                } else false
            }
        } catch (e: Exception) {
            false
        }
    }

    // Posts today's sleep check-in via Cloud API (avoids Supabase RLS with anon key).
    fun submitCheckIn(sleepHours: Float, sleepScore: Int, rhr: Int?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isCheckInSubmitting = true)
            val success = withContext(Dispatchers.IO) {
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
                } catch (e: Exception) {
                    false
                }
            }
            if (success) {
                load()
            } else {
                _state.value = _state.value.copy(isCheckInSubmitting = false)
            }
        }
    }

    companion object {
        fun factory(userId: String, apiUrl: String, userToken: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(userId, apiUrl, userToken) as T
            }
    }
}
