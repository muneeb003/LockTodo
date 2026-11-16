package com.locktodo.app

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat

object AppSettings {

    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Tasks shown as a notification on the lock screen. */
    fun notificationEnabled(c: Context) = prefs(c).getBoolean("notif", true)

    fun setNotificationEnabled(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("notif", value).apply()
        apply(c)
    }

    /** Full-screen to-do view that appears over the lock screen. */
    fun lockViewEnabled(c: Context) = prefs(c).getBoolean("lockview", false)

    fun setLockViewEnabled(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("lockview", value).apply()
        apply(c)
    }

    /** Show other apps' notifications on the lock-screen view. */
    fun showNotifications(c: Context) = prefs(c).getBoolean("show_notifs", true)

    fun setShowNotifications(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("show_notifs", value).apply()
    }

    /** Show only the app name, not the message, on the lock-screen view. */
    fun hideNotificationContent(c: Context) = prefs(c).getBoolean("hide_notif_content", false)

    fun setHideNotificationContent(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("hide_notif_content", value).apply()
        NotificationsService.reload()
    }

    /** "Display over other apps" — needed to open the view from the background. */
    fun hasOverlayPermission(c: Context) = Settings.canDrawOverlays(c)

    fun lockViewActive(c: Context) = lockViewEnabled(c) && hasOverlayPermission(c)

    /** Starts/stops the background service and refreshes the notification to match the settings. */
    fun apply(c: Context) {
        val intent = Intent(c, LockScreenService::class.java)
        if (lockViewActive(c)) {
            ContextCompat.startForegroundService(c, intent)
        } else {
            c.stopService(intent)
        }
        LockScreenNotifier.update(c)
    }
}
