package com.locktodo.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** The persistent notification that lists pending tasks on the lock screen. */
object LockScreenNotifier {

    const val ID = 1
    private const val CHANNEL = "lockscreen_tasks"
    private const val MAX_LINES = 7

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(
            CHANNEL,
            c.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW, // silent, but still shown on the lock screen
        ).apply {
            description = c.getString(R.string.channel_desc)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    fun build(c: Context): Notification {
        val pending = TodoStore.get(c).todos.value.sortedForDisplay().filter { !it.done }

        val title = when (pending.size) {
            0 -> c.getString(R.string.notif_all_done)
            1 -> c.getString(R.string.notif_one_left)
            else -> c.getString(R.string.notif_many_left, pending.size)
        }
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        pending.take(MAX_LINES).forEach { style.addLine("•  ${it.title}") }
        if (pending.size > MAX_LINES) {
            style.setSummaryText(c.getString(R.string.notif_more, pending.size - MAX_LINES))
        }

        val open = PendingIntent.getActivity(
            c, 0,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(pending.firstOrNull()?.title ?: c.getString(R.string.notif_nothing))
            .setStyle(if (pending.isEmpty()) null else style)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun update(c: Context) {
        val nm = NotificationManagerCompat.from(c)
        val wanted = AppSettings.notificationEnabled(c) || AppSettings.lockViewActive(c)
        if (!wanted) {
            nm.cancel(ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(c)
        nm.notify(ID, build(c))
    }
}
