package com.qwe7002.telegram_sms

import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.qwe7002.telegram_sms.MMKV.MMKVConst
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.TelegramApi
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.value.CcType
import com.tencent.mmkv.MMKV
import com.google.gson.Gson
import com.qwe7002.telegram_sms.value.Const

class NotificationService : NotificationListenerService() {
    lateinit var preferences: MMKV

    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(applicationContext)
        preferences = MMKV.defaultMMKV()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        Log.d(Const.TAG, "onNotificationPosted: $packageName")

        if (!preferences.getBoolean("initialized", false)) {
            Log.i(Const.TAG, "Uninitialized, Notification receiver is deactivated.")
            return
        }
        val notifyMMKV = MMKV.mmkvWithID(MMKVConst.NOTIFY_ID)
        val notifyListStr = notifyMMKV.getString("listen_list", "[]")
        val listenList: List<String> =
            Gson().fromJson(notifyListStr, Array<String>::class.java).toList()

        if (!listenList.contains(packageName)) {
            Log.i(Const.TAG, "[$packageName] Not in the list of listening packages.")
            return
        }
        val extras = sbn.notification.extras!!
        var appName = "unknown"
        Log.d(Const.TAG, "onNotificationPosted: $appNameList")
        if (appNameList.containsKey(packageName)) {
            appName = appNameList[packageName].toString()
        } else {
            val pm = applicationContext.packageManager
            try {
                val applicationInfo = pm.getApplicationInfo(sbn.packageName, 0)
                appName = pm.getApplicationLabel(applicationInfo) as String
                appNameList[packageName] = appName
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(Const.TAG, "onNotificationPosted: ", e)
            }
        }

        val title = extras.getString(Notification.EXTRA_TITLE)
            ?: getString(R.string.unable_to_obtain_information)
        val content = extras.getString(Notification.EXTRA_TEXT)
            ?: getString(R.string.unable_to_obtain_information)

        val requestBody = RequestMessage()
        requestBody.text = Template.render(
            applicationContext,
            "TPL_notification",
            mapOf("APP" to appName, "Title" to title, "Description" to content)
        )
        CcSendJob.startJob(
            applicationContext,
            CcType.NOTIFICATION,
            applicationContext.getString(R.string.Notification_Listener_title),
            requestBody.text
        )

        TelegramApi.sendMessage(
            context = applicationContext,
            requestBody = requestBody,
            errorTag = "NotificationService",
            fallbackSubId = -1  // No SMS fallback for notifications
        )
    }


    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    companion object {
        var appNameList: MutableMap<String, String?> = HashMap()
    }
}
