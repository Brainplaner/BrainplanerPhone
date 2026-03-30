package com.brainplaner.phone.ui.reflection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.brainplaner.phone.LocalStore
import com.brainplaner.phone.PhoneAwarenessService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ReflectionUiState(
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val error: String? = null,
)

class ReflectionViewModel(
    application: Application,
    private val sessionId: String,
    private val userId: String,
    private val apiUrl: String,
    private val userToken: String,
) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(ReflectionUiState())
    val state: StateFlow<ReflectionUiState> = _state

    init {
        // Try to sync any previously pending reflection from a past session.
        viewModelScope.launch(Dispatchers.IO) { trySyncPending() }
    }

    fun submit(
        focusScore: Int,            // 1–5: validates in-session distraction signals
        drainScore: Int,            // 1–5: validates recovery cost (overshoot + post-session)
        handoffNextAction: String,  // required: concrete next action
        note: String? = null,
    ) {
        if (_state.value.isSubmitting) return
        viewModelScope.launch {
            _state.value = ReflectionUiState(isSubmitting = true)

            // Save locally first — user is never blocked.
            LocalStore.savePendingReflection(
                ctx, sessionId, focusScore, drainScore, handoffNextAction, note
            )

            // Map focus to legacy execution_rating for backend compatibility.
            val executionRating = when {
                focusScore >= 4 -> "good"
                focusScore == 3 -> "ok"
                else -> "poor"
            }
            val cloudOk = withContext(Dispatchers.IO) {
                postReflection(focusScore, drainScore, handoffNextAction, note, executionRating)
            }
            if (cloudOk) LocalStore.clearPendingReflection(ctx)

            // Start 15-min post-session cooldown tracking (phone unlocks, screen time).
            PhoneAwarenessService.startCooldownForSession(ctx, sessionId)

            _state.value = ReflectionUiState(isSubmitted = true)
        }
    }

    private suspend fun trySyncPending() {
        val pending = LocalStore.getPendingReflection(ctx) ?: return
        val rating = when {
            pending.focusScore >= 4 -> "good"
            pending.focusScore == 3 -> "ok"
            else -> "poor"
        }
        val ok = postReflection(
            pending.focusScore, pending.drainScore, pending.handoffNextAction,
            pending.note, rating,
            overrideSessionId = pending.sessionId
        )
        if (ok) LocalStore.clearPendingReflection(ctx)
    }

    private suspend fun postReflection(
        focusScore: Int,
        drainScore: Int,
        handoffNextAction: String,
        note: String?,
        executionRating: String,
        overrideSessionId: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val sid = overrideSessionId ?: sessionId
            // Don't try to sync local-only sessions to cloud.
            if (sid.startsWith("local-")) return@withContext false

            val noteJson = if (note.isNullOrBlank()) "null"
            else "\"${note.trim().take(280).replace("\\", "\\\\").replace("\"", "\\\"")}\""
            val nextActionEscaped = handoffNextAction.take(200)
                .replace("\\", "\\\\").replace("\"", "\\\"")

            val body = """
                {
                    "execution_rating": "$executionRating",
                    "next_tuning": "same",
                    "next_action": "continue_same_task",
                    "focus_score": $focusScore,
                    "drain_score": $drainScore,
                    "handoff_next_action": "$nextActionEscaped",
                    "note": $noteJson
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("$apiUrl/sessions/$sid/reflection")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $userToken")
                .addHeader("X-User-ID", userId)
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        fun factory(application: Application, sessionId: String, userId: String, apiUrl: String, userToken: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReflectionViewModel(application, sessionId, userId, apiUrl, userToken) as T
            }
    }
}
