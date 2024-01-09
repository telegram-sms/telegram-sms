package com.airfreshener.telegram_sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.airfreshener.telegram_sms.utils.ContextUtils.app
import com.airfreshener.telegram_sms.utils.PaperUtils.DEFAULT_BOOK
import com.airfreshener.telegram_sms.utils.PaperUtils.tryRead
import com.airfreshener.telegram_sms.utils.ResendUtils
import com.airfreshener.telegram_sms.utils.ServiceUtils

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Receive action: " + intent.action)
        val prefsRepository = context.app().prefsRepository
        val logRepository = context.app().logRepository

        if (prefsRepository.getInitialized()) {
            val settings = prefsRepository.getSettings()
            logRepository.writeLog("Received [${intent.action}] broadcast, starting background service.")
            ServiceUtils.startService(context, settings)
            if (DEFAULT_BOOK.tryRead("resend_list", ArrayList<Any>()).size != 0) {
                Log.d(TAG, "An unsent message was detected, and the automatic resend process was initiated.")
                ResendUtils.startResend(context)
            }
        }
    }

    companion object {
        private const val TAG = "boot_receiver"
    }
}
