package com.qwe7002.telegram_sms

import android.app.Notification
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson
import com.qwe7002.telegram_sms.config.proxy
import com.qwe7002.telegram_sms.data_structure.RequestMessage
import com.qwe7002.telegram_sms.static_class.Logs
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.static_class.Other
import com.qwe7002.telegram_sms.static_class.Resend
import com.qwe7002.telegram_sms.value.constValue
import com.qwe7002.telegram_sms.value.notifyId
import io.paperdb.Paper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Objects

class NotificationService : NotificationListenerService() {
    val TAG: String = "notification_receiver"
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        Paper.init(applicationContext)
        sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        val notification = Other.getNotificationObj(
            applicationContext,
            getString(R.string.Notification_Listener_title)
        )
        startForeground(notifyId.NOTIFICATION_LISTENER_SERVICE, notification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        Log.d(TAG, "onNotificationPosted: $packageName")

        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, Notification receiver is deactivated.")
            return
        }

        val listenList: List<String> =
            Paper.book("system_config").read("notify_listen_list", ArrayList())!!
        if (!listenList.contains(packageName)) {
            Log.i(TAG, "[$packageName] Not in the list of listening packages.")
            return
        }
        val extras = sbn.notification.extras!!
        var appName = "unknown"
        Log.d(TAG, "onNotificationPosted: $appNameList")
        if (appNameList.containsKey(packageName)) {
            appName = appNameList[packageName].toString()
        } else {
            val pm = applicationContext.packageManager
            try {
                val applicationInfo = pm.getApplicationInfo(sbn.packageName, 0)
                appName = pm.getApplicationLabel(applicationInfo) as String
                appNameList[packageName] = appName
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(TAG, "onNotificationPosted: ", e)
            }
        }
        val title = extras.getString(Notification.EXTRA_TITLE, "None")
        val content = extras.getString(Notification.EXTRA_TEXT, "None")

        val botToken = sharedPreferences.getString("bot_token", "")
        val chatId = sharedPreferences.getString("chat_id", "")
        val messageThreadId = sharedPreferences.getString("message_thread_id", "")
        val requestUri = Network.getUrl(botToken.toString(), "sendMessage")
        val requestBody = RequestMessage()
        requestBody.chatId = chatId.toString()
        requestBody.messageThreadId = messageThreadId.toString()
/*        requestBody.text = """
            ${getString(R.string.receive_notification_title)}
            ${getString(R.string.app_name_title)}$appName
            ${getString(R.string.title)}$title
            ${getString(R.string.content)}$content
            """.trimIndent()*/
        requestBody.text = getString(R.string.receive_notification_title) + "\n" +
                getString(R.string.app_name_title) + appName + "\n" +
                getString(R.string.title) + title + "\n" +
                getString(R.string.content) + content
        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(constValue.JSON)
        val okhttpObj = Network.getOkhttpObj(
            sharedPreferences.getBoolean("doh_switch", true),
            Paper.book("system_config").read("proxy_config", proxy())
        )
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpObj.newCall(request)
        val errorHead = "Send notification failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "onFailure: ", e)
                Logs.writeLog(applicationContext, errorHead + e.message)
                Resend.addResendLoop(applicationContext, requestBody.text)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val result = Objects.requireNonNull(response.body).string()
                if (response.code != 200) {
                    Logs.writeLog(applicationContext, errorHead + response.code + " " + result)
                    Resend.addResendLoop(applicationContext, requestBody.text)
                }
            }
        })
    }


    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    companion object {
        var appNameList: MutableMap<String, String?> = HashMap()
    }
}
