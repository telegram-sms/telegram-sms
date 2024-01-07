package com.airfreshener.telegram_sms.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.utils.Consts
import com.airfreshener.telegram_sms.utils.LogUtils
import com.airfreshener.telegram_sms.utils.NetworkUtils.getOkhttpObj
import com.airfreshener.telegram_sms.utils.NetworkUtils.getUrl
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils
import com.airfreshener.telegram_sms.utils.PaperUtils
import com.airfreshener.telegram_sms.utils.ServiceUtils.stopForeground
import com.airfreshener.telegram_sms.utils.SmsUtils
import okhttp3.Request
import java.io.IOException
import java.util.Objects

class BatteryService : Service() {

    private var batteryReceiver: BatteryReceiver? = null

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        stopForeground()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = OtherUtils.getNotificationObj(
            applicationContext,
            getString(R.string.battery_monitoring_notify)
        )
        startForeground(Consts.ServiceNotifyId.BATTERY, notification)
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val context = applicationContext
        PaperUtils.init(context)
        val sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE)
        chatId = sharedPreferences.getString("chat_id", "")
        botToken = sharedPreferences.getString("bot_token", "") ?: ""
        dohSwitch = sharedPreferences.getBoolean("doh_switch", true)
        val chargerStatus = sharedPreferences.getBoolean("charger_status", false)
        batteryReceiver = BatteryReceiver()
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_BATTERY_OKAY)
        filter.addAction(Intent.ACTION_BATTERY_LOW)
        if (chargerStatus) {
            filter.addAction(Intent.ACTION_POWER_CONNECTED)
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        filter.addAction(Consts.BROADCAST_STOP_SERVICE)
        registerReceiver(batteryReceiver, filter)
        sendLoopList = ArrayList()
        Thread {
            val needRemove = ArrayList<SendObject>()
            while (true) {
                for (item in sendLoopList) {
                    networkHandle(item)
                    needRemove.add(item)
                }
                sendLoopList.removeAll(needRemove.toSet())
                needRemove.clear()
                if (sendLoopList.size == 0) {
                    // Only enter sleep mode when there are no messages
                    try {
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
        }.start()
    }

    private fun networkHandle(obj: SendObject) {
        val requestBody = RequestMessage()
        requestBody.chat_id = chatId
        requestBody.text = obj.content
        var requestUri = getUrl(botToken, "sendMessage")
        if (System.currentTimeMillis() - lastReceiveTime <= 5000L && lastReceiveMessageId != -1L) {
            requestUri = getUrl(botToken, "editMessageText")
            requestBody.message_id = lastReceiveMessageId
            Log.d(SERVICE_TAG, "onReceive: edit_mode")
        }
        lastReceiveTime = System.currentTimeMillis()
        val okHttpClient = getOkhttpObj(dohSwitch)
        val body = requestBody.toRequestBody()
        val request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okHttpClient.newCall(request)
        val errorHead = "Send battery info failed:"
        try {
            val response = call.execute()
            if (response.code == 200) {
                lastReceiveMessageId = OtherUtils.getMessageId(response.body?.string())
            } else {
                assert(response.body != null)
                lastReceiveMessageId = -1
                if (obj.action == Intent.ACTION_BATTERY_LOW) {
                    SmsUtils.sendFallbackSms(applicationContext, requestBody.text, -1)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            LogUtils.writeLog(applicationContext, errorHead + e.message)
            if (obj.action == Intent.ACTION_BATTERY_LOW) {
                SmsUtils.sendFallbackSms(applicationContext, requestBody.text, -1)
            }
        }
    }


    companion object {
        private var sendLoopList: ArrayList<SendObject> = ArrayList()
        var botToken: String = ""
        var chatId: String? = null
        var dohSwitch = false
        var lastReceiveTime: Long = 0
        var lastReceiveMessageId: Long = -1
        private const val SERVICE_TAG = "BatteryService"
        private const val RECEIVER_TAG = "BatteryReceiver"
    }

    private class SendObject {
        var content: String? = null
        var action: String? = null
    }

    private inner class BatteryReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            assert(intent.action != null)
            Log.d(RECEIVER_TAG, "Receive action: " + intent.action)
            if (intent.action == Consts.BROADCAST_STOP_SERVICE) {
                Log.i(RECEIVER_TAG, "Received stop signal, quitting now...")
                stopSelf()
                Process.killProcess(Process.myPid())
                return
            }
            val sb = StringBuilder(context.getString(R.string.system_message_head))
            val action = intent.action
            val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
            when (Objects.requireNonNull<String?>(action)) {
                Intent.ACTION_BATTERY_OKAY -> sb.append(context.getString(R.string.low_battery_status_end))
                Intent.ACTION_BATTERY_LOW -> sb.append(context.getString(R.string.battery_low))
                Intent.ACTION_POWER_CONNECTED -> sb.append(context.getString(R.string.charger_connect))
                Intent.ACTION_POWER_DISCONNECTED -> sb.append(context.getString(R.string.charger_disconnect))
            }
            var batteryLevel =
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (batteryLevel > 100) {
                Log.d(RECEIVER_TAG, "The previous battery is over 100%, and the correction is 100%.")
                batteryLevel = 100
            }
            sb.append("\n")
                .append(context.getString(R.string.current_battery_level))
                .append(batteryLevel)
                .append("%")
            val obj = SendObject()
            obj.action = action
            obj.content = sb.toString()
            sendLoopList.add(obj)
        }
    }
}
