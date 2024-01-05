package com.airfreshener.telegram_sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.airfreshener.telegram_sms.utils.LogUtils
import com.airfreshener.telegram_sms.utils.ResendUtils
import com.airfreshener.telegram_sms.utils.ServiceUtils
import io.paperdb.Paper

class boot_receiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val TAG = "boot_receiver"
        Log.d(TAG, "Receive action: " + intent.action)
        val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        if (sharedPreferences.getBoolean("initialized", false)) {
            Paper.init(context)
            LogUtils.write_log(
                context,
                "Received [" + intent.action + "] broadcast, starting background service."
            )
            ServiceUtils.start_service(
                context,
                sharedPreferences.getBoolean("battery_monitoring_switch", false),
                sharedPreferences.getBoolean("chat_command", false)
            )
            if (Paper.book()
                    .read("resend_list", ArrayList<Any>())!!.size != 0
            ) {
                Log.d(
                    TAG,
                    "An unsent message was detected, and the automatic resend process was initiated."
                )
                ResendUtils.start_resend(context)
            }
        }
    }
}
