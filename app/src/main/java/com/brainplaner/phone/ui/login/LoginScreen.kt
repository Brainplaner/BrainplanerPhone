package com.brainplaner.phone.ui.login

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.brainplaner.phone.UserAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Result handed back to MainActivity after a successful invite claim.
 */
data class ClaimedSession(
    val userId: String,
    val accessToken: String,
    val expiresAt: Long,
)

@Composable
fun LoginScreen(
    apiUrl: String,
    onLogin: (ClaimedSession) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Brainplaner Beta", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Enter the invite code you were given.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it.uppercase()
                errorMessage = null
            },
            label = { Text("Invite code") },
            placeholder = { Text("BP-XXXX-XXXX-XXXX") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            isError = errorMessage != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            ),
        )

        if (errorMessage != null) {
            Text(
                errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val trimmed = code.trim()
                if (trimmed.length < 4) {
                    errorMessage = "Enter your invite code"
                    return@Button
                }
                isLoading = true
                errorMessage = null
                scope.launch {
                    val result = claimInvite(context, apiUrl, trimmed)
                    isLoading = false
                    result.fold(
                        onSuccess = { onLogin(it) },
                        onFailure = { errorMessage = it.message ?: "Could not claim invite" },
                    )
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Continue")
            }
        }
    }
}

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

private suspend fun claimInvite(
    context: Context,
    apiUrl: String,
    code: String,
): Result<ClaimedSession> = withContext(Dispatchers.IO) {
    runCatching {
        val deviceId = UserAuth.getDeviceId(context)
        val payload = JSONObject()
            .put("code", code)
            .put("device_id", deviceId)
            .toString()
        val request = Request.Builder()
            .url("$apiUrl/beta/claim")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val detail = runCatching { JSONObject(body).optString("detail") }.getOrNull()
                throw IllegalStateException(
                    when (res.code) {
                        404 -> "Invite code not found"
                        409 -> "This invite was already used on another device"
                        410 -> "This invite has expired"
                        else -> detail?.takeIf { it.isNotBlank() } ?: "Server error (${res.code})"
                    }
                )
            }
            val json = JSONObject(body)
            ClaimedSession(
                userId = json.getString("user_id"),
                accessToken = json.getString("access_token"),
                expiresAt = json.getLong("expires_at"),
            )
        }
    }
}
