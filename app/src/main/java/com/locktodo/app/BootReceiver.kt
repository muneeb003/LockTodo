package com.locktodo.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings back the lock-screen notification / service after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED ->
                runCatching { AppSettings.apply(context) }
        }
    }
}
