package com.brainplaner.phone

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

/**
 * Counts user-perceptible notifications received while a session is active or
 * in cooldown. Per Stothart, Mitchum & Yehnert (2015), even unanswered
 * notifications ~triple commission errors on sustained-attention tasks
 * (d ≈ 0.5–0.7) — bigger than the brain-drain mere-presence effect.
 *
 * Forwards each qualifying notification to PhoneAwarenessService via a
 * package-scoped broadcast; aggregation, gating on session/cooldown phase,
 * and persistence live there.
 *
 * Requires the user to grant "Notification access" in system settings;
 * isPermissionGranted() / openPermissionSettings() are exposed for UI to
 * onboard the permission.
 */
class BrainplanerNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // Skip our own notifications (foreground service banner, etc.)
        if (sbn.packageName == packageName) return

        val n = sbn.notification ?: return

        // Skip ongoing system notifications (calls in progress, downloads, media,
        // foreground services). These don't fire fresh buzz/sound disruptions.
        if (sbn.isOngoing) return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        // Skip group summaries — children are posted individually, the summary
        // would double-count the bundle.
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        // Skip low/min priority — silent background status indicators that
        // don't disrupt attention. (Channel importance is the real lever on
        // O+, but well-behaved apps still mirror it onto Notification.priority.)
        @Suppress("DEPRECATION")
        if (n.priority < Notification.PRIORITY_DEFAULT) return

        val intent = Intent(ACTION_NOTIFICATION_RECEIVED).apply {
            setPackage(packageName)
            putExtra("source_package", sbn.packageName)
            putExtra("post_time", sbn.postTime)
        }
        sendBroadcast(intent)

        android.util.Log.d(
            "BrainplanerNotifListener",
            "Notification from ${sbn.packageName} (id=${sbn.id})"
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        android.util.Log.i("BrainplanerNotifListener", "Listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        android.util.Log.i("BrainplanerNotifListener", "Listener disconnected")
    }

    companion object {
        const val ACTION_NOTIFICATION_RECEIVED = "com.brainplaner.phone.NOTIFICATION_RECEIVED"

        fun isPermissionGranted(context: Context): Boolean {
            return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }

        fun openPermissionSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
