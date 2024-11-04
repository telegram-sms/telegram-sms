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
import com.qwe7002.telegram_sms.config.proxy
import com.qwe7002.telegram_sms.data_structure.RequestMessage
import com.qwe7002.telegram_sms.value.constValue
import io.paperdb.Paper
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Objects

object ussd {
    @JvmStatic
    @RequiresApi(api = Build.VERSION_CODES.O)
    fun sendUssd(context: Context, ussdRaw: String, subId: Int) {
        val TAG = "send_ussd"
        val ussd = other.getNineKeyMapConvert(ussdRaw)
        var tm =
            checkNotNull(context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
        if (subId != -1) {
            tm = tm.createForSubscriptionId(subId)
        }
        val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "send_ussd: No permission.")
        }

        val botToken = sharedPreferences.getString("bot_token", "")!!
        val chatId = sharedPreferences.getString("chat_id", "")!!
        val requestUri = network.getUrl(botToken, "sendMessage")
        val requestBody = RequestMessage()
        requestBody.chatId = chatId
        requestBody.text = """
            ${context.getString(R.string.send_ussd_head)}
            ${context.getString(R.string.ussd_code_running)}
            """.trimIndent()
        val requestBodyRaw = Gson().toJson(requestBody)
        val body: RequestBody = requestBodyRaw.toRequestBody(constValue.JSON)
        val okhttpClient = network.getOkhttpObj(
            sharedPreferences.getBoolean("doh_switch", true),
            Paper.book("system_config").read("proxy_config", proxy())
        )
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)

        Thread {
            var messageId = -1L
            try {
                val response = call.execute()
                messageId = other.getMessageId(
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
                    USSDCallBack(context, sharedPreferences, messageId),
                    handler
                )
                Looper.loop()
            }
        }.start()
    }
}