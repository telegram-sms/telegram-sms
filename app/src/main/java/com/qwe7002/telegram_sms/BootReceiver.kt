package com.qwe7002.telegram_sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.qwe7002.telegram_sms.static_class.log
import com.qwe7002.telegram_sms.static_class.Service
import io.paperdb.Paper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val TAG = "boot_receiver"
        Log.d(TAG, "Receive action: " + intent.action)
        val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        if (sharedPreferences.getBoolean("initialized", false)) {
            Paper.init(context)
            KeepAliveJob.startJob(context)
            ReSendJob.startJob(context)
            log.writeLog(
                context,
                "Received [" + intent.action + "] broadcast, starting background service."
            )
            Service.startService(
                context,
                sharedPreferences.getBoolean("battery_monitoring_switch", false),
                sharedPreferences.getBoolean("chat_command", false)
            )
        }
    }
}
