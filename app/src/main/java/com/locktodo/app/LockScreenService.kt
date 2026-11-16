package com.locktodo.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Runs while "Full lock screen view" is on. Each time the screen turns off it opens
 * [LockScreenActivity], so the to-do list is the first thing you see when you wake the phone.
 */
class LockScreenService : Service() {

    private var overlay: View? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF) return
            if (!Settings.canDrawOverlays(context)) return
            // Don't cover the screen during a phone or video call.
            val audio = context.getSystemService(AudioManager::class.java)
            if (audio.mode == AudioManager.MODE_IN_CALL ||
                audio.mode == AudioManager.MODE_IN_COMMUNICATION
            ) return

            context.startActivity(
                Intent(context, LockScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LockScreenNotifier.ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, LockScreenNotifier.ID, LockScreenNotifier.build(this), type)

        ContextCompat.registerReceiver(
            this, screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        addInvisibleOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        removeInvisibleOverlay()
        // Keep the notification; update() below decides whether it should stay.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        super.onDestroy()
        LockScreenNotifier.update(this)
    }

    /**
     * Android 15+ only lets "display over other apps" holders start activities from the
     * background while they have an overlay window showing, so keep a 1×1 transparent one.
     */
    private fun addInvisibleOverlay() {
        if (!Settings.canDrawOverlays(this) || overlay != null) return
        val view = View(this)
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching {
            getSystemService(WindowManager::class.java).addView(view, params)
            overlay = view
        }
    }

    private fun removeInvisibleOverlay() {
        overlay?.let { v -> runCatching { getSystemService(WindowManager::class.java).removeView(v) } }
        overlay = null
    }
}
