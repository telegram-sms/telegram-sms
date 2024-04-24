package com.airfreshener.telegram_sms.common.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.utils.ServiceUtils.telephonyManager
import com.airfreshener.telegram_sms.utils.UssdRequestCallback

class UssdRepository(
    private val appContext: Context,
    private val logRepository: LogRepository,
    private val telegramRepository: TelegramRepository,
) {

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun sendUssd(ussd: String?, subId: Int) {
        val TAG = "sendUssd"
        if (ussd == null) {
            logRepository.writeLog("$TAG: ussd is null")
            return
        }
        val tm: TelephonyManager = if (subId != -1) {
            appContext.telephonyManager.createForSubscriptionId(subId)
        } else {
            appContext.telephonyManager
        }
        if (ActivityCompat.checkSelfPermission(
                appContext,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "send_ussd: No permission.")
        }

        // TODO fix it
        telegramRepository.sendMessage(
            message = appContext.getString(R.string.send_ussd_head) + "\n" +
                    appContext.getString(R.string.ussd_code_running),
            onSuccess = { messageId ->
                if (ActivityCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    tm.sendUssdRequest(
                        ussd,
                        UssdRequestCallback(appContext, messageId),
                        Handler(Looper.getMainLooper())
                    )
                }

            }
        )
    }
}
