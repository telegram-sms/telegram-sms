package com.qwe7002.telegram_sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WAPReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == "android.provider.Telephony.WAP_PUSH_RECEIVED") {
            val pdu = intent.getByteArrayExtra("data")
            val contentType = intent.getStringExtra("contentType")
            if (contentType == "application/vnd.wap.mms-message") {
                Log.i("WAPReceiver", "onReceive: MMS received.")
/*                val mmsIntent = Intent(context, MmsReceiver::class.java)
                mmsIntent.putExtra("data", pdu)
                context.sendBroadcast(mmsIntent)*/
            }
        }

    }
}
