package com.qwe7002.telegram_sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.static_class.Other
import com.qwe7002.telegram_sms.static_class.Phone
import com.qwe7002.telegram_sms.static_class.Resend
import com.qwe7002.telegram_sms.static_class.SMS
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.value.Const
import com.tencent.mmkv.MMKV
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Objects


class WAPReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "WAPReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        MMKV.initialize(context)
        val action = intent.action
        Log.d(TAG, "Receive action: $action")

        if (action != "android.provider.Telephony.WAP_PUSH_RECEIVED" &&
            action != "android.provider.Telephony.WAP_PUSH_DELIVER") {
            return
        }

        val preferences = MMKV.defaultMMKV()
        if (!preferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, MMS receiver is deactivated.")
            return
        }

        val contentType = intent.getStringExtra("contentType") ?: intent.type
        if (contentType != "application/vnd.wap.mms-message") {
            Log.d(TAG, "Not an MMS message, content type: $contentType")
            return
        }

        Log.i(TAG, "MMS received, processing...")

        val extras = intent.extras ?: return
        val pdu = intent.getByteArrayExtra("data")
        if (pdu == null) {
            Log.e(TAG, "MMS PDU data is null")
            return
        }

        // Get slot information
        var intentSlot = extras.getInt("slot", -1)
        val subId = extras.getInt("subscription", -1)
        if (Other.getActiveCard(context) >= 2 && intentSlot == -1) {
            val manager = SubscriptionManager.from(context)
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                try {
                    val info = manager.getActiveSubscriptionInfo(subId)
                    if (info != null) {
                        intentSlot = info.simSlotIndex
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get subscription info: ${e.message}")
                }
            }
        }
        val slot = intentSlot
        val dualSim = if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Phone.getSimDisplayName(context, slot)
        } else {
            "Unknown"
        }

        // Parse MMS notification
        val mmsInfo = parseMmsNotification(pdu)

        val botToken = preferences.getString("bot_token", "")
        val chatId = preferences.getString("chat_id", "")
        val messageThreadId = preferences.getString("message_thread_id", "")
        val requestUri = Network.getUrl(botToken.toString(), "sendMessage")

        val requestBody = RequestMessage()
        requestBody.chatId = chatId.toString()
        requestBody.messageThreadId = messageThreadId.toString()

        val values = mapOf(
            "SIM" to dualSim,
            "From" to mmsInfo.from,
            "Subject" to mmsInfo.subject,
            "ContentType" to mmsInfo.contentType,
            "Size" to mmsInfo.messageSize
        )

        requestBody.text = Template.render(context, "TPL_received_mms", values)
        val requestBodyText = requestBody.text

        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(Const.JSON)
        val okhttpObj = Network.getOkhttpObj(
            preferences.getBoolean("doh_switch", true)
        )
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpObj.newCall(request)
        val errorHead = "Send MMS forward failed:"

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Log.e(TAG, errorHead + e.message)
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.SEND_SMS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    SMS.fallbackSMS(requestBodyText, subId)
                }
                Resend.addResendLoop(context, requestBody.text)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = Objects.requireNonNull(response.body).string()
                if (response.code != 200) {
                    Log.e(TAG, errorHead + response.code + " " + result)
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.SEND_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        SMS.fallbackSMS(requestBodyText, subId)
                    }
                    Resend.addResendLoop(context, requestBody.text)
                } else {
                    Log.i(TAG, "MMS notification forwarded successfully")
                }
            }
        })
    }

    /**
     * Parse MMS notification PDU to extract basic information
     */
    private fun parseMmsNotification(pdu: ByteArray): MmsInfo {
        val mmsInfo = MmsInfo()
        var i = 0

        try {
            while (i < pdu.size) {
                val header = pdu[i].toInt() and 0xFF
                i++

                when (header) {
                    // X-Mms-Message-Type (0x8C)
                    0x8C -> {
                        if (i < pdu.size) {
                            mmsInfo.messageType = pdu[i].toInt() and 0xFF
                            i++
                        }
                    }
                    // X-Mms-Transaction-Id (0x98)
                    0x98 -> {
                        val result = readTextString(pdu, i)
                        mmsInfo.transactionId = result.first
                        i = result.second
                    }
                    // X-Mms-MMS-Version (0x8D)
                    0x8D -> {
                        if (i < pdu.size) {
                            i++ // Skip version byte
                        }
                    }
                    // From (0x89)
                    0x89 -> {
                        val result = parseEncodedStringValue(pdu, i)
                        mmsInfo.from = result.first
                        i = result.second
                    }
                    // Subject (0x96)
                    0x96 -> {
                        val result = parseEncodedStringValue(pdu, i)
                        mmsInfo.subject = result.first
                        i = result.second
                    }
                    // X-Mms-Content-Location (0x83)
                    0x83 -> {
                        val result = readTextString(pdu, i)
                        mmsInfo.contentLocation = result.first
                        i = result.second
                    }
                    // X-Mms-Message-Size (0x8E)
                    0x8E -> {
                        val result = parseLongInteger(pdu, i)
                        mmsInfo.messageSize = formatFileSize(result.first)
                        i = result.second
                    }
                    // X-Mms-Expiry (0x88)
                    0x88 -> {
                        val result = parseValueLength(pdu, i)
                        i = result.second + result.first.toInt()
                    }
                    // Content-Type (0x84)
                    0x84 -> {
                        val result = parseContentType(pdu, i)
                        mmsInfo.contentType = result.first
                        i = result.second
                    }
                    // Date (0x85)
                    0x85 -> {
                        val result = parseLongInteger(pdu, i)
                        i = result.second
                    }
                    else -> {
                        // Try to skip unknown headers
                        if (header >= 0x80) {
                            // Well-known header, try to read value
                            if (i < pdu.size) {
                                val valueType = pdu[i].toInt() and 0xFF
                                if (valueType < 0x1F) {
                                    // Value-length format
                                    val result = parseValueLength(pdu, i)
                                    i = result.second + result.first.toInt()
                                } else if (valueType >= 0x80) {
                                    i++ // Short integer
                                } else {
                                    // Text or encoded string
                                    val result = readTextString(pdu, i)
                                    i = result.second
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MMS PDU: ${e.message}")
        }

        // Clean up from address
        if (mmsInfo.from.isNotEmpty()) {
            mmsInfo.from = cleanPhoneNumber(mmsInfo.from)
        }

        if (mmsInfo.from.isEmpty()) {
            mmsInfo.from = "Unknown"
        }
        if (mmsInfo.subject.isEmpty()) {
            mmsInfo.subject = "(No Subject)"
        }
        if (mmsInfo.messageSize.isEmpty()) {
            mmsInfo.messageSize = "Unknown"
        }
        if (mmsInfo.contentType.isEmpty()) {
            mmsInfo.contentType = "application/vnd.wap.multipart.mixed"
        }

        return mmsInfo
    }

    /**
     * Read text string from PDU
     */
    private fun readTextString(pdu: ByteArray, startIndex: Int): Pair<String, Int> {
        var i = startIndex
        val sb = StringBuilder()

        while (i < pdu.size && pdu[i].toInt() != 0) {
            sb.append(pdu[i].toInt().toChar())
            i++
        }
        if (i < pdu.size) {
            i++ // Skip null terminator
        }

        return Pair(sb.toString(), i)
    }

    /**
     * Parse encoded string value (with charset)
     */
    private fun parseEncodedStringValue(pdu: ByteArray, startIndex: Int): Pair<String, Int> {
        var i = startIndex

        if (i >= pdu.size) {
            return Pair("", i)
        }

        val firstByte = pdu[i].toInt() and 0xFF

        // Check if it's a value-length encoded string
        if (firstByte < 0x1F) {
            // Value-length followed by charset and text
            val valueLength = firstByte
            i++

            if (i >= pdu.size) {
                return Pair("", i)
            }

            // Check for address-present token (0x80)
            val token = pdu[i].toInt() and 0xFF
            if (token == 0x80) {
                i++
                // Read the encoded address
                val endIndex = minOf(i + valueLength - 1, pdu.size)
                val sb = StringBuilder()
                while (i < endIndex && pdu[i].toInt() != 0) {
                    val c = pdu[i].toInt() and 0xFF
                    if (c >= 0x20 && c < 0x7F) {
                        sb.append(c.toChar())
                    }
                    i++
                }
                if (i < pdu.size && pdu[i].toInt() == 0) {
                    i++
                }
                return Pair(sb.toString(), i)
            } else {
                // Skip charset if present
                if (token >= 0x80) {
                    i++
                }
                val sb = StringBuilder()
                val endIndex = minOf(startIndex + valueLength + 1, pdu.size)
                while (i < endIndex && pdu[i].toInt() != 0) {
                    val c = pdu[i].toInt() and 0xFF
                    if (c >= 0x20 && c < 0x7F) {
                        sb.append(c.toChar())
                    }
                    i++
                }
                if (i < pdu.size && pdu[i].toInt() == 0) {
                    i++
                }
                return Pair(sb.toString(), i)
            }
        } else if (firstByte == 0x1F) {
            // Quote followed by length
            i++
            if (i >= pdu.size) {
                return Pair("", i)
            }
            val length = pdu[i].toInt() and 0x7F
            i++
            val sb = StringBuilder()
            val endIndex = minOf(i + length, pdu.size)
            while (i < endIndex && pdu[i].toInt() != 0) {
                sb.append(pdu[i].toInt().toChar())
                i++
            }
            if (i < pdu.size && pdu[i].toInt() == 0) {
                i++
            }
            return Pair(sb.toString(), i)
        } else {
            // Plain text string
            return readTextString(pdu, i)
        }
    }

    /**
     * Parse value-length format
     */
    private fun parseValueLength(pdu: ByteArray, startIndex: Int): Pair<Long, Int> {
        var i = startIndex

        if (i >= pdu.size) {
            return Pair(0L, i)
        }

        val firstByte = pdu[i].toInt() and 0xFF

        if (firstByte < 0x1F) {
            i++
            return Pair(firstByte.toLong(), i)
        } else if (firstByte == 0x1F) {
            i++
            if (i >= pdu.size) {
                return Pair(0L, i)
            }
            val length = pdu[i].toInt() and 0x7F
            i++
            return Pair(length.toLong(), i)
        }

        return Pair(0L, i)
    }

    /**
     * Parse long integer from PDU
     */
    private fun parseLongInteger(pdu: ByteArray, startIndex: Int): Pair<Long, Int> {
        var i = startIndex

        if (i >= pdu.size) {
            return Pair(0L, i)
        }

        val firstByte = pdu[i].toInt() and 0xFF

        // Short-length format (1-30 bytes)
        if (firstByte <= 30) {
            val length = firstByte
            i++
            var value = 0L
            repeat(length) {
                if (i < pdu.size) {
                    value = (value shl 8) or (pdu[i].toLong() and 0xFF)
                    i++
                }
            }
            return Pair(value, i)
        }

        return Pair(0L, i)
    }

    /**
     * Parse content type from PDU
     */
    private fun parseContentType(pdu: ByteArray, startIndex: Int): Pair<String, Int> {
        var i = startIndex

        if (i >= pdu.size) {
            return Pair("", i)
        }

        val firstByte = pdu[i].toInt() and 0xFF

        // Well-known media type
        if (firstByte >= 0x80) {
            i++
            val mediaType = when (firstByte and 0x7F) {
                0x23 -> "application/vnd.wap.multipart.mixed"
                0x24 -> "application/vnd.wap.multipart.related"
                0x25 -> "application/vnd.wap.multipart.alternative"
                else -> "application/vnd.wap.multipart.mixed"
            }
            return Pair(mediaType, i)
        }

        // Text format content type
        return readTextString(pdu, i)
    }

    /**
     * Format file size to human readable format
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    /**
     * Clean phone number from address string
     */
    private fun cleanPhoneNumber(address: String): String {
        // Remove /TYPE=PLMN suffix and other type suffixes
        var result = address
            .replace("/TYPE=PLMN", "")
            .replace("/TYPE=IPv4", "")
            .replace("/TYPE=IPv6", "")
            .trim()

        // Extract phone number or email from the string
        val phonePattern = Regex("[+]?[0-9]{6,15}")
        val emailPattern = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")

        val phoneMatch = phonePattern.find(result)
        val emailMatch = emailPattern.find(result)

        result = when {
            phoneMatch != null -> phoneMatch.value
            emailMatch != null -> emailMatch.value
            else -> result
        }

        return result
    }

    /**
     * Data class to hold MMS information
     */
    private data class MmsInfo(
        var messageType: Int = 0,
        var transactionId: String = "",
        var from: String = "",
        var subject: String = "",
        var contentLocation: String = "",
        var messageSize: String = "",
        var contentType: String = ""
    )
}
