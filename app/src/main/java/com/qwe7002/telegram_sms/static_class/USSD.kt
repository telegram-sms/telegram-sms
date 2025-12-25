package com.qwe7002.telegram_sms.static_class

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
import com.google.gson.Gson
import com.qwe7002.telegram_sms.R
import com.qwe7002.telegram_sms.USSDCallBack
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.value.Const
import com.tencent.mmkv.MMKV
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Objects

object USSD {
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.O)
    fun sendUssd(context: Context, ussdRaw: String, subId: Int) {
        val ussd = Other.getNineKeyMapConvert(ussdRaw)
        var tm =
            checkNotNull(context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
        if (subId != -1) {
            tm = tm.createForSubscriptionId(subId)
        }
        val preferences = MMKV.defaultMMKV()
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(Const.TAG, "send_ussd: No permission.")
        }

        val botToken = preferences.getString("bot_token", "")!!
        val chatId = preferences.getString("chat_id", "")!!
        val messsageThreadId = preferences.getString("message_thread_id", "")!!
        val requestUri = Network.getUrl(botToken, "sendMessage")
        val requestBody = RequestMessage()
        requestBody.chatId = chatId
        requestBody.messageThreadId = messsageThreadId
        requestBody.text = Template.render(
            context,
            "TPL_send_USSD",
            mapOf("Request" to ussdRaw, "Response" to context.getString(R.string.ussd_code_running))
        )
        val requestBodyRaw = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
        val okhttpClient = Network.getOkhttpObj(
            preferences.getBoolean("doh_switch", true)
        )
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)

        Thread {
            var messageId = -1L
            try {
                val response = call.execute()
                messageId = Other.getMessageId(
                    Objects.requireNonNull(response.body).string()
                )
            } catch (e: IOException) {
                e.printStackTrace()
            }
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Looper.prepare()
                val handler = Handler(Looper.getMainLooper())
                tm.sendUssdRequest(
                    ussd,
                    USSDCallBack(context, messageId),
                    handler
                )
                Looper.loop()
            }
        }.start()
    }
}
