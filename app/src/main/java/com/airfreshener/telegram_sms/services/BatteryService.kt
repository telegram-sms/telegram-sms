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
import com.airfreshener.telegram_sms.model.ProxyConfigV2
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.model.ServiceNotifyId
import com.airfreshener.telegram_sms.utils.Consts
import com.airfreshener.telegram_sms.utils.LogUtils
import com.airfreshener.telegram_sms.utils.NetworkUtils.getOkhttpObj
import com.airfreshener.telegram_sms.utils.NetworkUtils.getUrl
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils
import com.airfreshener.telegram_sms.utils.SmsUtils
import io.paperdb.Paper
import okhttp3.Request
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

class BatteryService : Service() {

    private var context: Context? = null
    private var battery_receiver: BatteryReceiver? = null


    override fun onDestroy() {
        unregisterReceiver(battery_receiver)
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification =
            OtherUtils.getNotificationObj(context, getString(R.string.battery_monitoring_notify))
        startForeground(ServiceNotifyId.BATTERY, notification)
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        val context = applicationContext
        this.context = context
        Paper.init(context)
        val sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE)
        chat_id = sharedPreferences.getString("chat_id", "")
        bot_token = sharedPreferences.getString("bot_token", "") ?: ""
        doh_switch = sharedPreferences.getBoolean("doh_switch", true)
        val charger_status = sharedPreferences.getBoolean("charger_status", false)
        battery_receiver = BatteryReceiver()
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_BATTERY_OKAY)
        filter.addAction(Intent.ACTION_BATTERY_LOW)
        if (charger_status) {
            filter.addAction(Intent.ACTION_POWER_CONNECTED)
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        filter.addAction(Consts.BROADCAST_STOP_SERVICE)
        registerReceiver(battery_receiver, filter)
        send_loop_list = ArrayList()
        Thread {
            val need_remove = ArrayList<SendObject>()
            while (true) {
                for (item in send_loop_list) {
                    network_handle(item)
                    need_remove.add(item)
                }
                send_loop_list.removeAll(need_remove.toSet())
                need_remove.clear()
                if (send_loop_list.size == 0) {
                    //Only enter sleep mode when there are no messages
                    try {
                        Thread.sleep(1000)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
        }.start()
    }

    private fun network_handle(obj: SendObject) {
        val TAG = "network_handle"
        val request_body = RequestMessage()
        request_body.chat_id = chat_id
        request_body.text = obj.content
        var request_uri = getUrl(bot_token, "sendMessage")
        if (System.currentTimeMillis() - last_receive_time <= 5000L && last_receive_message_id != -1L) {
            request_uri = getUrl(bot_token, "editMessageText")
            request_body.message_id = last_receive_message_id
            Log.d(TAG, "onReceive: edit_mode")
        }
        last_receive_time = System.currentTimeMillis()
        val okhttp_client = getOkhttpObj(
            doh_switch,
            Paper.book("system_config").read("proxy_config", ProxyConfigV2())!!
        )
        val body = request_body.toRequestBody()
        val request: Request = Request.Builder().url(request_uri).method("POST", body).build()
        val call = okhttp_client.newCall(request)
        val error_head = "Send battery info failed:"
        try {
            val response = call.execute()
            if (response.code == 200) {
                last_receive_message_id = OtherUtils.getMessageId(response.body?.string())
            } else {
                assert(response.body != null)
                last_receive_message_id = -1
                if (obj.action == Intent.ACTION_BATTERY_LOW) {
                    SmsUtils.sendFallbackSms(context, request_body.text, -1)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            LogUtils.writeLog(context, error_head + e.message)
            if (obj.action == Intent.ACTION_BATTERY_LOW) {
                SmsUtils.sendFallbackSms(context, request_body.text, -1)
            }
        }
    }


    companion object {
        private var send_loop_list: ArrayList<SendObject> = ArrayList()
        var bot_token: String = ""
        var chat_id: String? = null
        var doh_switch = false
        var last_receive_time: Long = 0
        var last_receive_message_id: Long = -1
    }

    private class SendObject {
        var content: String? = null
        var action: String? = null
    }


    private inner class BatteryReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val TAG = "battery_receiver"
            assert(intent.action != null)
            Log.d(TAG, "Receive action: " + intent.action)
            if (intent.action == Consts.BROADCAST_STOP_SERVICE) {
                Log.i(TAG, "Received stop signal, quitting now...")
                stopSelf()
                Process.killProcess(Process.myPid())
                return
            }
            val body = StringBuilder(
                """
                  ${context.getString(R.string.system_message_head)}
                  
                  """.trimIndent()
            )
            val action = intent.action
            val batteryManager = context.getSystemService(BATTERY_SERVICE) as BatteryManager
            when (Objects.requireNonNull<String?>(action)) {
                Intent.ACTION_BATTERY_OKAY -> body.append(context.getString(R.string.low_battery_status_end))
                Intent.ACTION_BATTERY_LOW -> body.append(context.getString(R.string.battery_low))
                Intent.ACTION_POWER_CONNECTED -> body.append(context.getString(R.string.charger_connect))
                Intent.ACTION_POWER_DISCONNECTED -> body.append(context.getString(R.string.charger_disconnect))
            }
            var battery_level =
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (battery_level > 100) {
                Log.d(TAG, "The previous battery is over 100%, and the correction is 100%.")
                battery_level = 100
            }
            val result = body.append("\n").append(context.getString(R.string.current_battery_level))
                .append(battery_level).append("%").toString()
            val obj = SendObject()
            obj.action = action
            obj.content = result
            send_loop_list.add(obj)
        }
    }
}
