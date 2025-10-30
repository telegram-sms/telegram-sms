package com.qwe7002.telegram_sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qwe7002.telegram_sms.static_class.Logs
import com.qwe7002.telegram_sms.static_class.Service
import com.tencent.mmkv.MMKV

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(context::class.simpleName, "Receive action: " + intent.action)
        MMKV.initialize(context)
        val preferences = MMKV.defaultMMKV()
        if (preferences.getBoolean("initialized", false)) {
            KeepAliveJob.startJob(context)
            ReSendJob.startJob(context)
            Logs.writeLog(
                context,
                "Received [" + intent.action + "] broadcast, starting background service."
            )
            Service.startService(
                context,
                preferences.getBoolean("battery_monitoring_switch", false),
                preferences.getBoolean("chat_command", false)
            )
        }
    }
}
