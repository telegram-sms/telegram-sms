package com.airfreshener.telegram_sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("WapReceiver", "Receive action: " + intent.action)
    }
}
