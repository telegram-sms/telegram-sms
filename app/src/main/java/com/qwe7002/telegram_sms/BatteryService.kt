@file:Suppress("ClassName")

package com.qwe7002.telegram_sms

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.Other
import com.qwe7002.telegram_sms.static_class.TelegramApi
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.value.CcType
import com.qwe7002.telegram_sms.value.Notify
import com.tencent.mmkv.MMKV
import java.util.Objects

class BatteryService : Service() {
    private lateinit var batteryReceiver: batteryChangeReceiver
    private lateinit var sendLoopList: ArrayList<sendObj>
    private var lastReceiveTime: Long = 0
    private var lastReceiveMessageId: Long = -1


    @SuppressLint("InlinedApi")
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val notification =
            Other.getNotificationObj(
                applicationContext,
                getString(R.string.battery_monitoring_notify)
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notify.BATTERY,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Notify.BATTERY, notification)
        }
        return START_STICKY
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag", "InlinedApi")
    override fun onCreate() {
        super.onCreate()
        val preferences = MMKV.defaultMMKV()
        val chargerStatus = preferences.getBoolean("charger_status", false)
        batteryReceiver = batteryChangeReceiver()
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_BATTERY_OKAY)
        filter.addAction(Intent.ACTION_BATTERY_LOW)
        if (chargerStatus) {
            filter.addAction(Intent.ACTION_POWER_CONNECTED)
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(batteryReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
        sendLoopList = ArrayList()
        Thread {
            val needRemove = ArrayList<sendObj>()
            while (true) {
                for (item in sendLoopList) {
                    networkHandle(item)
                    needRemove.add(item)
                }
                sendLoopList.removeAll(needRemove.toSet())
                needRemove.clear()
                if (sendLoopList.isEmpty()) {
                    //Only enter sleep mode when there are no messages
                    try {
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        Log.i("BatteryService", "onCreate: $e")
                    }
                }
            }
        }.start()
    }

    private fun networkHandle(obj: sendObj) {
        val requestMessage = RequestMessage()
        requestMessage.text = obj.content

        var method = "sendMessage"
        if ((System.currentTimeMillis() - lastReceiveTime) <= 5000L && lastReceiveMessageId != -1L) {
            method = "editMessageText"
            requestMessage.messageId = lastReceiveMessageId
            Log.d(this::class.java.simpleName, "onReceive: edit_mode")
        }
        lastReceiveTime = System.currentTimeMillis()

        // Only enable SMS fallback for low battery notifications
        val enableFallback = obj.action == Intent.ACTION_BATTERY_LOW

        val result = TelegramApi.sendMessageSync(
            context = this,
            requestBody = requestMessage,
            method = method,
            errorTag = "BatteryService",
            fallbackSubId = if (enableFallback) 0 else -1,  // Use default sub for fallback
            enableResend = false  // Battery service handles its own retry logic
        )

        if (result != null) {
            lastReceiveMessageId = Other.getMessageId(result)
        } else {
            lastReceiveMessageId = -1
        }
    }

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private class sendObj {
        lateinit var content: String
        lateinit var action: String
    }

    internal inner class batteryChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(this::class.simpleName, "Receive action: " + intent.action)

            val action = intent.action
            val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
            val message = when (Objects.requireNonNull(action)) {
                Intent.ACTION_BATTERY_OKAY -> context.getString(R.string.low_battery_status_end)
                Intent.ACTION_BATTERY_LOW -> context.getString(R.string.battery_low)
                Intent.ACTION_POWER_CONNECTED -> context.getString(R.string.charger_connect)
                Intent.ACTION_POWER_DISCONNECTED -> context.getString(R.string.charger_disconnect)
                else -> {
                    Log.e(this::class.simpleName, "Unknown action: $action")
                    return
                }
            }
            var batteryLevel =
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (batteryLevel > 100) {
                Log.d(this::class.simpleName, "The previous battery is over 100%, and the correction is 100%.")
                batteryLevel = 100
            }
            val result = Template.render(
                applicationContext, "TPL_battery", mapOf(
                    "Message" to message, "BatteryLevel" to batteryLevel.toString()
                )
            )
            if (action == Intent.ACTION_BATTERY_LOW || action == Intent.ACTION_BATTERY_OKAY) {
                CcSendJob.startJob(
                    context,
                    CcType.BATTERY,
                    context.getString(R.string.app_name),
                    result
                )
            }
            val obj = sendObj()
            obj.action = action!!
            obj.content = result
            sendLoopList.add(obj)
        }
    }

}

