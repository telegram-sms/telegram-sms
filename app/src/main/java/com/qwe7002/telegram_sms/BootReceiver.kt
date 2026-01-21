package com.qwe7002.telegram_sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qwe7002.telegram_sms.static_class.Service
import com.qwe7002.telegram_sms.value.TAG
import com.tencent.mmkv.MMKV

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val logTag = "${TAG}.BootReceiver"
        Log.d(logTag, "Receive action: " + intent.action)
        MMKV.initialize(context)
        val preferences = MMKV.defaultMMKV()
        if (preferences.getBoolean("initialized", false)) {
            KeepAliveJob.startJob(context)
            ReSendJob.startJob(context)
            Log.i(
                logTag,
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
