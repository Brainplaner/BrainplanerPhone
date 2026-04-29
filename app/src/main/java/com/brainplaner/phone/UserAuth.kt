package com.brainplaner.phone

import android.content.Context
import java.util.UUID

/**
 * Per-tester auth state for beta.
 *
 * After redeeming an invite code via POST /beta/claim, we store the
 * returned access token (a Supabase-compatible JWT) plus the user_id
 * it authenticates. The token is sent as `Authorization: Bearer <jwt>`
 * on every request to FastAPI; the user_id is kept around for UI and
 * for legacy code paths that still want it.
 *
 * device_id is generated once on first launch and persists across
 * reclaims so the server can recognise the same device on reinstall.
 */
object UserAuth {
    private const val PREFS_NAME = "brainplaner_user"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
    private const val KEY_DEVICE_ID = "device_id"

    fun getUserId(context: Context): String? =
        prefs(context).getString(KEY_USER_ID, null)

    fun getAccessToken(context: Context): String? =
        prefs(context).getString(KEY_ACCESS_TOKEN, null)

    fun getTokenExpiresAt(context: Context): Long =
        prefs(context).getLong(KEY_TOKEN_EXPIRES_AT, 0L)

    /** Stable per-install device id, generated lazily on first read. */
    fun getDeviceId(context: Context): String {
        val p = prefs(context)
        val existing = p.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val fresh = UUID.randomUUID().toString()
        p.edit().putString(KEY_DEVICE_ID, fresh).apply()
        return fresh
    }

    fun saveSession(context: Context, userId: String, accessToken: String, expiresAt: Long) {
        prefs(context).edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_TOKEN_EXPIRES_AT, expiresAt)
            .apply()
    }

    /** Legacy single-field setter. Retained so unrelated callers keep compiling. */
    fun saveUserId(context: Context, userId: String) {
        prefs(context).edit().putString(KEY_USER_ID, userId).apply()
    }

    fun clearUserId(context: Context) {
        prefs(context).edit()
            .remove(KEY_USER_ID)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .apply()
    }

    fun isLoggedIn(context: Context): Boolean {
        val p = prefs(context)
        return p.getString(KEY_USER_ID, null) != null &&
            p.getString(KEY_ACCESS_TOKEN, null) != null
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
