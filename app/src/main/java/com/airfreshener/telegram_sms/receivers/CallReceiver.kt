package com.airfreshener.telegram_sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.airfreshener.telegram_sms.receivers.listeners.CallStatusListener
import io.paperdb.Paper
import java.util.*

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Paper.init(context)
        Log.d(TAG, "Receive action: " + intent.action)
        when (Objects.requireNonNull(intent.action)) {
            PHONE_STATE -> {
                if (intent.getStringExtra("incoming_number") != null) {
                    incomingNumber = intent.getStringExtra("incoming_number")
                }
                val telephony = context
                    .getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val customPhoneListener = CallStatusListener(context, slot, incomingNumber)
                telephony?.listen(customPhoneListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
            SUBSCRIPTION_PHONE_STATE -> slot = intent.getIntExtra("slot", -1)
        }
    }

    companion object {
        private var slot = 0
        private var incomingNumber: String? = null

        private const val PHONE_STATE = "android.intent.action.PHONE_STATE"
        private const val SUBSCRIPTION_PHONE_STATE = "android.intent.action.SUBSCRIPTION_PHONE_STATE"
        private const val TAG = "CallReceiver"
    }
}
