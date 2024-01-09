package com.airfreshener.telegram_sms.utils

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
import com.airfreshener.telegram_sms.TelegramSmsApp
import com.airfreshener.telegram_sms.UssdRequestCallback
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.ServiceUtils.telephonyManager
import okhttp3.Request
import java.io.IOException

object UssdUtils {
    @RequiresApi(api = Build.VERSION_CODES.O)
    fun sendUssd(context: Context, ussdRaw: String?, subId: Int) {
        val TAG = "sendUssd"
        val ussd = OtherUtils.getNineKeyMapConvert(ussdRaw!!)
        var tm: TelephonyManager = context.telephonyManager
        if (subId != -1) {
            tm = tm.createForSubscriptionId(subId)
        }
        val settings = (context.applicationContext as TelegramSmsApp).prefsRepository.getSettings()
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "send_ussd: No permission.")
        }
        val botToken = settings.botToken
        val chatId = settings.chatId
        val requestUri = NetworkUtils.getUrl(botToken, "sendMessage")
        val requestMessage = RequestMessage()
        requestMessage.chat_id = chatId
        requestMessage.text = """
            ${context.getString(R.string.send_ussd_head)}
            ${context.getString(R.string.ussd_code_running)}
            """.trimIndent()
        val body = requestMessage.toRequestBody()
        val okHttpClient = NetworkUtils.getOkhttpObj(settings)
        val request: Request = Request.Builder().url(requestUri).post(body).build()
        val call = okHttpClient.newCall(request)
        Thread {
            var messageId = -1L
            try {
                val response = call.execute()
                messageId = OtherUtils.getMessageId(response.body?.string())
            } catch (e: IOException) {
                e.printStackTrace()
            }
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Looper.prepare()
                tm.sendUssdRequest(
                    ussd,
                    UssdRequestCallback(context, settings, messageId),
                    Handler(requireNotNull(Looper.myLooper()))
                )
                Looper.loop()
            }
        }.start()
    }
}
