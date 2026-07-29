package com.stormalert.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stormalert.service.WeatherCheckService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            WeatherCheckService.startService(context)
        }
    }
}
