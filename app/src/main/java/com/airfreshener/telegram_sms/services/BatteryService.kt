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
import com.airfreshener.telegram_sms.utils.ContextUtils.app
import com.airfreshener.telegram_sms.utils.NetworkUtils
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils
import com.airfreshener.telegram_sms.utils.ServiceUtils.batteryManager
import com.airfreshener.telegram_sms.utils.ServiceUtils.stopForeground
import com.airfreshener.telegram_sms.utils.SmsUtils
import okhttp3.Request
import java.io.IOException
import java.util.Objects

class BatteryService : Service() {

    private var batteryReceiver: BatteryReceiver? = null
    private val prefsRepository by lazy { app().prefsRepository }
    private val logRepository by lazy { app().logRepository }

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
        val settings = prefsRepository.getSettings()
        val chargerStatus = settings.isChargerStatus
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
        sendLoopList.clear()
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
        val settings = prefsRepository.getSettings()
        val requestBody = RequestMessage()
        requestBody.chat_id = settings.chatId
        requestBody.text = obj.content
        val requestUri: String
        if (System.currentTimeMillis() - lastReceiveTime <= 5000L && lastReceiveMessageId != -1L) {
            requestUri = NetworkUtils.getUrl(settings.botToken, "editMessageText")
            requestBody.message_id = lastReceiveMessageId
            Log.d(SERVICE_TAG, "onReceive: edit_mode")
        } else {
            requestUri = NetworkUtils.getUrl(settings.botToken, "sendMessage")
        }
        lastReceiveTime = System.currentTimeMillis()
        val okHttpClient = NetworkUtils.getOkhttpObj(settings)
        val body = requestBody.toRequestBody()
        val request = Request.Builder().url(requestUri).post(body).build()
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
            logRepository.writeLog(errorHead + e.message)
            if (obj.action == Intent.ACTION_BATTERY_LOW) {
                SmsUtils.sendFallbackSms(applicationContext, requestBody.text, -1)
            }
        }
    }


    companion object {
        private val sendLoopList: ArrayList<SendObject> = ArrayList()
        private var lastReceiveTime: Long = 0
        private var lastReceiveMessageId: Long = -1
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
                Process.killProcess(Process.myPid()) // TODO why?
                return
            }
            val sb = StringBuilder(context.getString(R.string.system_message_head))
            val action = intent.action
            when (Objects.requireNonNull<String?>(action)) {
                Intent.ACTION_BATTERY_OKAY -> sb.append(context.getString(R.string.low_battery_status_end))
                Intent.ACTION_BATTERY_LOW -> sb.append(context.getString(R.string.battery_low))
                Intent.ACTION_POWER_CONNECTED -> sb.append(context.getString(R.string.charger_connect))
                Intent.ACTION_POWER_DISCONNECTED -> sb.append(context.getString(R.string.charger_disconnect))
            }
            var batteryLevel = context.batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
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
