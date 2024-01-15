package com.airfreshener.telegram_sms.services.chat

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.utils.Consts
import com.airfreshener.telegram_sms.utils.ContextUtils.app
import com.airfreshener.telegram_sms.utils.OtherUtils
import com.airfreshener.telegram_sms.utils.ServiceUtils.powerManager
import com.airfreshener.telegram_sms.utils.ServiceUtils.register
import com.airfreshener.telegram_sms.utils.ServiceUtils.stopForeground
import com.airfreshener.telegram_sms.utils.ServiceUtils.wifiManager

class ChatCommandService : Service() {

    private var threadMain: Thread? = null
    private var broadcastReceiver: ChatBroadcastReceiver? = null
    private var wakelock: WakeLock? = null
    private var wifiLock: WifiLock? = null
    private val prefsRepository by lazy { app().prefsRepository }
    private val logRepository by lazy { app().logRepository }
    private val telegramRepository by lazy { app().telegramRepository }
    private val controller by lazy {
        ChatServiceController(
            appContext = applicationContext,
            prefsRepository = prefsRepository,
            logRepository = logRepository,
            telegramRepository = telegramRepository,
            ussdRepository = app().ussdRepository,
        )
    }
    private val chatRunnable by lazy {
        ChatThreadMainRunnable(
            controller = controller,
            appContext = applicationContext,
            prefsRepository = prefsRepository,
            logRepository = logRepository,
        )
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val notification =
            OtherUtils.getNotificationObj(applicationContext, getString(R.string.chat_command_service_name))
        startForeground(Consts.ServiceNotifyId.CHAT_COMMAND, notification)
        return START_STICKY
    }

    @SuppressLint("InvalidWakeLockTag", "WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, WIFI_LOCK_TAG).apply {
            setReferenceCounted(false)
            if (!isHeld) acquire()
        }
        wakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            if (!isHeld) acquire()
        }
        startThread()
        broadcastReceiver = ChatBroadcastReceiver(
            service = this,
            logRepository = logRepository,
        ).also { receiver ->
            val intentFilter = IntentFilter().apply {
                addAction(Consts.BROADCAST_STOP_SERVICE)
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            }
            register(receiver, intentFilter)
        }
    }

    override fun onDestroy() {
        wifiLock?.release()
        wakelock?.release()
        broadcastReceiver?.let { unregisterReceiver(it) }
        chatRunnable.stopPolling()
        threadMain = null
        stopForeground()
        super.onDestroy()
    }

    fun isThreadRunning() = threadMain?.isAlive == true

    fun startThread() {
        threadMain = Thread(chatRunnable).apply { start() }
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        private const val WIFI_LOCK_TAG = "bot_command_polling_wifi"
        private const val WAKE_LOCK_TAG = "bot_command_polling"
    }
}
