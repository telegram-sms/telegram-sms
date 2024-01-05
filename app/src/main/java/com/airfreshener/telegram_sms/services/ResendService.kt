package com.airfreshener.telegram_sms.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.model.ProxyConfigV2
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.model.ServiceNotifyId
import com.airfreshener.telegram_sms.utils.Consts
import com.airfreshener.telegram_sms.utils.LogUtils
import com.airfreshener.telegram_sms.utils.NetworkUtils.check_network_status
import com.airfreshener.telegram_sms.utils.NetworkUtils.get_okhttp_obj
import com.airfreshener.telegram_sms.utils.NetworkUtils.get_url
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils
import io.paperdb.Paper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException

class ResendService : Service() {
    var context: Context? = null
    var request_uri: String? = null
    var receiver: StopNotifyReceiver? = null
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        resend_list = Paper.book().read(table_name, ArrayList())
        val notification =
            OtherUtils.get_notification_obj(context, getString(R.string.failed_resend))
        startForeground(ServiceNotifyId.RESEND_SERVICE, notification)
        return START_NOT_STICKY
    }

    private fun network_progress_handle(
        message: String,
        chat_id: String?,
        okhttp_client: OkHttpClient
    ) {
        val request_body = RequestMessage()
        request_body.chat_id = chat_id
        request_body.text = message
        if (message.contains("<code>") && message.contains("</code>")) {
            request_body.parse_mode = "html"
        }
        val body: RequestBody = request_body.toRequestBody()
        val request_uri = request_uri ?: return
        val request_obj: Request = Request.Builder().url(request_uri).method("POST", body).build()
        val call = okhttp_client.newCall(request_obj)
        try {
            val response = call.execute()
            if (response.code == 200) {
                val resend_list_local = Paper.book().read(table_name, ArrayList<String>())!!
                resend_list_local.remove(message)
                Paper.book().write(table_name, resend_list_local)
            }
        } catch (e: IOException) {
            LogUtils.write_log(context, "An error occurred while resending: " + e.message)
            e.printStackTrace()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val context = applicationContext
        this.context = context
        Paper.init(context)
        val filter = IntentFilter()
        filter.addAction(Consts.BROADCAST_STOP_SERVICE)
        receiver = StopNotifyReceiver()
        registerReceiver(receiver, filter)
        val sharedPreferences = context.getSharedPreferences("data", MODE_PRIVATE)
        request_uri = get_url(sharedPreferences.getString("bot_token", "")!!, "SendMessage")
        Thread {
            resend_list = Paper.book().read(table_name, ArrayList())
            while (true) {
                if (check_network_status(context)) {
                    val send_list = resend_list
                    val okhttp_client = get_okhttp_obj(
                        sharedPreferences.getBoolean("doh_switch", true),
                        Paper.book("system_config").read("proxy_config", ProxyConfigV2())!!
                    )
                    for (item in send_list!!) {
                        network_progress_handle(
                            item,
                            sharedPreferences.getString("chat_id", ""),
                            okhttp_client
                        )
                    }
                    resend_list = Paper.book().read(table_name, ArrayList())
                    if (resend_list === send_list || resend_list!!.size == 0) {
                        break
                    }
                }
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            LogUtils.write_log(context, "The resend failure message is complete.")
            stopSelf()
        }.start()
    }

    override fun onDestroy() {
        stopForeground(true)
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    inner class StopNotifyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Consts.BROADCAST_STOP_SERVICE) {
                Log.i("resend_loop", "Received stop signal, quitting now...")
                stopSelf()
            }
        }
    }

    companion object {
        var resend_list: ArrayList<String>? = null
        private const val table_name = "resend_list"
    }
}
