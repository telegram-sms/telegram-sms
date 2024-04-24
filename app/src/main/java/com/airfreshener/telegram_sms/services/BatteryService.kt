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
import com.airfreshener.telegram_sms.utils.ServiceUtils.register
import com.airfreshener.telegram_sms.utils.ServiceUtils.stopForeground
import com.airfreshener.telegram_sms.utils.SmsUtils
import okhttp3.Request
import java.io.IOException
import java.util.Objects

class BatteryService : Service() {

    private var batteryReceiver: BatteryReceiver? = null
    private val prefsRepository by lazy { app().prefsRepository }
    private val telegramRepository by lazy { app().telegramRepository }

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
        batteryReceiver = BatteryReceiver(service = this, sendLoopList = sendLoopList).also { receiver ->
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_BATTERY_LOW)
                if (chargerStatus) {
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                }
                addAction(Consts.BROADCAST_STOP_SERVICE)
            }
            register(receiver, filter)
        }
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
        val message = obj.content ?: return
        lastReceiveTime = System.currentTimeMillis()
        val messageId = telegramRepository.sendMessageSync(
            message = message,
            messageId = lastReceiveMessageId.takeIf { System.currentTimeMillis() - lastReceiveTime <= 5000L },
        )
        if (messageId != null) {
            lastReceiveMessageId = messageId
        } else {
            lastReceiveMessageId = -1
            if (obj.action == Intent.ACTION_BATTERY_LOW) {
                SmsUtils.sendFallbackSms(applicationContext, message, -1)
            }
        }
    }

    companion object {
        private val sendLoopList: ArrayList<SendObject> = ArrayList()
        private var lastReceiveTime: Long = 0
        private var lastReceiveMessageId: Long = -1
        private val TAG = BatteryService::class.java.simpleName
    }

}

private class SendObject {
    var content: String? = null
    var action: String? = null
}

private class BatteryReceiver(
    private val service: BatteryService,
    private val sendLoopList: ArrayList<SendObject>
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        assert(intent.action != null)
        Log.d(TAG, "Receive action: " + intent.action)
        if (intent.action == Consts.BROADCAST_STOP_SERVICE) {
            Log.i(TAG, "Received stop signal, quitting now...")
            service.stopSelf()
            Process.killProcess(Process.myPid()) // TODO why?
            return
        }
        val sb = StringBuilder()
        val action = intent.action
        if (action == null) {
            Log.e(TAG, "intent action is null")
            return
        }
        when (action) {
            Intent.ACTION_BATTERY_OKAY -> sb.append(context.getString(R.string.low_battery_status_end))
            Intent.ACTION_BATTERY_LOW -> sb.append(context.getString(R.string.battery_low))
            Intent.ACTION_POWER_CONNECTED -> sb.append(context.getString(R.string.charger_connect))
            Intent.ACTION_POWER_DISCONNECTED -> sb.append(context.getString(R.string.charger_disconnect))
        }
        var batteryLevel = context.batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (batteryLevel > 100) {
            Log.d(TAG, "The previous battery is over 100%, and the correction is 100%.")
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

    companion object {
        private val TAG = BatteryReceiver::class.java.simpleName
    }
}
