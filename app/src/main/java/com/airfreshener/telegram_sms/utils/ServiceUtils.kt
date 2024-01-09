package com.airfreshener.telegram_sms.utils

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.airfreshener.telegram_sms.services.BatteryService
import com.airfreshener.telegram_sms.services.ChatCommandService
import com.airfreshener.telegram_sms.services.NotificationListenerService

object ServiceUtils {

    fun Service.stopForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    fun stopAllService(context: Context) {
        val intent = Intent(Consts.BROADCAST_STOP_SERVICE)
        context.sendBroadcast(intent)
    }

    fun startService(
        context: Context,
        batterySwitch: Boolean,
        chatCommandSwitch: Boolean
    ) {
        tryStartNotificationListenerService(context)
        tryStartBatteryService(context, batterySwitch)
        tryStartChatPollingService(context, chatCommandSwitch)
    }

    private fun tryStartChatPollingService(context: Context, chatCommandSwitch: Boolean) {
        val chatPollingService = Intent(context, ChatCommandService::class.java)
        if (chatCommandSwitch) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(chatPollingService)
            } else {
                context.startService(chatPollingService)
            }
        }
    }

    private fun tryStartBatteryService(context: Context, batterySwitch: Boolean) {
        val batteryService = Intent(context, BatteryService::class.java)
        if (batterySwitch) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(batteryService)
            } else {
                context.startService(batteryService)
            }
        }
    }

    private fun tryStartNotificationListenerService(context: Context) {
        if (isNotifyListener(context)) {
            Log.d("start_service", "start_service: ")
            val thisComponent = ComponentName(context, NotificationListenerService::class.java)
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                thisComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                thisComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    fun isNotifyListener(context: Context): Boolean {
        val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
        return packageNames.contains(context.packageName)
    }
}
