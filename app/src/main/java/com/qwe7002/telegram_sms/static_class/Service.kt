package com.qwe7002.telegram_sms.static_class

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.qwe7002.telegram_sms.BatteryService
import com.qwe7002.telegram_sms.NotificationService
import com.qwe7002.telegram_sms.chat_command_service
import com.qwe7002.telegram_sms.value.constValue

object Service {
    @JvmStatic
    fun stopAllService(context: Context) {
        val intent = Intent(constValue.BROADCAST_STOP_SERVICE)
        context.sendBroadcast(intent)
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun startService(context: Context, batterySwitch: Boolean, chatCommandSwitch: Boolean) {
        val batteryService = Intent(context, BatteryService::class.java)
        val chatLongPollingService = Intent(context, chat_command_service::class.java)
        if (isNotifyListener(context)) {
            Log.d("start_service", "start_service: ")
            val thisComponent = ComponentName(context, NotificationService::class.java)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (batterySwitch) {
                context.startForegroundService(batteryService)
            }
            if (chatCommandSwitch) {
                context.startForegroundService(chatLongPollingService)
            }
        } else {
            if (batterySwitch) {
                context.startService(batteryService)
            }
            if (chatCommandSwitch) {
                context.startService(chatLongPollingService)
            }
        }
    }

    @JvmStatic
    fun isNotifyListener(context: Context): Boolean {
        val packageNames = NotificationManagerCompat.getEnabledListenerPackages(context)
        return packageNames.contains(context.packageName)
    }
}
