package com.airfreshener.telegram_sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.airfreshener.telegram_sms.utils.LogUtils
import com.airfreshener.telegram_sms.utils.ResendUtils
import com.airfreshener.telegram_sms.utils.ServiceUtils
import io.paperdb.Paper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Receive action: " + intent.action)
        val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        if (sharedPreferences.getBoolean("initialized", false)) {
            Paper.init(context)
            LogUtils.writeLog(
                context,
                "Received [${intent.action}] broadcast, starting background service."
            )
            ServiceUtils.startService(
                context,
                sharedPreferences.getBoolean("battery_monitoring_switch", false),
                sharedPreferences.getBoolean("chat_command", false)
            )
            if (Paper.book().read("resend_list", ArrayList<Any>())!!.size != 0) {
                Log.d(TAG, "An unsent message was detected, and the automatic resend process was initiated.")
                ResendUtils.startResend(context)
            }
        }
    }

    companion object {
        private const val TAG = "boot_receiver"
    }
}
