package com.airfreshener.telegram_sms.services

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.TelegramSmsApp
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.utils.Consts
import com.airfreshener.telegram_sms.utils.LogUtils
import com.airfreshener.telegram_sms.utils.NetworkUtils.getOkhttpObj
import com.airfreshener.telegram_sms.utils.NetworkUtils.getUrl
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils.getNotificationObj
import com.airfreshener.telegram_sms.utils.PaperUtils.getSystemBook
import com.airfreshener.telegram_sms.utils.ResendUtils.addResendLoop
import com.airfreshener.telegram_sms.utils.ServiceUtils.stopForeground
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class NotificationListenerService : NotificationListenerService() {

    private val prefsRepository by lazy { (application as TelegramSmsApp).prefsRepository }

    override fun onCreate() {
        super.onCreate()
        val notification =
            getNotificationObj(applicationContext, getString(R.string.Notification_Listener_title))
        startForeground(Consts.ServiceNotifyId.NOTIFICATION_LISTENER_SERVICE, notification)
    }

    override fun onDestroy() {
        stopForeground()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val context = applicationContext
        val packageName = sbn.packageName
        Log.d(TAG, "onNotificationPosted: $packageName")
        val settings = prefsRepository.getSettings()
        if (!prefsRepository.getInitialized()) {
            Log.i(TAG, "Uninitialized, Notification receiver is deactivated.")
            return
        }
        val listenList: List<String> = getSystemBook().read("notify_listen_list", ArrayList())!!
        if (!listenList.contains(packageName)) {
            Log.i(TAG, "[$packageName] Not in the list of listening packages.")
            return
        }
        val extras = sbn.notification.extras!!
        var appName: String? = "unknown"
        Log.d(TAG, "onNotificationPosted: $appNameList")
        if (appNameList.containsKey(packageName)) {
            appName = appNameList[packageName]
        } else {
            val pm = context.packageManager
            try {
                val applicationInfo = pm.getApplicationInfo(sbn.packageName, 0)
                appName = pm.getApplicationLabel(applicationInfo) as String
                appNameList[packageName] = appName
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }
        }
        val title = extras.getString(Notification.EXTRA_TITLE, "None")
        val content = extras.getString(Notification.EXTRA_TEXT, "None")
        val botToken = settings.botToken
        val chatId = settings.chatId
        val requestUri = getUrl(botToken, "sendMessage")
        val requestBody = RequestMessage()
        requestBody.chat_id = chatId
        requestBody.text = """
            ${getString(R.string.receive_notification_title)}
            ${getString(R.string.app_name_title)}$appName
            ${getString(R.string.title)}$title
            ${getString(R.string.content)}$content
            """.trimIndent()
        val body = requestBody.toRequestBody()
        val okhttpClient = getOkhttpObj(settings.isDnsOverHttp)
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        val errorHead = "Send notification failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                LogUtils.writeLog(context, errorHead + e.message)
                addResendLoop(context!!, requestBody.text)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body ?: return
                val result = responseBody.string()
                if (response.code != 200) {
                    LogUtils.writeLog(context, errorHead + response.code + " " + result)
                    addResendLoop(context, requestBody.text)
                }
            }
        })
    }

    companion object {
        private val appNameList: MutableMap<String, String?> = HashMap()
        const val TAG = "notification_receiver"
    }
}
