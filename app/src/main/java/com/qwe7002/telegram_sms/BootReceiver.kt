package com.qwe7002.telegram_sms

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.qwe7002.telegram_sms.static_class.log
import com.qwe7002.telegram_sms.static_class.service
import io.paperdb.Paper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val TAG = "boot_receiver"
        Log.d(TAG, "Receive action: " + intent.action)
        
        // Start services regardless of initialization status
        // This ensures services run even if app UI was never shown
        Paper.init(context)
        KeepAliveJob.startJob(context)
        ReSendJob.startJob(context)
        
        val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        
        // Start all services with minimal notifications
        service.startService(
            context,
            sharedPreferences.getBoolean("battery_monitoring_switch", false),
            sharedPreferences.getBoolean("chat_command", false)
        )
        
        log.writeLog(
            context,
            "Received [" + intent.action + "] broadcast, starting background service."
        )
        
        // Ensure app remains hidden from recents
        val packageManager = context.packageManager
        val componentName = ComponentName(context, main_activity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
