package com.qwe7002.telegram_sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Objects
import java.util.concurrent.Executors


class WAPReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "WAPReceiver"
        private const val MMS_CONTENT_URI = "content://mms"
        private const val MMS_PART_URI = "content://mms/part"

        // MMS part content types for images
        private val IMAGE_CONTENT_TYPES = listOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/bmp",
            "image/webp"
        )
    }

    private val executor = Executors.newSingleThreadExecutor()

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

        // Parse MMS notification from PDU
        val mmsInfo = parseMmsNotification(pdu)

        // Delay to allow MMS to be stored in content provider
        Handler(Looper.getMainLooper()).postDelayed({
            executor.execute {
                processMMSWithAttachments(context, mmsInfo, dualSim, subId)
            }
        }, 5000) // 5 second delay to allow MMS download
    }

    /**
     * Process MMS with attachments from content provider
     */
    private fun processMMSWithAttachments(
        context: Context,
        mmsInfo: MmsInfo,
        dualSim: String,
        subId: Int
    ) {
        val preferences = MMKV.defaultMMKV()
        val botToken = preferences.getString("bot_token", "") ?: ""
        val chatId = preferences.getString("chat_id", "") ?: ""
        val messageThreadId = preferences.getString("message_thread_id", "") ?: ""

        // Try to get MMS from content provider
        val mmsData = getMmsFromContentProvider(context, mmsInfo)

        // Update mmsInfo with data from content provider if available
        if (mmsData.from.isNotEmpty()) {
            mmsInfo.from = mmsData.from
        }
        if (mmsData.subject.isNotEmpty()) {
            mmsInfo.subject = mmsData.subject
        }
        if (mmsData.textContent.isNotEmpty()) {
            mmsInfo.textContent = mmsData.textContent
        }

        val values = mapOf(
            "SIM" to dualSim,
            "From" to mmsInfo.from,
            "Subject" to mmsInfo.subject,
            "Content" to mmsInfo.textContent.ifEmpty { "(No text content)" },
            "ContentType" to mmsInfo.contentType,
            "Size" to mmsInfo.messageSize
        )

        val messageText = Template.render(context, "TPL_received_mms", values)

        // Check if there are images to send
        if (mmsData.images.isNotEmpty()) {
            // Send images with caption
            sendImagesToTelegram(context, botToken, chatId, messageThreadId, messageText, mmsData.images, subId)
        } else {
            // No images, send text message only
            sendTextMessage(context, botToken, chatId, messageThreadId, messageText, subId)
        }
    }

    /**
     * Get MMS data from content provider
     */
    private fun getMmsFromContentProvider(context: Context, mmsInfo: MmsInfo): MmsData {
        val mmsData = MmsData()

        try {
            // Find the latest MMS
            val mmsId = findLatestMmsId(context, mmsInfo.transactionId)
            if (mmsId == null) {
                Log.w(TAG, "Could not find MMS in content provider")
                return mmsData
            }

            Log.d(TAG, "Found MMS ID: $mmsId")

            // Get sender address
            mmsData.from = getMmsAddress(context, mmsId)

            // Get MMS parts (text and images)
            getMmsParts(context, mmsId, mmsData)

        } catch (e: Exception) {
            Log.e(TAG, "Error reading MMS from content provider: ${e.message}")
            e.printStackTrace()
        }

        return mmsData
    }

    /**
     * Find the latest MMS ID, optionally matching transaction ID
     */
    private fun findLatestMmsId(context: Context, transactionId: String?): String? {
        var mmsId: String? = null
        var cursor: Cursor? = null

        try {
            val uri = MMS_CONTENT_URI.toUri()
            val projection = arrayOf("_id", "tr_id", "sub", "date")

            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC LIMIT 5"
            )

            cursor?.let {
                while (it.moveToNext()) {
                    val id = it.getString(it.getColumnIndexOrThrow("_id"))
                    val trId = it.getString(it.getColumnIndexOrThrow("tr_id")) ?: ""

                    // If transaction ID matches or we don't have one, use this MMS
                    if (transactionId.isNullOrEmpty() || trId == transactionId) {
                        mmsId = id
                        break
                    }
                }

                // If no match found, use the most recent one
                if (mmsId == null && it.moveToFirst()) {
                    mmsId = it.getString(it.getColumnIndexOrThrow("_id"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding MMS ID: ${e.message}")
        } finally {
            cursor?.close()
        }

        return mmsId
    }

    /**
     * Get MMS sender address
     */
    private fun getMmsAddress(context: Context, mmsId: String): String {
        var address = ""
        var cursor: Cursor? = null

        try {
            val uri = "$MMS_CONTENT_URI/$mmsId/addr".toUri()
            cursor = context.contentResolver.query(
                uri,
                null,
                "type=137", // 137 = from address
                null,
                null
            )

            cursor?.let {
                if (it.moveToFirst()) {
                    address = it.getString(it.getColumnIndexOrThrow("address")) ?: ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS address: ${e.message}")
        } finally {
            cursor?.close()
        }

        return cleanPhoneNumber(address)
    }

    /**
     * Get MMS parts (text and images)
     */
    private fun getMmsParts(context: Context, mmsId: String, mmsData: MmsData) {
        var cursor: Cursor? = null

        try {
            val uri = "$MMS_CONTENT_URI/$mmsId/part".toUri()
            cursor = context.contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )

            cursor?.let {
                while (it.moveToNext()) {
                    val partId = it.getString(it.getColumnIndexOrThrow("_id"))
                    val contentType = it.getString(it.getColumnIndexOrThrow("ct")) ?: ""
                    val text = it.getString(it.getColumnIndexOrThrow("text"))
                    val name = it.getString(it.getColumnIndexOrThrow("name")) ?: "image"

                    when {
                        contentType == "text/plain" -> {
                            // Text part
                            if (!text.isNullOrEmpty()) {
                                mmsData.textContent = text
                            } else {
                                // Try to read from data
                                mmsData.textContent = readTextFromPart(context, partId)
                            }
                        }
                        contentType in IMAGE_CONTENT_TYPES -> {
                            // Image part
                            val imageData = readImageFromPart(context, partId)
                            if (imageData != null) {
                                val extension = getExtensionFromMimeType(contentType)
                                val fileName = if (name.contains(".")) name else "$name.$extension"
                                mmsData.images.add(MmsImage(fileName, contentType, imageData))
                                Log.d(TAG, "Found image: $fileName, size: ${imageData.size}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS parts: ${e.message}")
        } finally {
            cursor?.close()
        }
    }

    /**
     * Read text from MMS part
     */
    private fun readTextFromPart(context: Context, partId: String): String {
        var text = ""
        var inputStream: InputStream? = null

        try {
            val partUri = "$MMS_PART_URI/$partId".toUri()
            inputStream = context.contentResolver.openInputStream(partUri)
            inputStream?.let {
                val buffer = ByteArrayOutputStream()
                val data = ByteArray(1024)
                var count: Int
                while (it.read(data).also { c -> count = c } != -1) {
                    buffer.write(data, 0, count)
                }
                text = buffer.toString("UTF-8")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading text from part: ${e.message}")
        } finally {
            inputStream?.close()
        }

        return text
    }

    /**
     * Read image data from MMS part
     */
    private fun readImageFromPart(context: Context, partId: String): ByteArray? {
        var inputStream: InputStream? = null

        try {
            val partUri = "$MMS_PART_URI/$partId".toUri()
            inputStream = context.contentResolver.openInputStream(partUri)
            inputStream?.let {
                val buffer = ByteArrayOutputStream()
                val data = ByteArray(4096)
                var count: Int
                while (it.read(data).also { c -> count = c } != -1) {
                    buffer.write(data, 0, count)
                }
                return buffer.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading image from part: ${e.message}")
        } finally {
            inputStream?.close()
        }

        return null
    }

    /**
     * Get file extension from MIME type
     */
    private fun getExtensionFromMimeType(mimeType: String): String {
        return when (mimeType) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            "image/webp" -> "webp"
            else -> "jpg"
        }
    }

    /**
     * Send images to Telegram using sendPhoto API
     */
    private fun sendImagesToTelegram(
        context: Context,
        botToken: String,
        chatId: String,
        messageThreadId: String,
        caption: String,
        images: List<MmsImage>,
        subId: Int
    ) {
        val preferences = MMKV.defaultMMKV()
        val okhttpObj = Network.getOkhttpObj(preferences.getBoolean("doh_switch", true))

        // Send first image with caption, rest without
        images.forEachIndexed { index, image ->
            val imageCaption = if (index == 0) caption else ""
            sendSingleImage(context, botToken, chatId, messageThreadId, imageCaption, image, okhttpObj, subId)
        }
    }

    /**
     * Send a single image to Telegram
     */
    private fun sendSingleImage(
        context: Context,
        botToken: String,
        chatId: String,
        messageThreadId: String,
        caption: String,
        image: MmsImage,
        okhttpObj: okhttp3.OkHttpClient,
        subId: Int
    ) {
        val requestUri = Network.getUrl(botToken, "sendPhoto")

        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("photo", image.fileName,
                image.data.toRequestBody(image.contentType.toMediaType()))

        if (caption.isNotEmpty()) {
            multipartBuilder.addFormDataPart("caption", caption)
        }

        if (messageThreadId.isNotEmpty()) {
            multipartBuilder.addFormDataPart("message_thread_id", messageThreadId)
        }

        val requestBody = multipartBuilder.build()
        val request = Request.Builder()
            .url(requestUri)
            .post(requestBody)
            .build()

        val call = okhttpObj.newCall(request)
        val errorHead = "Send MMS image failed:"

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Log.e(TAG, errorHead + e.message)
                // Fallback to text message
                if (caption.isNotEmpty()) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.SEND_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        SMS.fallbackSMS(caption, subId)
                    }
                    Resend.addResendLoop(context, caption)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = Objects.requireNonNull(response.body).string()
                if (response.code != 200) {
                    Log.e(TAG, errorHead + response.code + " " + result)
                    // Fallback to text message on error
                    if (caption.isNotEmpty()) {
                        sendTextMessage(context, botToken, chatId, messageThreadId, caption, subId)
                    }
                } else {
                    Log.i(TAG, "MMS image sent successfully")
                }
            }
        })
    }

    /**
     * Send text message to Telegram
     */
    private fun sendTextMessage(
        context: Context,
        botToken: String,
        chatId: String,
        messageThreadId: String,
        text: String,
        subId: Int
    ) {
        val preferences = MMKV.defaultMMKV()
        val requestUri = Network.getUrl(botToken, "sendMessage")

        val requestBody = RequestMessage()
        requestBody.chatId = chatId
        requestBody.messageThreadId = messageThreadId
        requestBody.text = text

        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(Const.JSON)
        val okhttpObj = Network.getOkhttpObj(preferences.getBoolean("doh_switch", true))
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
                    SMS.fallbackSMS(text, subId)
                }
                Resend.addResendLoop(context, text)
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
                        SMS.fallbackSMS(text, subId)
                    }
                    Resend.addResendLoop(context, text)
                } else {
                    Log.i(TAG, "MMS text message forwarded successfully")
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
     * Data class to hold MMS information from PDU
     */
    private data class MmsInfo(
        var messageType: Int = 0,
        var transactionId: String = "",
        var from: String = "",
        var subject: String = "",
        var contentLocation: String = "",
        var messageSize: String = "",
        var contentType: String = "",
        var textContent: String = ""
    )

    /**
     * Data class to hold MMS data from content provider
     */
    private data class MmsData(
        var from: String = "",
        var subject: String = "",
        var textContent: String = "",
        var images: MutableList<MmsImage> = mutableListOf()
    )

    /**
     * Data class to hold MMS image
     */
    private data class MmsImage(
        val fileName: String,
        val contentType: String,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as MmsImage
            if (fileName != other.fileName) return false
            if (contentType != other.contentType) return false
            if (!data.contentEquals(other.data)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = fileName.hashCode()
            result = 31 * result + contentType.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }
}
