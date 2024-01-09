package com.airfreshener.telegram_sms.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.TelegramSmsApp
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.utils.Consts
import com.airfreshener.telegram_sms.utils.LogUtils
import com.airfreshener.telegram_sms.utils.NetworkUtils.checkNetworkStatus
import com.airfreshener.telegram_sms.utils.NetworkUtils.getOkhttpObj
import com.airfreshener.telegram_sms.utils.NetworkUtils.getUrl
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils
import com.airfreshener.telegram_sms.utils.PaperUtils.DEFAULT_BOOK
import com.airfreshener.telegram_sms.utils.PaperUtils.tryRead
import com.airfreshener.telegram_sms.utils.ServiceUtils.stopForeground
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException

class ResendService : Service() {

    private val prefsRepository by lazy { (application as TelegramSmsApp).prefsRepository }

    var requestUri: String? = null
    private var receiver: StopNotifyReceiver? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        resendList = DEFAULT_BOOK.tryRead(table_name, ArrayList())
        val notification =
            OtherUtils.getNotificationObj(applicationContext, getString(R.string.failed_resend))
        startForeground(Consts.ServiceNotifyId.RESEND_SERVICE, notification)
        return START_NOT_STICKY
    }

    private fun networkProgressHandle(
        message: String,
        chatId: String?,
        okHttpClient: OkHttpClient
    ) {
        val requestBody = RequestMessage()
        requestBody.chat_id = chatId
        requestBody.text = message
        if (message.contains("<code>") && message.contains("</code>")) {
            requestBody.parse_mode = "html"
        }
        val body: RequestBody = requestBody.toRequestBody()
        val requestUri = requestUri ?: return
        val requestObj: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okHttpClient.newCall(requestObj)
        try {
            val response = call.execute()
            if (response.code == 200) {
                val resendListLocal = DEFAULT_BOOK.tryRead(table_name, ArrayList<String>())
                resendListLocal.remove(message)
                DEFAULT_BOOK.write(table_name, resendListLocal)
            }
        } catch (e: IOException) {
            LogUtils.writeLog(applicationContext, "An error occurred while resending: " + e.message)
            e.printStackTrace()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val context = applicationContext
        val filter = IntentFilter().apply {
            addAction(Consts.BROADCAST_STOP_SERVICE)
        }
        receiver = StopNotifyReceiver()
        registerReceiver(receiver, filter)
        val settings = prefsRepository.getSettings()
        requestUri = getUrl(settings.botToken, "SendMessage")
        Thread {
            resendList = DEFAULT_BOOK.tryRead(table_name, ArrayList())
            while (true) {
                if (checkNetworkStatus(context)) {
                    val sendList = resendList
                    val okHttpClient = getOkhttpObj(settings.isDnsOverHttp)
                    for (item in sendList) {
                        networkProgressHandle(item, settings.chatId, okHttpClient)
                    }
                    resendList = DEFAULT_BOOK.tryRead(table_name, ArrayList())
                    if (resendList === sendList || resendList.isEmpty()) {
                        break
                    }
                }
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            LogUtils.writeLog(context, "The resend failure message is complete.")
            stopSelf()
        }.start()
    }

    override fun onDestroy() {
        stopForeground()
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
        var resendList: List<String> = emptyList()
        private const val table_name = "resend_list"
    }
}
