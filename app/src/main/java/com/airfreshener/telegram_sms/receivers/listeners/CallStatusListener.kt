package com.airfreshener.telegram_sms.receivers.listeners

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.utils.ContextUtils.app
import com.airfreshener.telegram_sms.utils.OtherUtils
import com.airfreshener.telegram_sms.utils.ResendUtils
import com.airfreshener.telegram_sms.utils.SmsUtils

class CallStatusListener(
    private val context: Context,
    private val slot: Int,
    incomingNumber: String?
) : PhoneStateListener() {

    private var lastReceiveStatus = TelephonyManager.CALL_STATE_IDLE
    private val incomingNumber: String
    private val logRepository = context.app().logRepository
    private val prefsRepository = context.app().prefsRepository
    private val telegramRepository = context.app().telegramRepository

    init {
        this.incomingNumber = incomingNumber ?: "-"
    }

    @Deprecated("Deprecated in Java")
    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
        if (lastReceiveStatus == TelephonyManager.CALL_STATE_RINGING
            && state == TelephonyManager.CALL_STATE_IDLE
        ) {

            if (!prefsRepository.getInitialized()) {
                Log.i(TAG, "Uninitialized, Phone receiver is deactivated.")
                return
            }
            val settings = prefsRepository.getSettings()
            val dualSim = OtherUtils.getDualSimCardDisplay(context, slot, settings.isDisplayDualSim)
            val message = "[" + dualSim + context.getString(R.string.missed_call_head) + "]" + "\n" +
                context.getString(R.string.Incoming_number) + incomingNumber
            telegramRepository.sendMessage(
                message = message,
                onSuccess = { messageId ->
                    if (!OtherUtils.isPhoneNumber(incomingNumber)) {
                        logRepository.writeLog("[$incomingNumber] Not a regular phone number.")
                    } else {
                        OtherUtils.addMessageList(messageId, incomingNumber, slot)
                    }
                },
                onFailure = {
                    SmsUtils.sendFallbackSms(context, message, OtherUtils.getSubId(context, slot))
                    ResendUtils.addResendLoop(context, message)
                }
            )
        }
        lastReceiveStatus = state
    }

    companion object {
        private const val TAG = "CallStatusListener"
    }
}
