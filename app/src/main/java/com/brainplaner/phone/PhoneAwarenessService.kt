package com.brainplaner.phone

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class PhoneAwarenessService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // Cloud API Configuration
    private val CLOUD_API_URL = "https://brainplaner-api-beta.onrender.com"
    // For Render deployment, use: "https://brainplaner-api-beta.onrender.com"

    // Supabase for detailed event logging
    private val SUPABASE_URL = "https://mhmmiaqaqoddlkyziati.supabase.co"
    private val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1obW1pYXFhcW9kZGxreXppYXRpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njc5NjQ2NDcsImV4cCI6MjA4MzU0MDY0N30.zN5bUHUWDqo2RASQkd-FQyTy01pwi_xFLVs2CpPZMXg"
    private val POLL_INTERVAL_MS = 5000L // Poll every 5 seconds

    // Per-user JWT (Supabase-compatible) minted by POST /beta/claim and stored
    // in UserAuth. Used for both FastAPI calls and direct-Supabase REST calls
    // so RLS resolves auth.uid() to the right user. Falls back to the anon key
    // for users who haven't redeemed an invite yet (legacy bypass mode).
    private var loggedAnonFallback = false
    private fun userToken(): String {
        val token = UserAuth.getAccessToken(this)
        if (token != null) return token
        if (!loggedAnonFallback) {
            android.util.Log.w(
                "PhoneAwareness",
                "No per-user JWT; using anon key. Tester must redeem an invite for RLS to scope correctly."
            )
            loggedAnonFallback = true
        }
        return SUPABASE_ANON_KEY
    }

    // Live event batching
    private val liveEventBatch = mutableListOf<Map<String, Any>>()
    private val BATCH_SIZE = 10 // Send batch after 10 events
    private val BATCH_TIMEOUT_MS = 30000L // Or after 30 seconds
    private var lastBatchSubmitTime = System.currentTimeMillis()

    // Get user_id from stored preferences
    private var USER_ID: String? = null

    private var sessionId: String? = null
    private var unlockCount = 0
    private var notificationCount = 0
    private var screenOnSeconds = 0
    private var screenOnStartTime: Long? = null
    private var lastScreenState = "unknown"
    private var pollingJob: Job? = null
    private var isAutoDetected = false
    private var isSessionPaused = false  // Synced from LocalStore

    private val pauseResumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.brainplaner.phone.SESSION_PAUSED" -> {
                    isSessionPaused = true
                    android.util.Log.i("PhoneAwareness", "Received pause signal, event logging suspended")
                    updateNotification()
                }
                "com.brainplaner.phone.SESSION_RESUMED" -> {
                    val resumedSessionId = intent.getStringExtra("session_id")
                    if (resumedSessionId != null && cooldownSessionId == resumedSessionId) {
                        // Recover from accidental cooldown trigger on a session that is actually resumed.
                        sessionId = resumedSessionId
                        cancelCooldown("resume broadcast for active session")
                    }
                    isSessionPaused = false
                    android.util.Log.i("PhoneAwareness", "Received resume signal, event logging resumed")
                    updateNotification()
                }
                BrainplanerNotificationListener.ACTION_NOTIFICATION_RECEIVED -> {
                    val sourcePackage = intent.getStringExtra("source_package") ?: "unknown"
                    if (isInCooldown) {
                        // Feeds the Mark 2023 self-initiated/external decomposition of
                        // cooldown unlocks in consolidation_compute.compute_consolidation_score
                        // breakdown — interpretive only, NEVER folded into CBI or the
                        // consolidation score formula (phone_usage.md backs the split,
                        // not weights).
                        cooldownNotificationCount++
                        android.util.Log.i("PhoneAwareness", "Cooldown: notification from $sourcePackage (count=$cooldownNotificationCount)")
                        logEvent("notification_received", mapOf(
                            "notification_count" to cooldownNotificationCount,
                            "source_package" to sourcePackage,
                            "phase" to "cooldown"
                        ))
                        updateNotification()
                    } else if (sessionId != null && !isSessionPaused) {
                        notificationCount++
                        android.util.Log.i("PhoneAwareness", "Event: notification from $sourcePackage (count=$notificationCount)")
                        logEvent("notification_received", mapOf(
                            "notification_count" to notificationCount,
                            "source_package" to sourcePackage
                        ))
                        updateNotification()
                    }
                }
                "com.brainplaner.phone.SESSION_ENDED" -> {
                    // User pressed Stop. Clear active state so events stop logging,
                    // but DO NOT start cooldown — that waits until reflection is closed.
                    val endedSessionId = intent.getStringExtra("session_id")
                    if (endedSessionId != null && sessionId == endedSessionId) {
                        android.util.Log.i("PhoneAwareness", "Received session_ended for $endedSessionId — clearing active state, deferring cooldown until reflection closes")
                        sessionId = null
                        isAutoDetected = false
                        isSessionPaused = false
                        screenOnStartTime = null
                        updateNotification()
                    }
                }
            }
        }
    }

    // Cooldown tracking — continues after session ends to measure post-session behavior
    private val COOLDOWN_GRACE_PERIOD_MS = 10_000L // Wait before counting cooldown metrics
    private val COOLDOWN_DURATION_MS = 15 * 60 * 1000L // 15 minutes
    private val COOLDOWN_TEST_MODE = false
    private var isInCooldown = false
    private var cooldownSessionId: String? = null  // session that triggered cooldown
    private var cooldownGraceStartTime: Long? = null
    private var cooldownStartTime: Long? = null
    private var cooldownUnlockCount = 0
    private var cooldownNotificationCount = 0
    private var cooldownScreenOnSeconds = 0
    private var cooldownTimerJob: Job? = null
    private var timeToFirstPickupSeconds: Double? = null  // null = no pickup yet

    private fun isCooldownPending(): Boolean = cooldownSessionId != null && !isInCooldown

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    if (isCooldownPending()) {
                        lastScreenState = "on"
                        screenOnStartTime = null
                        updateNotification()
                        return
                    }
                    if (isInCooldown) {
                        // Cooldown phase: track separately
                        if (lastScreenState == "off") {
                            cooldownUnlockCount++
                            if (timeToFirstPickupSeconds == null) {
                                cooldownStartTime?.let { start ->
                                    timeToFirstPickupSeconds = (System.currentTimeMillis() - start) / 1000.0
                                    android.util.Log.i("PhoneAwareness", "Cooldown: first pickup at ${timeToFirstPickupSeconds}s")
                                }
                            }
                            android.util.Log.i("PhoneAwareness", "Cooldown: unlock (count=$cooldownUnlockCount)")
                            logEvent("unlock", mapOf("unlock_count" to cooldownUnlockCount, "phase" to "cooldown"))
                        }
                        lastScreenState = "on"
                        screenOnStartTime = System.currentTimeMillis()
                        updateNotification()
                        logEvent("screen_on", mapOf("phase" to "cooldown"))
                    } else if (!isSessionPaused) {
                        // Active session phase: original behavior
                        if (lastScreenState == "off" && sessionId != null) {
                            unlockCount++
                            android.util.Log.i("PhoneAwareness", "Event: unlock from screen_on (count=$unlockCount)")
                            logEvent("unlock", mapOf("unlock_count" to unlockCount))
                        }
                        lastScreenState = "on"
                        screenOnStartTime = System.currentTimeMillis()
                        updateNotification()
                        android.util.Log.i("PhoneAwareness", "Event: screen_on")
                        logEvent("screen_on")
                    } else {
                        lastScreenState = "on"
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    if (isCooldownPending()) {
                        lastScreenState = "off"
                        screenOnStartTime = null
                        updateNotification()
                        return
                    }
                    lastScreenState = "off"
                    screenOnStartTime?.let {
                        val duration = (System.currentTimeMillis() - it) / 1000
                        if (isInCooldown) {
                            cooldownScreenOnSeconds += duration.toInt()
                            android.util.Log.i("PhoneAwareness", "Cooldown: screen on for ${duration}s, total: ${cooldownScreenOnSeconds}s")
                        } else if (!isSessionPaused) {
                            screenOnSeconds += duration.toInt()
                            android.util.Log.i("PhoneAwareness", "Screen was on for ${duration}s, total: ${screenOnSeconds}s")
                        }
                    }
                    screenOnStartTime = null
                    updateNotification()
                    if (isInCooldown) {
                        logEvent("screen_off", mapOf("phase" to "cooldown"))
                    } else if (!isSessionPaused) {
                        android.util.Log.i("PhoneAwareness", "Event: screen_off")
                        logEvent("screen_off")
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    if (isCooldownPending()) {
                        updateNotification()
                        return
                    }
                    if (isInCooldown) {
                        cooldownUnlockCount++
                        if (timeToFirstPickupSeconds == null) {
                            cooldownStartTime?.let { start ->
                                timeToFirstPickupSeconds = (System.currentTimeMillis() - start) / 1000.0
                                android.util.Log.i("PhoneAwareness", "Cooldown: first pickup (USER_PRESENT) at ${timeToFirstPickupSeconds}s")
                            }
                        }
                        android.util.Log.i("PhoneAwareness", "Cooldown: unlock USER_PRESENT (count=$cooldownUnlockCount)")
                        logEvent("unlock", mapOf("unlock_count" to cooldownUnlockCount, "phase" to "cooldown"))
                        updateNotification()
                    } else if (sessionId != null && !isSessionPaused) {
                        unlockCount++
                        android.util.Log.i("PhoneAwareness", "Event: unlock from USER_PRESENT (count=$unlockCount)")
                        logEvent("unlock", mapOf("unlock_count" to unlockCount))
                        updateNotification()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Load user_id from SharedPreferences - but don't fail if missing
        // The service may start before user logs in, so we'll check again in onStartCommand
        val userId = UserAuth.getUserId(this)
        if (userId != null) {
            USER_ID = userId
            android.util.Log.i("PhoneAwareness", "Service onCreate() for user: $USER_ID")
        } else {
            android.util.Log.w("PhoneAwareness", "Service onCreate() but no user logged in yet")
            // Don't initialize USER_ID yet, will be set later
        }

        // Load pause state from LocalStore
        syncPauseStateFromLocalStore()

        // Register screen state listeners
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        // Register broadcast receiver for pause/resume/ended signals + notification listener events
        val pauseResumeFilter = IntentFilter().apply {
            addAction("com.brainplaner.phone.SESSION_PAUSED")
            addAction("com.brainplaner.phone.SESSION_RESUMED")
            addAction("com.brainplaner.phone.SESSION_ENDED")
            addAction(BrainplanerNotificationListener.ACTION_NOTIFICATION_RECEIVED)
        }

        // Android 13+ requires explicit export flag for broadcast receivers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(pauseResumeReceiver, pauseResumeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
            registerReceiver(pauseResumeReceiver, pauseResumeFilter)
        }

        createNotificationChannel()

        android.util.Log.i("PhoneAwareness", "=== Service onCreate() complete ===")

        // Note: startForeground() and startPolling() are called in onStartCommand()
        // which always runs after onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check if user is logged in - if not, don't restart automatically
        if (USER_ID == null) {
            val userId = UserAuth.getUserId(this)
            if (userId != null) {
                USER_ID = userId
                android.util.Log.i("PhoneAwareness", "User logged in, now tracking: $USER_ID")
            } else {
                android.util.Log.w("PhoneAwareness", "No user logged in, service will stop")
                stopSelf()
                return START_NOT_STICKY // Don't restart if no user
            }
        }

        val newSessionId = intent?.getStringExtra("session_id")
        val startCooldownSessionId = intent?.getStringExtra("start_cooldown_session_id")
        val enablePolling = intent?.getBooleanExtra("enable_polling", false) ?: false

        android.util.Log.i(
            "PhoneAwareness",
            "onStartCommand(session_id=$newSessionId, cooldown_session_id=$startCooldownSessionId, enable_polling=$enablePolling, intent=${intent != null}, user=$USER_ID)"
        )

        // ALWAYS become foreground - required on Android 8+ to prevent crash
        startForeground(NOTIFICATION_ID, buildNotification())

        if (startCooldownSessionId != null) {
            // Session ended on phone UI: keep service alive and transition to cooldown phase.
            sessionId = null
            isAutoDetected = false
            if ((cooldownSessionId != startCooldownSessionId) || cooldownTimerJob?.isActive != true) {
                startCooldown(startCooldownSessionId)
            } else {
                android.util.Log.i("PhoneAwareness", "Cooldown already active for session: $startCooldownSessionId")
            }
        } else if (newSessionId != null) {
            // Manual session start from phone UI.
            // Preserve counters when upgrading local session id -> cloud session id.
            val previousSessionId = sessionId
            val isUpgradeFromLocal = previousSessionId?.startsWith("local-") == true && !newSessionId.startsWith("local-")
            // Set sessionId first so finishCooldown() inside cancelCooldown() does not stopSelf().
            sessionId = newSessionId
            if (cooldownSessionId != null) {
                cancelCooldown("manual session start")
            }
            isAutoDetected = false
            isSessionPaused = false

            if (isUpgradeFromLocal) {
                android.util.Log.i("PhoneAwareness", "Upgraded local session to cloud id: $previousSessionId -> $newSessionId")
            } else {
                unlockCount = 0
                notificationCount = 0
                screenOnSeconds = 0
                screenOnStartTime = if (lastScreenState == "on") System.currentTimeMillis() else null
                android.util.Log.i("PhoneAwareness", "Started tracking new session: $newSessionId")
            }
        } else {
            // Polling mode (explicit or START_STICKY restart with null intent)
            isAutoDetected = true
            android.util.Log.i("PhoneAwareness", "Running in polling mode (foreground)")
        }

        // Ensure polling loop is always running
        if (pollingJob == null || pollingJob?.isActive != true) {
            android.util.Log.i("PhoneAwareness", "Starting/restarting polling loop")
            startPolling()
        } else {
            android.util.Log.d("PhoneAwareness", "Polling loop already active")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        pollingJob?.cancel()
        cooldownTimerJob?.cancel()

        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
            // Receiver might not be registered
        }

        try {
            unregisterReceiver(pauseResumeReceiver)
        } catch (_: Exception) {
            // Receiver might not be registered
        }

        // Submit metrics synchronously in background thread to avoid NetworkOnMainThreadException
        Thread {
            try {
                // If in cooldown, finish it now (submit what we have)
                if (isInCooldown) {
                    android.util.Log.i("PhoneAwareness", "Service destroyed during cooldown — submitting partial cooldown metrics")
                    finishCooldown()
                }

                // Flush any remaining live events in batch
                val flushSessionId = sessionId ?: cooldownSessionId
                flushSessionId?.let { sid ->
                    USER_ID?.let { uid ->
                        synchronized(liveEventBatch) {
                            if (liveEventBatch.isNotEmpty()) {
                                android.util.Log.i("PhoneAwareness", "Flushing remaining ${liveEventBatch.size} events on destroy")
                                submitLiveEventBatchSync(sid, uid)
                            }
                        }
                    }
                }

                submitPhoneMetricsBlocking()
            } catch (e: Exception) {
                android.util.Log.e("PhoneAwareness", "Error submitting metrics on destroy", e)
            }

            // Cleanup after metrics are sent
            try {
                serviceScope.cancel()
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            } catch (_: Exception) {
                // Ignore cleanup errors
            }
        }.start()
    }

    private fun submitLiveEventBatchSync(currentSessionId: String, userId: String) {
        if (liveEventBatch.isEmpty()) return

        val batchToSend = liveEventBatch.toList()
        liveEventBatch.clear()

        if (COOLDOWN_TEST_MODE) {
            android.util.Log.i("PhoneAwareness", "[TEST_MODE] Would sync-submit ${batchToSend.size} final events — skipped")
            return
        }

        try {
            val eventsJson = batchToSend.joinToString(",\n      ") { event ->
                val metadataJson = (event["metadata"] as? Map<*, *>)?.let { meta ->
                    if (meta.isEmpty()) "{}"
                    else "{" + meta.entries.joinToString(",") {
                        """"${it.key}":${if (it.value is String) "\"${it.value}\"" else it.value}"""
                    } + "}"
                } ?: "{}"

                """
                {
                    "event_type": "${event["event_type"]}",
                    "timestamp": "${event["timestamp"]}",
                    "device": "${event["device"]}",
                    "metadata": $metadataJson
                }
                """.trimIndent()
            }

            val json = """
                {
                    "events": [$eventsJson]
                }
            """.trimIndent()

            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$CLOUD_API_URL/sessions/$currentSessionId/phone-events")
                .post(body)
                .addHeader("Authorization", "Bearer ${userToken()}")
                .addHeader("X-User-ID", userId)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    android.util.Log.i("PhoneAwareness", "✓ Final batch submitted on destroy")
                } else {
                    android.util.Log.e("PhoneAwareness", "✗ Failed to submit final batch: ${response.code}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PhoneAwareness", "Error in submitLiveEventBatchSync", e)
        }
    }

    private fun submitPhoneMetricsBlocking() {
        val currentSessionId = sessionId ?: return
        val userId = USER_ID ?: run {
            android.util.Log.w("PhoneAwareness", "Cannot submit metrics, no user logged in")
            return
        }

        // Calculate final screen time if screen is currently on
        screenOnStartTime?.let {
            val duration = (System.currentTimeMillis() - it) / 1000
            screenOnSeconds += duration.toInt()
        }

        android.util.Log.i(
            "PhoneAwareness",
            "Submitting phone metrics: unlocks=$unlockCount, notifications=$notificationCount, screen_on=${screenOnSeconds}s"
        )

        if (COOLDOWN_TEST_MODE) {
            android.util.Log.i("PhoneAwareness", "[TEST_MODE] Would submit phone metrics — skipped")
            return
        }

        try {
            val json = """
                {
                  "phone_unlock_count": $unlockCount,
                  "phone_screen_on_seconds": $screenOnSeconds,
                  "phone_notification_count": $notificationCount
                }
            """.trimIndent()

            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$CLOUD_API_URL/sessions/$currentSessionId/phone")
                .post(body)
                .addHeader("Authorization", "Bearer ${userToken()}")
                .addHeader("X-User-ID", userId)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    android.util.Log.i("PhoneAwareness", "Phone metrics submitted successfully via Cloud API")
                    return // Success, no need for fallback
                } else {
                    val errorBody = response.body?.string() ?: ""
                    android.util.Log.e(
                        "PhoneAwareness",
                        "Failed to submit phone metrics via Cloud API: ${response.code} - $errorBody"
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PhoneAwareness", "Cloud API unreachable for phone metrics", e)
        }

        // Fallback: submit phone metrics directly to Supabase session_metrics table
        android.util.Log.i("PhoneAwareness", "Falling back to Supabase for phone metrics")
        submitPhoneMetricsToSupabase(currentSessionId, userId)
    }

    private fun submitPhoneMetricsToSupabase(currentSessionId: String, userId: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            val metrics = listOf(
                """{"session_id":"$currentSessionId","user_id":"$userId","metric_key":"phone_unlock_count","value_num":$unlockCount,"unit":"count","observed_ts":"$timestamp","created_at":"$timestamp"}""",
                """{"session_id":"$currentSessionId","user_id":"$userId","metric_key":"phone_screen_on_seconds","value_num":$screenOnSeconds,"unit":"seconds","observed_ts":"$timestamp","created_at":"$timestamp"}""",
                """{"session_id":"$currentSessionId","user_id":"$userId","metric_key":"phone_notification_count","value_num":$notificationCount,"unit":"count","observed_ts":"$timestamp","created_at":"$timestamp"}"""
            )

            val json = "[${metrics.joinToString(",")}]"
            val body = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/session_metrics")
                .post(body)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${userToken()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    android.util.Log.i("PhoneAwareness", "✓ Phone metrics submitted directly to Supabase")
                } else {
                    val errorBody = response.body?.string() ?: ""
                    android.util.Log.e("PhoneAwareness", "✗ Supabase fallback failed: ${response.code} - $errorBody")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PhoneAwareness", "Error in Supabase fallback for phone metrics", e)
        }
    }

    // ==================== Cooldown Phase ====================

    private fun startCooldown(completedSessionId: String) {
        if (cooldownSessionId == completedSessionId && cooldownTimerJob?.isActive == true) {
            android.util.Log.i("PhoneAwareness", "Cooldown already pending/active for session: $completedSessionId")
            return
        }

        isInCooldown = false
        cooldownSessionId = completedSessionId
        cooldownGraceStartTime = System.currentTimeMillis()
        cooldownStartTime = null
        cooldownUnlockCount = 0
        cooldownNotificationCount = 0
        cooldownScreenOnSeconds = 0
        screenOnStartTime = null
        timeToFirstPickupSeconds = null

        android.util.Log.i(
            "PhoneAwareness",
            "=== Cooldown armed for session $completedSessionId (grace=${COOLDOWN_GRACE_PERIOD_MS / 1000}s, duration=${COOLDOWN_DURATION_MS / 60000} min) ==="
        )
        updateNotification()

        // Start after grace period, then auto-stop after COOLDOWN_DURATION_MS.
        cooldownTimerJob?.cancel()
        cooldownTimerJob = serviceScope.launch {
            delay(COOLDOWN_GRACE_PERIOD_MS)
            isInCooldown = true
            cooldownStartTime = System.currentTimeMillis()
            cooldownGraceStartTime = null

            // If screen is currently on, start counting from activation time.
            if (lastScreenState == "on") {
                screenOnStartTime = System.currentTimeMillis()
            }

            android.util.Log.i("PhoneAwareness", "=== Cooldown started for session $completedSessionId (${COOLDOWN_DURATION_MS / 60000} min) ===")
            updateNotification()

            delay(COOLDOWN_DURATION_MS)
            android.util.Log.i("PhoneAwareness", "Cooldown timer expired for session $completedSessionId")
            finishCooldown()
        }
    }

    private fun finishCooldown() {
        val cdSessionId = cooldownSessionId ?: return
        val userId = USER_ID ?: return

        // Finalize screen-on time if screen is currently on
        screenOnStartTime?.let {
            val duration = (System.currentTimeMillis() - it) / 1000
            cooldownScreenOnSeconds += duration.toInt()
            screenOnStartTime = null
        }

        val actualCooldownSeconds = cooldownStartTime?.let {
            ((System.currentTimeMillis() - it) / 1000).toInt()
        } ?: (COOLDOWN_DURATION_MS / 1000).toInt()

        android.util.Log.i(
            "PhoneAwareness",
            "=== Cooldown finished: session=$cdSessionId, unlocks=$cooldownUnlockCount, " +
            "notifications=$cooldownNotificationCount, screen_on=${cooldownScreenOnSeconds}s, " +
            "first_pickup=${timeToFirstPickupSeconds}s, duration=${actualCooldownSeconds}s ==="
        )

        // Flush remaining events
        synchronized(liveEventBatch) {
            if (liveEventBatch.isNotEmpty()) {
                submitLiveEventBatchSync(cdSessionId, userId)
            }
        }

        // Submit cooldown metrics to Cloud API
        submitCooldownMetrics(
            sessionId = cdSessionId,
            userId = userId,
            unlockCount = cooldownUnlockCount,
            notificationCount = cooldownNotificationCount,
            screenOnSeconds = cooldownScreenOnSeconds,
            cooldownDurationSeconds = actualCooldownSeconds,
            timeToFirstPickupSeconds = timeToFirstPickupSeconds
        )

        // Reset cooldown state
        isInCooldown = false
        cooldownSessionId = null
        cooldownGraceStartTime = null
        cooldownStartTime = null
        cooldownUnlockCount = 0
        cooldownNotificationCount = 0
        cooldownScreenOnSeconds = 0
        screenOnStartTime = null
        cooldownTimerJob = null
        timeToFirstPickupSeconds = null
        unlockCount = 0
        notificationCount = 0
        updateNotification()

        // Keep the service alive in polling mode after cooldown so the next session
        // (especially one started from PC) is observed from "active" -> "completed"
        // and triggers a fresh cooldown. Stopping here was causing missed cooldowns.
        isAutoDetected = true
        updateNotification()
    }

    private fun cancelCooldown(reason: String) {
        // No cooldown in any state (active or pending grace)
        if (cooldownSessionId == null) return

        if (isInCooldown) {
            // Active cooldown interrupted (e.g. new session started within 15 min).
            // Submit partial metrics instead of silently dropping them.
            android.util.Log.i("PhoneAwareness", "Cancelling active cooldown: $reason — submitting partial metrics")
            finishCooldown()
            return
        }

        // Pending grace period — too little data to be meaningful, just clear state.
        android.util.Log.i("PhoneAwareness", "Cancelling pending cooldown (grace): $reason")
        cooldownTimerJob?.cancel()
        cooldownTimerJob = null
        cooldownSessionId = null
        cooldownGraceStartTime = null
        cooldownStartTime = null
        cooldownUnlockCount = 0
        cooldownNotificationCount = 0
        cooldownScreenOnSeconds = 0
        timeToFirstPickupSeconds = null
        updateNotification()
    }

    private fun submitCooldownMetrics(
        sessionId: String,
        userId: String,
        unlockCount: Int,
        notificationCount: Int,
        screenOnSeconds: Int,
        cooldownDurationSeconds: Int,
        timeToFirstPickupSeconds: Double?
    ) {
        if (COOLDOWN_TEST_MODE) {
            android.util.Log.i(
                "PhoneAwareness",
                "[TEST_MODE] Would submit cooldown: session=$sessionId, unlocks=$unlockCount, " +
                "notifications=$notificationCount, screen=${screenOnSeconds}s, " +
                "duration=${cooldownDurationSeconds}s, first_pickup=${timeToFirstPickupSeconds}s — skipped (no DB write)"
            )
            return
        }
        // Try Cloud API first
        try {
            val firstPickupJson = timeToFirstPickupSeconds?.let { "$it" } ?: "null"
            val json = """
                {
                  "cooldown_unlock_count": $unlockCount,
                  "cooldown_screen_on_seconds": $screenOnSeconds,
                  "cooldown_duration_seconds": $cooldownDurationSeconds,
                  "time_to_first_pickup_seconds": $firstPickupJson,
                  "cooldown_notification_count": $notificationCount
                }
            """.trimIndent()

            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$CLOUD_API_URL/sessions/$sessionId/cooldown")
                .post(body)
                .addHeader("Authorization", "Bearer ${userToken()}")
                .addHeader("X-User-ID", userId)
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    android.util.Log.i("PhoneAwareness", "✓ Cooldown metrics submitted via Cloud API")
                    return
                } else {
                    val errorBody = response.body?.string() ?: ""
                    android.util.Log.e("PhoneAwareness", "✗ Cloud API cooldown failed: ${response.code} - $errorBody")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PhoneAwareness", "Cloud API unreachable for cooldown metrics", e)
        }

        // Fallback: submit cooldown metrics directly to Supabase session_metrics
        android.util.Log.i("PhoneAwareness", "Falling back to Supabase for cooldown metrics")
        submitCooldownMetricsToSupabase(sessionId, userId, unlockCount, notificationCount, screenOnSeconds, cooldownDurationSeconds, timeToFirstPickupSeconds)
    }

    private fun submitCooldownMetricsToSupabase(
        sessionId: String,
        userId: String,
        unlockCount: Int,
        notificationCount: Int,
        screenOnSeconds: Int,
        cooldownDurationSeconds: Int,
        timeToFirstPickupSeconds: Double?
    ) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date())

            // Compute derived metrics locally (mirrors Cloud API logic)
            val durationMinutes = cooldownDurationSeconds / 60.0
            val screenOnRatio = if (cooldownDurationSeconds > 0)
                (screenOnSeconds.toDouble() / cooldownDurationSeconds).coerceIn(0.0, 1.0) else 0.0
            val unlockRatePerMin = if (durationMinutes > 0)
                unlockCount / durationMinutes else 0.0

            // Compute behavior index (same formula as Cloud API)
            val unlocksPerHour = unlockRatePerMin * 60
            val unlockScore = (unlocksPerHour / 20.0).coerceIn(0.0, 1.0)
            val pickupScore = if (timeToFirstPickupSeconds == null) 0.0
                else (1.0 - (timeToFirstPickupSeconds / 600.0)).coerceIn(0.0, 1.0)
            val behaviorIndex = ((0.40 * unlockScore + 0.30 * screenOnRatio + 0.30 * pickupScore) * 100)
                .coerceIn(0.0, 100.0)
            val label = when {
                behaviorIndex < 15 -> "strong_recovery"
                behaviorIndex < 35 -> "normal_recovery"
                behaviorIndex < 60 -> "moderate_fatigue"
                else -> "high_fatigue"
            }

            val firstPickupJson = timeToFirstPickupSeconds?.let { "$it" } ?: "null"
            val json = """
                {
                  "session_id": "$sessionId",
                  "user_id": "$userId",
                  "cooldown_unlock_count": $unlockCount,
                  "cooldown_screen_on_seconds": $screenOnSeconds,
                  "cooldown_duration_seconds": $cooldownDurationSeconds,
                  "time_to_first_pickup_seconds": $firstPickupJson,
                  "cooldown_screen_on_ratio": ${"%.4f".format(screenOnRatio)},
                  "cooldown_unlock_rate_per_min": ${"%.4f".format(unlockRatePerMin)},
                  "cooldown_behavior_index": ${"%.1f".format(behaviorIndex)},
                  "cooldown_label": "$label",
                  "created_at": "$timestamp"
                }
            """.trimIndent()

            val body = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/session_cooldowns")
                .post(body)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${userToken()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal,resolution=merge-duplicates")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    android.util.Log.i("PhoneAwareness", "✓ Cooldown metrics submitted to Supabase session_cooldowns")
                } else {
                    val errorBody = response.body?.string() ?: ""
                    android.util.Log.e("PhoneAwareness", "✗ Supabase cooldown fallback failed: ${response.code} - $errorBody")
                }
            }

            // Dual-write cooldown_notification_count to session_metrics so the
            // /backfill/cooldowns route can recover it on legacy rows. The primary
            // path writes it to session_cooldowns.cooldown_notification_count; the
            // server uses it only for the Mark 2023 unlock decomposition, never
            // for the CBI or consolidation_score formula.
            val metricRow = """[{"session_id":"$sessionId","user_id":"$userId","metric_key":"cooldown_notification_count","value_num":$notificationCount,"unit":"count","observed_ts":"$timestamp","created_at":"$timestamp"}]"""
            val metricBody = metricRow.toRequestBody("application/json".toMediaType())
            val metricRequest = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/session_metrics")
                .post(metricBody)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${userToken()}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .build()
            client.newCall(metricRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    android.util.Log.e("PhoneAwareness", "✗ Supabase cooldown_notification_count metric failed: ${response.code} - $errorBody")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PhoneAwareness", "Error in Supabase cooldown fallback", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val cooldownPrefs by lazy {
        getSharedPreferences("phone_cooldown_state", Context.MODE_PRIVATE)
    }

    private fun wasCooldownHandled(sessionId: String): Boolean {
        return cooldownPrefs.getStringSet("handled_session_ids", emptySet())
            ?.contains(sessionId) == true
    }

    private fun markCooldownHandled(sessionId: String) {
        val current = cooldownPrefs.getStringSet("handled_session_ids", emptySet()) ?: emptySet()
        // Cap to last 50 to avoid unbounded growth
        val updated = (current + sessionId).toList().takeLast(50).toSet()
        cooldownPrefs.edit().putStringSet("handled_session_ids", updated).apply()
    }

    private fun isRecentlyEnded(endTs: String?): Boolean {
        if (endTs.isNullOrBlank() || endTs == "null") return false
        // Trigger catch-up cooldown only if session ended within the cooldown window —
        // older sessions are not worth a partial post-session measurement.
        return try {
            val parsed = parseIsoTimestamp(endTs) ?: return false
            val ageMs = System.currentTimeMillis() - parsed
            ageMs in 0..COOLDOWN_DURATION_MS
        } catch (e: Exception) {
            android.util.Log.w("PhoneAwareness", "Could not parse end_ts: $endTs", e)
            false
        }
    }

    private fun parseIsoTimestamp(ts: String): Long? {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                return sdf.parse(ts)?.time
            } catch (_: Exception) { /* try next */ }
        }
        return null
    }

    private fun syncPauseStateFromLocalStore() {
        try {
            val activeSession = LocalStore.getActiveSession(this)
            if (activeSession != null && activeSession.id == sessionId) {
                isSessionPaused = activeSession.isPaused
                android.util.Log.d("PhoneAwareness", "Synced pause state from LocalStore: isPaused=$isSessionPaused, sessionId=${activeSession.id}")
            } else if (sessionId == null && activeSession != null) {
                // Service restarted, load session from LocalStore
                sessionId = activeSession.id
                isSessionPaused = activeSession.isPaused
                android.util.Log.i("PhoneAwareness", "Restored session from LocalStore: id=$sessionId, isPaused=$isSessionPaused")
            }
        } catch (e: Exception) {
            android.util.Log.e("PhoneAwareness", "Error syncing pause state from LocalStore", e)
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        android.util.Log.i("PhoneAwareness", "=== Starting polling loop (interval=${POLL_INTERVAL_MS}ms, user=$USER_ID) ===")
        pollingJob = serviceScope.launch {
            var pollCount = 0
            while (isActive) {
                pollCount++
                try {
                    android.util.Log.d("PhoneAwareness", "Poll #$pollCount starting...")
                    pollSupabaseSession()
                } catch (e: Exception) {
                    android.util.Log.e("PhoneAwareness", "Poll #$pollCount error: ${e.message}", e)
                }
                delay(POLL_INTERVAL_MS)
            }
            android.util.Log.w("PhoneAwareness", "Polling loop ended after $pollCount polls")
        }
    }

    private suspend fun pollSupabaseSession() = withContext(Dispatchers.IO) {
        // Don't poll if no user is logged in
        val userId = USER_ID ?: return@withContext

        try {
            // Query the most recent session for this user (ANY status, sorted by start_ts)
            // We need to see both active and completed to properly detect start/stop
            val url = "$SUPABASE_URL/rest/v1/sessions?user_id=eq.$userId&order=start_ts.desc&limit=1&select=id,status,start_ts,end_ts"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${userToken()}")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("PhoneAwareness", "Poll HTTP error: ${response.code}")
                    return@withContext
                }

                val responseBody = response.body?.string() ?: run {
                    android.util.Log.w("PhoneAwareness", "Poll: empty response body")
                    return@withContext
                }

                android.util.Log.d("PhoneAwareness", "Poll response (${responseBody.length} chars): ${responseBody.take(200)}")

                // Empty array = no sessions at all
                if (responseBody.trim() == "[]" || responseBody.trim() == "") {
                    android.util.Log.d("PhoneAwareness", "Poll: no sessions found for user")
                    return@withContext
                }

                // Parse response (simplified JSON parsing)
                val idMatch = Regex(""""id":"([^"]+)"""").find(responseBody)
                val statusMatch = Regex(""""status":"([^"]+)"""").find(responseBody)
                val endTsMatch = Regex(""""end_ts":"([^"]+)"""").find(responseBody)

                val remoteId = idMatch?.groupValues?.get(1)
                val remoteStatus = statusMatch?.groupValues?.get(1)
                val remoteEndTs = endTsMatch?.groupValues?.get(1)

                if (remoteId == null || remoteStatus == null) {
                    android.util.Log.w("PhoneAwareness", "Poll: could not parse response: $responseBody")
                    return@withContext
                }

                android.util.Log.d("PhoneAwareness", "Poll: remote=$remoteId/$remoteStatus, local=$sessionId, cooldown=$isInCooldown/$cooldownSessionId")

                // Keep paused flag aligned with persisted local-first state.
                syncPauseStateFromLocalStore()

                // Sync logic: match Supabase state
                when {
                    remoteStatus == "active" && isInCooldown && cooldownSessionId == remoteId -> {
                        // Session resumed while cooldown was active (e.g. stale poll race): recover active tracking.
                        sessionId = remoteId
                        isAutoDetected = false
                        isSessionPaused = false
                        cancelCooldown("remote session is active again")
                    }
                    remoteStatus == "active" && sessionId == remoteId && isSessionPaused -> {
                        // Session resumed remotely — clear paused flag, keep counters intact
                        android.util.Log.i("PhoneAwareness", "Session $remoteId resumed remotely")
                        isSessionPaused = false
                        updateNotification()
                    }
                    remoteStatus == "active" && sessionId != remoteId
                            && LocalStore.getPendingReflectionRouteSessionId(this@PhoneAwarenessService) == remoteId -> {
                        // Session was just stopped locally; cloud /end hasn't synced yet, so the
                        // poll still reports "active". Don't re-track — reflection is pending.
                        android.util.Log.d("PhoneAwareness", "Ignoring stale active poll for $remoteId — reflection pending")
                    }
                    remoteStatus == "active" && sessionId != remoteId -> {
                        // New session started remotely (by PC) or detected on startup
                        android.util.Log.i("PhoneAwareness", "Auto-detected new session: $remoteId (was: $sessionId)")
                        // Set sessionId first so finishCooldown() inside cancelCooldown() does not stopSelf().
                        sessionId = remoteId
                        if (cooldownSessionId != null && cooldownSessionId != remoteId) {
                            cancelCooldown("new active session detected")
                        }
                        isAutoDetected = true
                        isSessionPaused = false
                        unlockCount = 0
                        notificationCount = 0
                        screenOnSeconds = 0
                        screenOnStartTime = if (lastScreenState == "on") System.currentTimeMillis() else null

                        // Update notification with new session info
                        updateNotification()

                        // Notify MainActivity if possible
                        sendBroadcast(Intent("com.brainplaner.phone.SESSION_AUTO_STARTED").apply {
                            setPackage(packageName)
                            putExtra("session_id", remoteId)
                        })
                    }
                    remoteStatus == "paused" && sessionId != null && sessionId == remoteId && !isSessionPaused -> {
                        // Session paused — suspend event counting but do NOT start cooldown
                        android.util.Log.i("PhoneAwareness", "Session $sessionId paused remotely — suspending event tracking")
                        isSessionPaused = true
                        updateNotification()
                    }
                    remoteStatus != "active" && remoteStatus != "paused" && sessionId != null && sessionId == remoteId && !isInCooldown -> {
                        // Session truly ended (completed/cancelled).
                        val completedSessionId = sessionId!!
                        val pendingReflection = LocalStore.getPendingReflectionRouteSessionId(this@PhoneAwarenessService)

                        sessionId = null  // no longer an active session
                        isAutoDetected = false
                        isSessionPaused = false
                        screenOnStartTime = null

                        if (pendingReflection == completedSessionId) {
                            // Reflection still open — cooldown will start when reflection closes.
                            android.util.Log.i("PhoneAwareness", "Session completed: $completedSessionId — deferring cooldown until reflection is submitted")
                            updateNotification()
                        } else {
                            android.util.Log.i("PhoneAwareness", "Session completed: $completedSessionId — entering cooldown phase (${ COOLDOWN_DURATION_MS / 60000 } min)")
                            startCooldown(completedSessionId)
                            markCooldownHandled(completedSessionId)
                        }

                        sendBroadcast(Intent("com.brainplaner.phone.SESSION_AUTO_STOPPED").apply {
                            setPackage(packageName)
                        })
                    }
                    // Catch-up: session we never observed active is now ended recently
                    // (e.g. started + ended on PC while phone service was dead, or doze gap).
                    // Only trigger if it hasn't already been processed for cooldown.
                    remoteStatus != "active" && remoteStatus != "paused"
                            && sessionId == null
                            && cooldownSessionId == null
                            && !wasCooldownHandled(remoteId)
                            && isRecentlyEnded(remoteEndTs) -> {
                        val pendingReflection = LocalStore.getPendingReflectionRouteSessionId(this@PhoneAwarenessService)
                        if (pendingReflection == remoteId) {
                            android.util.Log.i(
                                "PhoneAwareness",
                                "Catch-up: completed session $remoteId has pending reflection — deferring cooldown"
                            )
                        } else {
                            android.util.Log.i(
                                "PhoneAwareness",
                                "Catch-up: completed session $remoteId never observed active — starting cooldown anyway (end_ts=$remoteEndTs)"
                            )
                            startCooldown(remoteId)
                            markCooldownHandled(remoteId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PhoneAwareness", "Poll failed", e)
        }
    }

    private fun logEvent(eventType: String, metadata: Map<String, Any> = emptyMap()) {
        // During cooldown, use the cooldown session ID
        val currentSessionId = if (isInCooldown) cooldownSessionId else sessionId
        if (currentSessionId == null) {
            android.util.Log.d("PhoneAwareness", "Skipping event '$eventType' (no sessionId yet)")
            return
        }

        val userId = USER_ID ?: run {
            android.util.Log.w("PhoneAwareness", "Cannot log event, no user logged in")
            return
        }

        // Create event for batch
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val event = mapOf(
            "event_type" to eventType,
            "timestamp" to timestamp,
            "device" to "phone",
            "metadata" to metadata
        )

        synchronized(liveEventBatch) {
            liveEventBatch.add(event)
            android.util.Log.d("PhoneAwareness", "Queued event: $eventType (batch size: ${liveEventBatch.size})")

            // Submit batch if size threshold reached or timeout exceeded
            val shouldSubmit = liveEventBatch.size >= BATCH_SIZE ||
                    (System.currentTimeMillis() - lastBatchSubmitTime) > BATCH_TIMEOUT_MS

            if (shouldSubmit) {
                submitLiveEventBatch(currentSessionId, userId)
            }
        }

        // Also log to Supabase for backup/debugging
        logEventToSupabase(currentSessionId, eventType, timestamp, metadata)
    }

    private fun submitLiveEventBatch(currentSessionId: String, userId: String) {
        if (liveEventBatch.isEmpty()) return

        val batchToSend = liveEventBatch.toList()
        liveEventBatch.clear()
        lastBatchSubmitTime = System.currentTimeMillis()

        if (COOLDOWN_TEST_MODE) {
            android.util.Log.i("PhoneAwareness", "[TEST_MODE] Would submit ${batchToSend.size} live events — skipped")
            return
        }

        android.util.Log.i("PhoneAwareness", "Submitting batch of ${batchToSend.size} phone events to Cloud API")

        serviceScope.launch(Dispatchers.IO) {
            try {
                // Build JSON manually to ensure correct format
                val eventsJson = batchToSend.joinToString(",\n      ") { event ->
                    val metadataJson = (event["metadata"] as? Map<*, *>)?.let { meta ->
                        if (meta.isEmpty()) "{}"
                        else "{" + meta.entries.joinToString(",") {
                            """"${it.key}":${if (it.value is String) "\"${it.value}\"" else it.value}"""
                        } + "}"
                    } ?: "{}"

                    """
                    {
                        "event_type": "${event["event_type"]}",
                        "timestamp": "${event["timestamp"]}",
                        "device": "${event["device"]}",
                        "metadata": $metadataJson
                    }
                    """.trimIndent()
                }

                val json = """
                    {
                        "events": [$eventsJson]
                    }
                """.trimIndent()

                val body = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$CLOUD_API_URL/sessions/$currentSessionId/phone-events")
                    .post(body)
                    .addHeader("Authorization", "Bearer ${userToken()}")
                    .addHeader("X-User-ID", userId)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        android.util.Log.i("PhoneAwareness", "✓ Live events batch submitted successfully: $responseBody")
                    } else {
                        val errorBody = response.body?.string() ?: ""
                        android.util.Log.e(
                            "PhoneAwareness",
                            "✗ Failed to submit live events batch: ${response.code} - $errorBody"
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PhoneAwareness", "Error submitting live events batch", e)
            }
        }
    }

    private fun logEventToSupabase(currentSessionId: String, eventType: String, timestamp: String, metadata: Map<String, Any>) {
        if (COOLDOWN_TEST_MODE) {
            android.util.Log.d("PhoneAwareness", "[TEST_MODE] Would log $eventType to Supabase — skipped")
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            try {
                val metadataJson = if (metadata.isEmpty()) {
                    "{}"
                } else {
                    "{" + metadata.entries.joinToString(",") {
                        """"${it.key}":${if (it.value is String) "\"${it.value}\"" else it.value}"""
                    } + "}"
                }

                val json = """
                    {
                      "session_id": "$currentSessionId",
                      "event_type": "$eventType",
                      "timestamp": "$timestamp",
                      "device": "phone",
                      "metadata": $metadataJson
                    }
                """.trimIndent()

                val body = json.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/phone_events_live?select=id")
                    .post(body)
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer ${userToken()}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "No error body"
                        android.util.Log.e("PhoneAwareness", "Failed to log $eventType to Supabase: ${response.code} - $errorBody")
                    } else {
                        val responseBody = response.body?.string()
                        android.util.Log.d(
                            "PhoneAwareness",
                            "Successfully logged $eventType (HTTP ${response.code})${if (!responseBody.isNullOrBlank()) ": $responseBody" else ""}"
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PhoneAwareness", "Error logging event: $eventType", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Brainplaner Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Phone awareness tracking for Brainplaner sessions"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isInCooldown) {
            "Brainplaner Cooldown"
        } else if (isCooldownPending()) {
            "Brainplaner Cooldown Starting"
        } else if (isSessionPaused) {
            "Brainplaner Tracking Paused"
        } else if (sessionId != null) {
            "Brainplaner Tracking Active" + if (isAutoDetected) " (Auto)" else ""
        } else {
            "Brainplaner Monitoring"
        }

        val text = if (isInCooldown) {
            val elapsed = cooldownStartTime?.let { (System.currentTimeMillis() - it) / 60000 } ?: 0
            val remaining = (COOLDOWN_DURATION_MS / 60000) - elapsed
            "Post-session tracking: ${remaining}min left | Unlocks: $cooldownUnlockCount"
        } else if (isCooldownPending()) {
            val elapsedMs = cooldownGraceStartTime?.let { System.currentTimeMillis() - it } ?: 0L
            val remainingSeconds = ((COOLDOWN_GRACE_PERIOD_MS - elapsedMs).coerceAtLeast(0L) + 999L) / 1000L
            "Cooldown starts in ${remainingSeconds}s | Put phone away for better recovery"
        } else if (isSessionPaused) {
            "Session paused | Unlocks so far: $unlockCount"
        } else if (sessionId != null) {
            "Unlocks: $unlockCount | Screen: $lastScreenState"
        } else {
            "Waiting for session..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun updateNotification() {
        if (!canPostNotifications()) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val CHANNEL_ID = "brainplaner_tracking"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, sessionId: String) {
            val intent = Intent(context, PhoneAwarenessService::class.java).apply {
                putExtra("session_id", sessionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startPollingMode(context: Context) {
            val intent = Intent(context, PhoneAwarenessService::class.java).apply {
                putExtra("enable_polling", true)
            }
            // Must use startForegroundService on Android 8+ since onStartCommand calls startForeground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startCooldownForSession(context: Context, sessionId: String) {
            val intent = Intent(context, PhoneAwarenessService::class.java).apply {
                putExtra("start_cooldown_session_id", sessionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PhoneAwarenessService::class.java))
        }
    }
}
