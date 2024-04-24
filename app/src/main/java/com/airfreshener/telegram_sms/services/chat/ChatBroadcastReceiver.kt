package com.airfreshener.telegram_sms.services.chat

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Process
import android.util.Log
import com.airfreshener.telegram_sms.common.data.LogRepository
import com.airfreshener.telegram_sms.utils.Consts
import com.airfreshener.telegram_sms.utils.NetworkUtils

class ChatBroadcastReceiver(
    private val service: ChatCommandService,
    private val logRepository: LogRepository,
) : android.content.BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive: " + intent.action)
        if (intent.action == null) {
            logRepository.writeLog("ChatCommandService, received action is null")
            return
        }
        when (intent.action) {
            Consts.BROADCAST_STOP_SERVICE -> {
                Log.i(TAG, "Received stop signal, quitting now...")
                service.stopSelf()
                Process.killProcess(Process.myPid())
            }

            ConnectivityManager.CONNECTIVITY_ACTION -> if (NetworkUtils.checkNetworkStatus(context)) {
                if (service.isThreadRunning().not()) {
                    logRepository.writeLog("Network connections has been restored.")
                    service.startThread()
                }
            }
        }
    }

    companion object {
        private val TAG = ChatBroadcastReceiver::class.java.simpleName
    }
}
