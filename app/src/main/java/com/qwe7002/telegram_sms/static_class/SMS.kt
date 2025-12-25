package com.qwe7002.telegram_sms.static_class

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.gson.Gson
import com.qwe7002.telegram_sms.R
import com.qwe7002.telegram_sms.SMSSendResultReceiver
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.value.Const
import com.tencent.mmkv.MMKV
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects

data class SmsInfo(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int // 1=inbox, 2=sent
) {
    fun getTypeString(): String = if (type == 1) "inbox" else "sent"
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(date))
    }
}

object SMS {
    private const val TAG = "SMS"

    @JvmStatic
    fun isDefaultSmsApp(context: Context): Boolean {
        return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

    @JvmStatic
    @SuppressLint("Range")
    @RequiresPermission(Manifest.permission.READ_SMS)
    fun getSmsList(
        context: Context,
        type: String = "all",
        page: Int = 0,
        pageSize: Int = 5
    ): Pair<List<SmsInfo>, Int> {
        val smsList = mutableListOf<SmsInfo>()
        val uri = when (type.lowercase()) {
            "inbox" -> Uri.parse("content://sms/inbox")
            "sent" -> Uri.parse("content://sms/sent")
            else -> Uri.parse("content://sms")
        }

        val cursor = context.contentResolver.query(
            uri,
            arrayOf("_id", "address", "body", "date", "type"),
            null,
            null,
            "date DESC"
        )

        var totalCount = 0
        cursor?.use {
            totalCount = it.count
            val startPos = page * pageSize
            if (startPos < totalCount && it.moveToPosition(startPos)) {
                var count = 0
                do {
                    val id = it.getLong(it.getColumnIndex("_id"))
                    val address = it.getString(it.getColumnIndex("address")) ?: ""
                    val body = it.getString(it.getColumnIndex("body")) ?: ""
                    val date = it.getLong(it.getColumnIndex("date"))
                    val smsType = it.getInt(it.getColumnIndex("type"))
                    smsList.add(SmsInfo(id, address, body, date, smsType))
                    count++
                } while (count < pageSize && it.moveToNext())
            }
        }

        val totalPages = (totalCount + pageSize - 1) / pageSize
        return Pair(smsList, totalPages)
    }

    @JvmStatic
    @SuppressLint("Range")
    @RequiresPermission(Manifest.permission.READ_SMS)
    fun getSmsById(context: Context, id: Long): SmsInfo? {
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms"),
            arrayOf("_id", "address", "body", "date", "type"),
            "_id = ?",
            arrayOf(id.toString()),
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                return SmsInfo(
                    it.getLong(it.getColumnIndex("_id")),
                    it.getString(it.getColumnIndex("address")) ?: "",
                    it.getString(it.getColumnIndex("body")) ?: "",
                    it.getLong(it.getColumnIndex("date")),
                    it.getInt(it.getColumnIndex("type"))
                )
            }
        }
        return null
    }

    @JvmStatic
    fun deleteSmsById(context: Context, id: Long): Boolean {
        if (!isDefaultSmsApp(context)) {
            Log.w(TAG, "Cannot delete SMS: not default SMS app")
            return false
        }
        return try {
            val rowsDeleted = context.contentResolver.delete(
                Uri.parse("content://sms"),
                "_id = ?",
                arrayOf(id.toString())
            )
            rowsDeleted > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete SMS: ${e.message}", e)
            false
        }
    }

    @JvmStatic
    @RequiresPermission(allOf = [Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_NUMBERS, Manifest.permission.READ_PHONE_STATE])
    fun sendSms(context: Context, sendTo: String, content: String, slot: Int, subId: Int) {
        send(context, sendTo, content, slot, subId, -1)
    }

    @JvmStatic
    @SuppressLint("UnspecifiedImmutableFlag", "UnspecifiedRegisterReceiverFlag")
    @RequiresPermission(allOf = [Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS, Manifest.permission.READ_PHONE_STATE])
    fun send(
        context: Context,
        sendTo: String,
        content: String,
        slot: Int,
        subId: Int,
        contextId: Long
    ) {
        var messageId = contextId
        if (!Other.isPhoneNumber(sendTo)) {
            Log.w("SMS", "[$sendTo] is an illegal phone number")
            return
        }
        val preferences = MMKV.defaultMMKV()
        val botToken = preferences.getString("bot_token", "")!!
        val chatId = preferences.getString("chat_id", "")!!
        val messageThreadId = preferences.getString("message_thread_id", "")!!
        var requestUri = Network.getUrl(botToken, "sendMessage")
        if (messageId != -1L) {
            Log.d(Const.TAG, "Find the message_id and switch to edit mode.")
            requestUri = Network.getUrl(botToken, "editMessageText")
        }
        val requestBody = RequestMessage()
        requestBody.chatId = chatId
        @Suppress("DEPRECATION") val smsManager = if (subId == -1) {
            SmsManager.getDefault()
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
        val dualSim = Phone.getSimDisplayName(context, slot)
        val values = mapOf(
            "SIM" to dualSim,
            "To" to sendTo,
            "Content" to content,
        )
        val sendContent = Template.render(context, "TPL_send_sms", values)
        requestBody.text =
            "$sendContent\n${context.getString(R.string.status)}${context.getString(R.string.sending)}"
        requestBody.messageId = messageId
        requestBody.messageThreadId = messageThreadId
        val gson = Gson()
        val requestBodyRaw = gson.toJson(requestBody)
        val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
        val okhttpClient = Network.getOkhttpObj(
            preferences.getBoolean("doh_switch", true)
        )
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpClient.newCall(request)
        try {
            val response = call.execute()
            if (response.code != 200) {
                throw IOException(response.code.toString())
            }
            if (messageId == -1L) {
                messageId = Other.getMessageId(Objects.requireNonNull(response.body).string())
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("SMS", "failed to send message:" + e.message)
        }
        val divideContents = smsManager.divideMessage(content)
        val sendReceiverList = ArrayList<PendingIntent>()
        val filter = IntentFilter("send_sms")
        val receiver: BroadcastReceiver = SMSSendResultReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        Log.d(context::class.simpleName, "onReceive: $messageId")
        val intent = Intent("send_sms")
        intent.putExtra("message_id", messageId)
        intent.putExtra("message_text", sendContent)
        intent.putExtra("sub_id", smsManager.subscriptionId)
        val sentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                context,
                messageId.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        sendReceiverList.add(sentIntent)
        smsManager.sendMultipartTextMessage(
            sendTo,
            null,
            divideContents,
            sendReceiverList,
            null
        )
    }

    @RequiresPermission(Manifest.permission.SEND_SMS)
    fun fallbackSMS(content: String?, subId: Int) {
        val preferences = MMKV.defaultMMKV()
        val trustNumber = preferences.getString("trusted_phone_number", null)
        if (trustNumber == null) {
            Log.i(this::class.simpleName, "The trusted number is empty.")
            return
        }
        if (!preferences.getBoolean("fallback_sms", false)) {
            Log.i(this::class.simpleName, "SMS fallback is not turned on.")
            return
        }
        val smsManager = if (subId == -1) {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(SmsManager.getDefaultSmsSubscriptionId())
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
        val divideContents = smsManager.divideMessage(content)
        smsManager.sendMultipartTextMessage(trustNumber, null, divideContents, null, null)
    }
}
