@file:Suppress("ClassName")

package com.qwe7002.telegram_sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.Logs
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.static_class.Other
import com.qwe7002.telegram_sms.static_class.SMS
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.value.CcType
import com.qwe7002.telegram_sms.value.Const
import com.qwe7002.telegram_sms.value.Notify
import com.tencent.mmkv.MMKV
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Objects

class BatteryService : Service() {
    private lateinit var batteryReceiver: batteryChangeReceiver
    private lateinit var botToken: String
    private lateinit var chatId: String
    private lateinit var messageThreadId: String
    private lateinit var sendLoopList: ArrayList<sendObj>
    private var dohSwitch: Boolean = false
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
        //val sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        val preferences = MMKV.defaultMMKV()
        chatId = preferences.getString("chat_id", "").toString()
        botToken = preferences.getString("bot_token", "").toString()
        messageThreadId = preferences.getString("message_thread_id", "").toString()
        dohSwitch = preferences.getBoolean("doh_switch", true)
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
        val TAG = "network_handle"
        val requestMessage = RequestMessage()
        requestMessage.chatId = chatId
        requestMessage.text = obj.content
        requestMessage.messageThreadId = messageThreadId
        var requestUri = Network.getUrl(botToken, "sendMessage")
        if ((System.currentTimeMillis() - lastReceiveTime) <= 5000L && lastReceiveMessageId != -1L) {
            requestUri = Network.getUrl(botToken, "editMessageText")
            requestMessage.messageId = lastReceiveMessageId
            Log.d(TAG, "onReceive: edit_mode")
        }
        lastReceiveTime = System.currentTimeMillis()
        val okhttpObj = Network.getOkhttpObj(dohSwitch)
        val requestBodyRaw = Gson().toJson(requestMessage)
        val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpObj.newCall(request)
        val errorHead = "Send battery info failed:"
        try {
            val response = call.execute()
            if (response.code == 200) {
                lastReceiveMessageId =
                    Other.getMessageId(Objects.requireNonNull(response.body).string())
            } else {
                lastReceiveMessageId = -1
                if (obj.action == Intent.ACTION_BATTERY_LOW) {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.SEND_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        SMS.fallbackSMS( requestMessage.text, -1)
                    }

                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "networkHandle: $e")
            Logs.writeLog(applicationContext, errorHead + e.message)
            if (obj.action == Intent.ACTION_BATTERY_LOW) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.SEND_SMS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    SMS.fallbackSMS( requestMessage.text, -1)
                }
            }
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
            val TAG = "battery_receiver"
            assert(intent.action != null)
            Log.d(TAG, "Receive action: " + intent.action)

            val action = intent.action
            val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
            val message = when (Objects.requireNonNull(action)) {
                Intent.ACTION_BATTERY_OKAY -> context.getString(R.string.low_battery_status_end)
                Intent.ACTION_BATTERY_LOW -> context.getString(R.string.battery_low)
                Intent.ACTION_POWER_CONNECTED -> context.getString(R.string.charger_connect)
                Intent.ACTION_POWER_DISCONNECTED -> context.getString(R.string.charger_disconnect)
                else -> {
                    Log.e(TAG, "Unknown action: $action")
                    return
                }
            }
            var batteryLevel =
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (batteryLevel > 100) {
                Log.d(TAG, "The previous battery is over 100%, and the correction is 100%.")
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

