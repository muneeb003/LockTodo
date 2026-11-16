package com.locktodo.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One notification from another app, ready to show on the lock-screen view. */
data class LockNotif(
    val key: String,
    val appName: String,
    val appIcon: ImageBitmap?,
    val title: String,
    val text: String,
    val time: Long,
    val contentIntent: PendingIntent?,
    val clearable: Boolean,
    val autoCancel: Boolean,
)

/** Live list of the phone's notifications, shared with the lock-screen view. */
object NotificationHub {
    private val _items = MutableStateFlow<List<LockNotif>>(emptyList())
    val items: StateFlow<List<LockNotif>> = _items.asStateFlow()
    internal fun set(list: List<LockNotif>) { _items.value = list }
}

/**
 * Reads the phone's notifications (needs "Notification access", granted in Settings)
 * so the lock-screen view can show them, open them and dismiss them.
 */
class NotificationsService : NotificationListenerService() {

    private val iconCache = HashMap<String, ImageBitmap?>()

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        instance = null
        NotificationHub.set(emptyList())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        val hideContent = AppSettings.hideNotificationContent(this)
        val list = runCatching { activeNotifications }.getOrNull().orEmpty()
            .asSequence()
            .filter { it.packageName != packageName }            // not our own task notification
            .filter { !it.isOngoing }                             // skip music players, downloads, etc.
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .filter { it.notification.visibility != Notification.VISIBILITY_SECRET }
            .mapNotNull { toItem(it, hideContent) }
            .sortedByDescending { it.time }
            .toList()
        NotificationHub.set(list)
    }

    private fun toItem(sbn: StatusBarNotification, hideContent: Boolean): LockNotif? {
        val n = sbn.notification
        // Respect apps that mark their notification as private on the lock screen.
        val shown = if (hideContent || n.visibility == Notification.VISIBILITY_PRIVATE) n.publicVersion else n
        val extras = (shown ?: n).extras
        var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        var text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (hideContent || (n.visibility == Notification.VISIBILITY_PRIVATE && shown == null)) {
            title = getString(R.string.notif_hidden_title)
            text = ""
        }
        if (title.isBlank() && text.isBlank()) return null

        val pm = packageManager
        val appName = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val icon = iconCache.getOrPut(sbn.packageName) {
            runCatching { pm.getApplicationIcon(sbn.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }

        return LockNotif(
            key = sbn.key,
            appName = appName,
            appIcon = icon,
            title = title,
            text = text,
            time = sbn.postTime,
            contentIntent = n.contentIntent,
            clearable = sbn.isClearable,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
        )
    }

    companion object {
        @Volatile
        private var instance: NotificationsService? = null

        fun hasAccess(c: Context) =
            NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

        fun dismiss(key: String) {
            runCatching { instance?.cancelNotification(key) }
        }

        fun dismissAll() {
            runCatching { instance?.cancelAllNotifications() }
        }

        fun reload() {
            instance?.refresh()
        }
    }
}
