package com.airfreshener.telegram_sms.utils

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.airfreshener.telegram_sms.model.Settings
import com.airfreshener.telegram_sms.services.BatteryService
import com.airfreshener.telegram_sms.services.ChatCommandService
import com.airfreshener.telegram_sms.services.NotificationListenerService

object ServiceUtils {

    val Context.telephonyManager: TelephonyManager
            get() = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    val Context.subscriptionManager: SubscriptionManager
            get() = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

    val Context.batteryManager: BatteryManager
            get() = getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    val Context.powerManager: PowerManager
            get() = getSystemService(Context.POWER_SERVICE) as PowerManager

    val Context.connectivityManager: ConnectivityManager
            get() = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val Context.notificationManager: NotificationManager
            get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val Context.wifiManager: WifiManager
            get() = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun Service.stopForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    fun restartServices(context: Context, settings: Settings) {
        stopAllService(context)
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        startService(context, settings)
    }

    fun stopAllService(context: Context) {
        context.sendBroadcast(Intent(Consts.BROADCAST_STOP_SERVICE))
    }

    fun startService(context: Context, settings: Settings) {
        tryStartNotificationListenerService(context)
        tryStartBatteryService(context, settings.isBatteryMonitoring)
        tryStartChatPollingService(context, settings.isChatCommand)
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

    fun Context.register(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(receiver, filter, Service.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }
}
