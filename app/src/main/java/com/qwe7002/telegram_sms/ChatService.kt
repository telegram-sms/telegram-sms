package com.qwe7002.telegram_sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.qwe7002.telegram_sms.MMKV.MMKVConst
import com.qwe7002.telegram_sms.data_structure.SMSRequestInfo
import com.qwe7002.telegram_sms.data_structure.telegram.PollingBody
import com.qwe7002.telegram_sms.data_structure.telegram.ReplyMarkupKeyboard.KeyboardMarkup
import com.qwe7002.telegram_sms.data_structure.telegram.ReplyMarkupKeyboard.getInlineKeyboardObj
import com.qwe7002.telegram_sms.data_structure.telegram.ReplyMarkupKeyboard.createSmsListKeyboard
import com.qwe7002.telegram_sms.data_structure.telegram.ReplyMarkupKeyboard.createSmsDetailKeyboard
import com.qwe7002.telegram_sms.data_structure.telegram.ReplyMarkupKeyboard.createDeleteConfirmKeyboard
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.ChatCommand.getCommandList
import com.qwe7002.telegram_sms.static_class.ChatCommand.getInfo
import com.qwe7002.telegram_sms.static_class.Network.checkNetworkStatus
import com.qwe7002.telegram_sms.static_class.Network.getOkhttpObj
import com.qwe7002.telegram_sms.static_class.Network.getUrl
import com.qwe7002.telegram_sms.static_class.Other.getActiveCard
import com.qwe7002.telegram_sms.static_class.Other.getMessageId
import com.qwe7002.telegram_sms.static_class.Other.getNotificationObj
import com.qwe7002.telegram_sms.static_class.Other.getSendPhoneNumber
import com.qwe7002.telegram_sms.static_class.Other.getSubId
import com.qwe7002.telegram_sms.static_class.Other.isPhoneNumber
import com.qwe7002.telegram_sms.static_class.Phone
import com.qwe7002.telegram_sms.static_class.Resend.addResendLoop
import com.qwe7002.telegram_sms.static_class.SMS
import com.qwe7002.telegram_sms.static_class.SMS.send
import com.qwe7002.telegram_sms.static_class.SmsInfo
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.static_class.USSD.sendUssd
import com.qwe7002.telegram_sms.value.Const
import com.qwe7002.telegram_sms.value.Notify
import com.qwe7002.telegram_sms.value.TAG
import com.qwe7002.telegram_sms.value.TAG_FILTER
import com.tencent.mmkv.MMKV
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.Objects
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ChatService : Service() {
    companion object {
        private val logTag = "${TAG}.${this::class.java.simpleName}"
        private var RequestOffset: Long = 0
        private lateinit var sharedPreferences: MMKV
        private var sendSmsNextStatus = SEND_SMS_STATUS.STANDBY_STATUS
        private var sendUssdNextStatus = SEND_USSD_STATUS.STANDBY_STATUS
        private lateinit var threadMain: Thread
        private var firstRequest = true

        private fun isNumeric(str: String): Boolean {
            for (element in str) {
                if (!Character.isDigit(element)) {
                    return false
                }
            }
            return true
        }

        private fun readLogcat(lines: Int): String {
            return try {
                val level = "I"
                val addFilterArray = TAG_FILTER.map { tag -> "${TAG}.$tag:$level" }.toTypedArray().plus("${TAG}:$level")
                val command = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    arrayOf(
                        "logcat",*addFilterArray, "*:S", "-d", "-t", lines
                            .toString(), "-v", "time", "--pid=${android.os.Process.myPid()}"
                    )
                } else {
                    arrayOf(
                        "logcat", *addFilterArray, "*:S", "-d", "-t", lines
                            .toString(), "-v", "time"
                    )
                }
                val process = Runtime.getRuntime().exec(
                    command
                )

                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                val logBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    logBuilder.append(line).append("\n")
                }
                reader.close()
                process.destroy()
                logBuilder.toString().trim().ifEmpty { "No logs available" }
            } catch (e: Exception) {
                Log.e(logTag, "Failed to read logcat: ${e.message}", e)
                "Failed to read logs: ${e.message}"
            }
        }
    }

    @Suppress("ClassName")
    private object CALLBACK_DATA_VALUE {
        const val SEND: String = "send"
        const val CANCEL: String = "cancel"
        const val USSD_SEND: String = "ussd_send"
        const val USSD_CANCEL: String = "ussd_cancel"
        const val SIM1: String = "sim1"
        const val SIM2: String = "sim2"
    }

    @Suppress("ClassName")
    private object SEND_SMS_STATUS {
        const val STANDBY_STATUS: Int = -1
        const val SIM_SELECT_STATUS: Int = -2
        const val PHONE_INPUT_STATUS: Int = 0
        const val MESSAGE_INPUT_STATUS: Int = 1
        const val WAITING_TO_SEND_STATUS: Int = 2
        const val READY_TO_SEND_STATUS: Int = 4
        const val SEND_STATUS: Int = 3
    }

    @Suppress("ClassName")
    private object SEND_USSD_STATUS {
        const val STANDBY_STATUS: Int = -1
        const val SIM_SELECT_STATUS: Int = -2
        const val CODE_INPUT_STATUS: Int = 0
        const val WAITING_TO_SEND_STATUS: Int = 1
        const val SEND_STATUS: Int = 2
    }

    private lateinit var chatId: String
    private lateinit var botToken: String
    private lateinit var messageThreadId: String
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var pollingHttpClient: OkHttpClient
    private lateinit var wakelock: WakeLock
    private lateinit var wifiLock: WifiLock
    private lateinit var botUsername: String
    private val isRunning = AtomicBoolean(false)

    private val chatMMKV = MMKV.mmkvWithID(MMKVConst.CHAT_ID)
    private val chatInfoMMKV = MMKV.mmkvWithID(MMKVConst.CHAT_INFO_ID)

    private fun receiveHandle(resultObj: JsonObject, getIdOnly: Boolean) {
        val updateId = resultObj["update_id"].asLong
        RequestOffset = updateId + 1
        if (getIdOnly) {
            Log.d(logTag, "Receive handle: Get ID only mode, update_id=$updateId")
            return
        }
        var messageType = ""
        val requestBody = RequestMessage()
        requestBody.chatId = chatId
        requestBody.messageThreadId = messageThreadId
        lateinit var jsonObject: JsonObject

        if (resultObj.has("message")) {
            jsonObject = resultObj["message"].asJsonObject
            messageType = jsonObject["chat"].asJsonObject["type"].asString
        }
        if (resultObj.has("channel_post")) {
            messageType = "channel"
            jsonObject = resultObj["channel_post"].asJsonObject
        }
        lateinit var callbackData: String
        var callbackMessageId: Long = -1
        if (resultObj.has("callback_query")) {
            messageType = "callback_query"
            val callbackQuery = resultObj["callback_query"].asJsonObject
            callbackData = callbackQuery["data"].asString
            if (callbackQuery.has("message")) {
                callbackMessageId = callbackQuery["message"].asJsonObject["message_id"].asLong
            }
        }

        // Handle SMS management callbacks
        if (messageType == "callback_query" && callbackData.startsWith("sms_")) {
            handleSmsCallback(callbackData, callbackMessageId, requestBody)
            return
        }

        // Handle SMS SIM selection callback
        if (messageType == "callback_query" && sendSmsNextStatus == SEND_SMS_STATUS.SIM_SELECT_STATUS) {
            when (callbackData) {
                CALLBACK_DATA_VALUE.SIM1 -> {
                    chatMMKV.putInt("slot", 0)
                    sendSmsNextStatus = SEND_SMS_STATUS.MESSAGE_INPUT_STATUS
                    val requestUri = getUrl(botToken, "editMessageText")
                    requestBody.text = Template.render(
                        applicationContext,
                        "TPL_send_sms_chat",
                        mapOf("SIM" to "SIM1 ", "Content" to getString(R.string.enter_reply_number))
                    )
                    requestBody.messageId = callbackMessageId
                    val gson = Gson()
                    val requestBodyRaw = gson.toJson(requestBody)
                    val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
                    val okhttpObj = getOkhttpObj(sharedPreferences.getBoolean("doh_switch", false))
                    val request: Request =
                        Request.Builder().url(requestUri).method("POST", body).build()
                    val call = okhttpObj.newCall(request)
                    try {
                        val response = call.execute()
                        if (response.code != 200) {
                            throw IOException(response.code.toString())
                        }
                    } catch (e: IOException) {
                        Log.e(logTag, "Failed to edit message: ${e.message}", e)
                    }
                }

                CALLBACK_DATA_VALUE.SIM2 -> {
                    chatMMKV.putInt("slot", 1)
                    sendSmsNextStatus = SEND_SMS_STATUS.MESSAGE_INPUT_STATUS
                    val requestUri = getUrl(botToken, "editMessageText")
                    requestBody.text = Template.render(
                        applicationContext,
                        "TPL_send_sms_chat",
                        mapOf("SIM" to "SIM2 ", "Content" to getString(R.string.enter_reply_number))
                    )
                    requestBody.messageId = callbackMessageId
                    val gson = Gson()
                    val requestBodyRaw = gson.toJson(requestBody)
                    val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
                    val okhttpObj = getOkhttpObj(sharedPreferences.getBoolean("doh_switch", false))
                    val request: Request =
                        Request.Builder().url(requestUri).method("POST", body).build()
                    val call = okhttpObj.newCall(request)
                    try {
                        val response = call.execute()
                        if (response.code != 200) {
                            throw IOException(response.code.toString())
                        }
                    } catch (e: IOException) {
                        Log.e(logTag, "Failed to edit message: ${e.message}", e)
                    }
                }
            }
            return
        }

        if (messageType == "callback_query" && sendSmsNextStatus != SEND_SMS_STATUS.STANDBY_STATUS) {
            var slot = chatMMKV.getInt("slot", -1)
            val messageId = chatMMKV.getLong("message_id", -1L)
            val to = chatMMKV.getString("to", "").toString()
            val content = chatMMKV.getString("content", "").toString()
            if (callbackData != CALLBACK_DATA_VALUE.SEND) {
                setSmsSendStatusStandby()
                val requestUri = getUrl(
                    botToken, "editMessageText"
                )
                val dualSim = Phone.getSimDisplayName(applicationContext, slot)
                requestBody.text = Template.render(
                    applicationContext,
                    "TPL_send_sms",
                    mapOf("SIM" to dualSim, "To" to to, "Content" to content)
                ) + "\n" + getString(R.string.status) + getString(R.string.cancel_button)
                requestBody.messageId = messageId
                val gson = Gson()
                val requestBodyRaw = gson.toJson(requestBody)
                val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
                val okhttpObj = getOkhttpObj(
                    sharedPreferences.getBoolean("doh_switch", false)
                )
                val request: Request =
                    Request.Builder().url(requestUri).method("POST", body).build()
                val call = okhttpObj.newCall(request)
                try {
                    val response = call.execute()
                    if (response.code != 200) {
                        throw IOException(response.code.toString())
                    }
                } catch (e: IOException) {
                    Log.e(logTag, "Failed to edit message: ${e.message}", e)
                }
                return
            }
            // First reply to user with "Sending" status before executing send operation
            setSmsSendStatusStandby()
            val requestUri = getUrl(botToken, "editMessageText")
            val dualSim = Phone.getSimDisplayName(applicationContext, slot)
            requestBody.text = Template.render(
                applicationContext,
                "TPL_send_sms",
                mapOf("SIM" to dualSim, "To" to to, "Content" to content)
            ) + "\n" + getString(R.string.status) + getString(R.string.sending)
            requestBody.messageId = messageId
            val gson = Gson()
            val requestBodyRaw = gson.toJson(requestBody)
            val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
            val okhttpObj = getOkhttpObj(
                sharedPreferences.getBoolean("doh_switch", false)
            )
            val request: Request =
                Request.Builder().url(requestUri).method("POST", body).build()
            val call = okhttpObj.newCall(request)
            try {
                val response = call.execute()
                if (response.code != 200) {
                    throw IOException(response.code.toString())
                }
            } catch (e: IOException) {
                Log.e(logTag, "Failed to edit message: ${e.message}", e)
            }

            // Now execute the send operation
            var subId = -1
            if (getActiveCard(applicationContext) == 1) {
                slot = -1
            } else {
                subId = getSubId(applicationContext, slot)
            }
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                send(applicationContext, to, content, slot, subId, messageId)
            }
            return
        }

        // Handle USSD SIM selection callback
        if (messageType == "callback_query" && sendUssdNextStatus == SEND_USSD_STATUS.SIM_SELECT_STATUS) {
            when (callbackData) {
                CALLBACK_DATA_VALUE.SIM1 -> {
                    chatMMKV.putInt("ussd_slot", 0)
                    sendUssdNextStatus = SEND_USSD_STATUS.WAITING_TO_SEND_STATUS
                    val requestUri = getUrl(botToken, "editMessageText")
                    requestBody.text = Template.render(
                        applicationContext,
                        "TPL_send_USSD_chat",
                        mapOf("Content" to "SIM1 ${getString(R.string.enter_ussd_code)}")
                    )
                    requestBody.messageId = callbackMessageId
                    val gson = Gson()
                    val requestBodyRaw = gson.toJson(requestBody)
                    val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
                    val okhttpObj = getOkhttpObj(sharedPreferences.getBoolean("doh_switch", false))
                    val request: Request =
                        Request.Builder().url(requestUri).method("POST", body).build()
                    val call = okhttpObj.newCall(request)
                    try {
                        val response = call.execute()
                        if (response.code != 200) {
                            throw IOException(response.code.toString())
                        }
                    } catch (e: IOException) {
                        Log.e(logTag, "Failed to edit message: ${e.message}", e)
                    }
                }

                CALLBACK_DATA_VALUE.SIM2 -> {
                    chatMMKV.putInt("ussd_slot", 1)
                    sendUssdNextStatus = SEND_USSD_STATUS.WAITING_TO_SEND_STATUS
                    val requestUri = getUrl(botToken, "editMessageText")
                    requestBody.text = Template.render(
                        applicationContext,
                        "TPL_send_USSD_chat",
                        mapOf("Content" to "SIM2 ${getString(R.string.enter_ussd_code)}")
                    )
                    requestBody.messageId = callbackMessageId
                    val gson = Gson()
                    val requestBodyRaw = gson.toJson(requestBody)
                    val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
                    val okhttpObj = getOkhttpObj(sharedPreferences.getBoolean("doh_switch", false))
                    val request: Request =
                        Request.Builder().url(requestUri).method("POST", body).build()
                    val call = okhttpObj.newCall(request)
                    try {
                        val response = call.execute()
                        if (response.code != 200) {
                            throw IOException(response.code.toString())
                        }
                    } catch (e: IOException) {
                        Log.e(logTag, "Failed to edit message: ${e.message}", e)
                    }
                }
            }
            return
        }

        // Handle USSD callback
        if (messageType == "callback_query" && sendUssdNextStatus != SEND_USSD_STATUS.STANDBY_STATUS) {
            val ussdSlot = chatMMKV.getInt("ussd_slot", -1)
            val messageId = chatMMKV.getLong("ussd_message_id", -1L)
            val ussdCode = chatMMKV.getString("ussd_code", "").toString()

            if (callbackData != CALLBACK_DATA_VALUE.USSD_SEND) {
                // Cancel USSD
                setUssdSendStatusStandby()
                val requestUri = getUrl(botToken, "editMessageText")
                val dualSim = if (ussdSlot != -1) "SIM${ussdSlot + 1} " else ""
                requestBody.text = Template.render(
                    applicationContext, "TPL_send_USSD_chat",
                    mapOf(
                        "Content" to "${dualSim}USSD: $ussdCode\n${getString(R.string.status)}${
                            getString(
                                R.string.cancel_button
                            )
                        }"
                    )
                )
                requestBody.messageId = messageId
                val gson = Gson()
                val requestBodyRaw = gson.toJson(requestBody)
                val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
                val okhttpObj = getOkhttpObj(sharedPreferences.getBoolean("doh_switch", false))
                val request: Request =
                    Request.Builder().url(requestUri).method("POST", body).build()
                val call = okhttpObj.newCall(request)
                try {
                    val response = call.execute()
                    if (response.code != 200) {
                        throw IOException(response.code.toString())
                    }
                } catch (e: IOException) {
                    Log.e(logTag, "Failed to edit message: ${e.message}", e)
                }
                return
            }

            // First reply to user with "Sending" status before executing send operation
            setUssdSendStatusStandby()
            val requestUri = getUrl(botToken, "editMessageText")
            val dualSim = if (ussdSlot != -1) "SIM${ussdSlot + 1} " else ""
            requestBody.text = Template.render(
                applicationContext, "TPL_send_USSD_chat",
                mapOf(
                    "Content" to "${dualSim}USSD: $ussdCode\n${getString(R.string.status)}${
                        getString(R.string.sending)
                    }"
                )
            )
            requestBody.messageId = messageId
            val gson = Gson()
            val requestBodyRaw = gson.toJson(requestBody)
            val body: RequestBody = requestBodyRaw.toRequestBody(Const.JSON)
            val okhttpObj = getOkhttpObj(sharedPreferences.getBoolean("doh_switch", false))
            val request: Request =
                Request.Builder().url(requestUri).method("POST", body).build()
            val call = okhttpObj.newCall(request)
            try {
                val response = call.execute()
                if (response.code != 200) {
                    throw IOException(response.code.toString())
                }
            } catch (e: IOException) {
                Log.e(logTag, "Failed to edit message: ${e.message}", e)
            }

            // Now execute the send operation
            var subId = -1
            if (getActiveCard(applicationContext) > 1 && ussdSlot >= 0) {
                subId = getSubId(applicationContext, ussdSlot)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                sendUssd(applicationContext, ussdCode, subId, messageId)
            }
            return
        }

        lateinit var fromObj: JsonObject
        val isPrivate = messageType == "private"
        if (jsonObject.has("from")) {
            fromObj = jsonObject["from"].asJsonObject
            if (!isPrivate && fromObj["is_bot"].asBoolean) {
                Log.d(logTag, "Message from bot ignored")
                return
            }
        }
        if (jsonObject.has("chat")) {
            fromObj = jsonObject["chat"].asJsonObject
        }

        val fromId = fromObj["id"].asString
        var fromTopicId = ""
        if (messageThreadId != "") {
            if (jsonObject.has("is_topic_message")) {
                fromTopicId = jsonObject["message_thread_id"].asString
            }
            if (messageThreadId != fromTopicId) {
                Log.w(
                    logTag,
                    "Topic ID mismatch: expected=$messageThreadId, actual=$fromTopicId"
                )
                return
            }
        }
        if (chatId != fromId) {
            Log.w(logTag, "Chat ID not authorized: $fromId")
            return
        }
        var command = ""
        var currentBotUsername = ""
        var requestMsg = ""
        if (jsonObject.has("text")) {
            requestMsg = jsonObject["text"].asString
        }
        val hasReplyToMessage = jsonObject.has("reply_to_message")
        if (hasReplyToMessage) {
            val saveItemString = chatInfoMMKV.getString(
                jsonObject["reply_to_message"].asJsonObject["message_id"].asString,
                null
            )
            if (saveItemString != null && requestMsg.isNotEmpty()) {
                val saveItem =
                    Gson().fromJson(saveItemString, SMSRequestInfo::class.java)
                val phoneNumber = saveItem.phone
                val cardSlot = saveItem.card
                sendSmsNextStatus = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS
                chatMMKV.putInt("slot", cardSlot)
                chatMMKV.putString("to", phoneNumber)
                chatMMKV.putString("content", requestMsg)
            }
        }
        if (jsonObject.has("entities")) {
            val tempCommand: String
            val tempCommandLowercase: String
            val entities = jsonObject["entities"].asJsonArray
            val entitiesObjCommand = entities[0].asJsonObject
            if (entitiesObjCommand["type"].asString == "bot_command") {
                val commandOffset = entitiesObjCommand["offset"].asInt
                val commandEndOffset = commandOffset + entitiesObjCommand["length"].asInt
                tempCommand =
                    requestMsg.substring(commandOffset, commandEndOffset).trim { it <= ' ' }
                tempCommandLowercase = tempCommand.lowercase(Locale.getDefault()).replace("_", "")
                command = tempCommandLowercase
                if (tempCommandLowercase.contains("@")) {
                    val commandAtLocation = tempCommandLowercase.indexOf("@")
                    command = tempCommandLowercase.substring(0, commandAtLocation)
                    currentBotUsername = tempCommand.substring(commandAtLocation + 1)
                }
            }
        }
        if (!isPrivate && currentBotUsername != botUsername) {
            Log.d(logTag, "Privacy mode: Bot username not matched, ignoring message")
            return
        }
        Log.d(logTag, "Command received: $command")
        var hasCommand = false
        when (command) {
            "/help", "/start", "/commandlist" -> {
                requestBody.text = getCommandList(
                    applicationContext, command, isPrivate,
                    botUsername
                )
                hasCommand = true
            }

            "/ping", "/getinfo" -> {
                requestBody.text = getInfo(applicationContext)
                hasCommand = true
            }

            "/log" -> {
                val commands =
                    requestMsg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                var line = 10
                if (commands.size == 2 && isNumeric(commands[1])) {
                    val parsedLine = commands[1].toIntOrNull() ?: 10
                    line = parsedLine.coerceAtMost(50)
                }
                requestBody.text = Template.render(
                    applicationContext, "TPL_system_message",
                    mapOf("Message" to readLogcat(line))
                )
                hasCommand = true
            }

            "/sendussd", "/sendussd1", "/sendussd2" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val isDualSim = getActiveCard(applicationContext) > 1

                    // Parse command and arguments
                    val commandList = requestMsg.split(" ").filter { it.isNotEmpty() }
                    val baseCommand = commandList[0].trim()

                    // Determine slot based on command format
                    var ussdSlot = -1
                    var codeIndex = 1 // Default: code is at index 1

                    if (isDualSim) {
                        when (baseCommand) {
                            "/sendussd1" -> ussdSlot = 0
                            "/sendussd2" -> ussdSlot = 1
                            "/sendussd" -> {
                                // Check if SIM card number is specified after command
                                if (commandList.size > 1) {
                                    when (commandList[1].trim()) {
                                        "1" -> {
                                            ussdSlot = 0
                                            codeIndex = 2 // Code is at index 2
                                        }

                                        "2" -> {
                                            ussdSlot = 1
                                            codeIndex = 2 // Code is at index 2
                                        }
                                    }
                                }
                            }
                        }
                    }
                    chatMMKV.putInt("ussd_slot", ussdSlot)

                    if (commandList.size > codeIndex) {
                        // Direct USSD code provided: /sendussd *123# or /sendussd 1 *123#
                        val ussdCode = commandList[codeIndex]
                        if (isValidUssdCode(ussdCode)) {
                            chatMMKV.putString("ussd_code", ussdCode)
                            sendUssdNextStatus = SEND_USSD_STATUS.SEND_STATUS
                            // Show confirmation with keyboard
                            val dualSim = if (chatMMKV.getInt("ussd_slot", -1) != -1)
                                "SIM${chatMMKV.getInt("ussd_slot", -1) + 1} " else ""
                            val keyboardMarkup = KeyboardMarkup().apply {
                                inlineKeyboard = arrayListOf(
                                    getInlineKeyboardObj(
                                        getString(R.string.send_button),
                                        CALLBACK_DATA_VALUE.USSD_SEND
                                    ),
                                    getInlineKeyboardObj(
                                        getString(R.string.cancel_button),
                                        CALLBACK_DATA_VALUE.USSD_CANCEL
                                    )
                                )
                            }
                            requestBody.replyMarkup = keyboardMarkup
                            requestBody.text = Template.render(
                                applicationContext, "TPL_system_message",
                                mapOf("Message" to "${dualSim}USSD: $ussdCode")
                            )
                        } else {
                            setUssdSendStatusStandby()
                            requestBody.text = Template.render(
                                applicationContext, "TPL_system_message",
                                mapOf("Message" to getString(R.string.invalid_ussd_code))
                            )
                        }
                        hasCommand = true
                    } else {
                        // Interactive mode
                        Log.d(logTag, "Entering interactive USSD sending mode")

                        // If dual SIM and no specific SIM selected, show SIM selection
                        if (isDualSim && ussdSlot == -1) {
                            sendUssdNextStatus = SEND_USSD_STATUS.SIM_SELECT_STATUS
                            val keyboardMarkup = KeyboardMarkup().apply {
                                inlineKeyboard = arrayListOf(
                                    getInlineKeyboardObj(
                                        "SIM 1",
                                        CALLBACK_DATA_VALUE.SIM1
                                    ),
                                    getInlineKeyboardObj(
                                        "SIM 2",
                                        CALLBACK_DATA_VALUE.SIM2
                                    )
                                )
                            }
                            requestBody.replyMarkup = keyboardMarkup
                            requestBody.text = Template.render(
                                applicationContext, "TPL_send_USSD_chat",
                                mapOf("Content" to getString(R.string.select_sim_card))
                            )
                        } else {
                            // Single SIM or specific SIM command - proceed to code input
                            val dualSim = if (chatMMKV.getInt("ussd_slot", -1) != -1)
                                "SIM${chatMMKV.getInt("ussd_slot", -1) + 1} " else ""
                            requestBody.text = Template.render(
                                applicationContext, "TPL_send_USSD_chat",
                                mapOf("Content" to "$dualSim${getString(R.string.enter_ussd_code)}")
                            )
                            sendUssdNextStatus = SEND_USSD_STATUS.WAITING_TO_SEND_STATUS
                        }
                        hasCommand = true
                    }
                } else {
                    requestBody.text = Template.render(
                        applicationContext, "TPL_system_message",
                        mapOf("Content" to getString(R.string.unknown_command))
                    )
                    hasCommand = true
                }
            }

            "/listsms" -> {
                if (!SMS.isDefaultSmsApp(applicationContext)) {
                    requestBody.text = Template.render(
                        applicationContext, "TPL_system_message",
                        mapOf("Message" to getString(R.string.not_default_sms_app))
                    )
                    hasCommand = true
                } else if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.READ_SMS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val commandList = requestMsg.split(" ").filter { it.isNotEmpty() }
                    val smsType = if (commandList.size >= 2) commandList[1].lowercase() else "all"
                    val (smsList, totalPages) = SMS.getSmsList(applicationContext, smsType, 0, 5)

                    if (smsList.isEmpty()) {
                        requestBody.text = Template.render(
                            applicationContext, "TPL_system_message",
                            mapOf("Message" to getString(R.string.sms_list_empty))
                        )
                    } else {
                        val typeLabel = when (smsType) {
                            "inbox" -> getString(R.string.sms_type_inbox)
                            "sent" -> getString(R.string.sms_type_sent)
                            else -> getString(R.string.sms_type_all)
                        }
                        requestBody.text = buildSmsListMessage(smsList, typeLabel)
                        val keyboardMarkup = KeyboardMarkup().apply {
                            inlineKeyboard = createSmsListKeyboard(
                                smsList.map { it.id },
                                0,
                                totalPages,
                                smsType
                            )
                        }
                        requestBody.replyMarkup = keyboardMarkup
                    }
                    hasCommand = true
                }
            }

            "/sendsms", "/sendsms1", "/sendsms2" -> {
                var sendSlot = -1
                val isDualSim = getActiveCard(applicationContext) > 1

                // Parse command and arguments
                val commandParts = requestMsg.split(" ", "\n", limit = 3).filter { it.isNotEmpty() }
                val baseCommand = commandParts[0].trim()

                // Determine slot based on command format
                if (isDualSim) {
                    when (baseCommand) {
                        "/sendsms1" -> sendSlot = 0
                        "/sendsms2" -> sendSlot = 1
                        "/sendsms" -> {
                            // Check if SIM card number is specified after command
                            if (commandParts.size > 1) {
                                when (commandParts[1].trim()) {
                                    "1" -> sendSlot = 0
                                    "2" -> sendSlot = 1
                                }
                            }
                        }
                    }
                }

                chatMMKV.putInt("slot", sendSlot)
                val msgSendList =
                    requestMsg.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                Log.d(logTag, "SMS send list size: ${msgSendList.size}")

                // Check if phone number is provided
                val hasPhoneNumber =
                    if (sendSlot != -1 && baseCommand == "/sendsms" && commandParts.size > 1 && (commandParts[1] == "1" || commandParts[1] == "2")) {
                        // Format: /sendsms 1\nphone\ncontent or /sendsms 2\nphone\ncontent
                        msgSendList.size > 2
                    } else {
                        msgSendList.size > 1
                    }

                if (hasPhoneNumber) {
                    val phoneLineIndex =
                        if (sendSlot != -1 && baseCommand == "/sendsms" && commandParts.size > 1 && (commandParts[1] == "1" || commandParts[1] == "2")) {
                            2 // Skip command and SIM number
                        } else {
                            1 // Skip only command
                        }

                    if (msgSendList.size > phoneLineIndex) {
                        val msgSendTo = getSendPhoneNumber(msgSendList[phoneLineIndex])
                        if (isPhoneNumber(msgSendTo)) {
                            chatMMKV.putString("to", msgSendTo)
                            val sendContent =
                                msgSendList.drop(phoneLineIndex + 1).joinToString("\n")
                            chatMMKV.putString("content", sendContent)
                            sendSmsNextStatus = SEND_SMS_STATUS.SEND_STATUS
                            // Show confirmation with keyboard
                            val dualSim = if (sendSlot != -1) "SIM${sendSlot + 1} " else ""
                            val keyboardMarkup = KeyboardMarkup().apply {
                                inlineKeyboard = arrayListOf(
                                    getInlineKeyboardObj(
                                        getString(R.string.send_button),
                                        CALLBACK_DATA_VALUE.SEND
                                    ),
                                    getInlineKeyboardObj(
                                        getString(R.string.cancel_button),
                                        CALLBACK_DATA_VALUE.CANCEL
                                    )
                                )
                            }
                            requestBody.replyMarkup = keyboardMarkup
                            val values = mapOf(
                                "SIM" to dualSim,
                                "To" to msgSendTo,
                                "Content" to sendContent
                            )
                            requestBody.text =
                                Template.render(applicationContext, "TPL_send_sms", values)
                        } else {
                            setSmsSendStatusStandby()
                            requestBody.text = Template.render(
                                applicationContext,
                                "TPL_send_sms_chat",
                                mapOf(
                                    "SIM" to "",
                                    "Content" to getString(R.string.unable_get_phone_number)
                                )
                            )
                        }
                        hasCommand = true
                    }
                } else {
                    // Interactive mode
                    Log.d(logTag, "Entering interactive SMS sending mode")

                    // If dual SIM and no specific SIM selected, show SIM selection
                    if (isDualSim && sendSlot == -1) {
                        sendSmsNextStatus = SEND_SMS_STATUS.SIM_SELECT_STATUS
                        val keyboardMarkup = KeyboardMarkup().apply {
                            inlineKeyboard = arrayListOf(
                                getInlineKeyboardObj(
                                    "SIM 1",
                                    CALLBACK_DATA_VALUE.SIM1
                                ),
                                getInlineKeyboardObj(
                                    "SIM 2",
                                    CALLBACK_DATA_VALUE.SIM2
                                )
                            )
                        }
                        requestBody.replyMarkup = keyboardMarkup
                        requestBody.text = Template.render(
                            applicationContext,
                            "TPL_send_sms_chat",
                            mapOf("SIM" to "", "Content" to getString(R.string.select_sim_card))
                        )
                    } else {
                        // Single SIM or specific SIM command - proceed to phone input
                        val dualSim = if (sendSlot != -1) "SIM${sendSlot + 1} " else ""
                        requestBody.text = Template.render(
                            applicationContext,
                            "TPL_send_sms_chat",
                            mapOf(
                                "SIM" to dualSim,
                                "Content" to getString(R.string.enter_reply_number)
                            )
                        )
                        sendSmsNextStatus = SEND_SMS_STATUS.MESSAGE_INPUT_STATUS
                    }
                    hasCommand = true
                }
            }

            else -> {
                if (!isPrivate && sendSmsNextStatus == -1) {
                    if (messageType != "supergroup" || messageThreadId.isEmpty()) {
                        Log.d(logTag, "Non-private conversation without topic, ignoring message")
                        return
                    }
                }
                requestBody.text = Template.render(
                    applicationContext, "TPL_system_message",
                    mapOf("Message" to getString(R.string.unknown_command))
                )
            }
        }

        if (hasCommand) {
            Log.d(logTag, "Command processed, entering standby state")
            // Only reset status if we're not in an interactive mode that needs to continue
            if (sendSmsNextStatus != SEND_SMS_STATUS.SIM_SELECT_STATUS &&
                sendSmsNextStatus != SEND_SMS_STATUS.MESSAGE_INPUT_STATUS &&
                sendSmsNextStatus != SEND_SMS_STATUS.WAITING_TO_SEND_STATUS &&
                sendSmsNextStatus != SEND_SMS_STATUS.SEND_STATUS
            ) {
                setSmsSendStatusStandby()
            }
            if (sendUssdNextStatus != SEND_USSD_STATUS.SIM_SELECT_STATUS &&
                sendUssdNextStatus != SEND_USSD_STATUS.WAITING_TO_SEND_STATUS &&
                sendUssdNextStatus != SEND_USSD_STATUS.SEND_STATUS
            ) {
                setUssdSendStatusStandby()
            }
        }
        if (!hasCommand && sendSmsNextStatus != -1) {
            Log.d(logTag, "Entering interactive SMS sending mode, status=$sendSmsNextStatus")
            val sendSlotTemp = chatMMKV.getInt("slot", -1)
            val dualSim = if (sendSlotTemp != -1) "SIM${sendSlotTemp + 1} " else ""

            var resultSend = Template.render(
                applicationContext,
                "TPL_send_sms_chat",
                mapOf("SIM" to dualSim, "Content" to getString(R.string.failed_to_get_information))
            )
            Log.d(logTag, "Sending mode status: $sendSmsNextStatus")
            resultSend = when (sendSmsNextStatus) {
                SEND_SMS_STATUS.PHONE_INPUT_STATUS -> {
                    sendSmsNextStatus = SEND_SMS_STATUS.MESSAGE_INPUT_STATUS
                    Template.render(
                        applicationContext,
                        "TPL_send_sms_chat",
                        mapOf("SIM" to dualSim, "Content" to getString(R.string.enter_reply_number))
                    )
                }

                SEND_SMS_STATUS.MESSAGE_INPUT_STATUS -> {
                    if (!hasReplyToMessage) {
                        setSmsSendStatusStandby()
                        Template.render(
                            applicationContext,
                            "TPL_send_sms_chat",
                            mapOf(
                                "SIM" to dualSim,
                                "Content" to getString(R.string.please_reply_to_continue)
                            )
                        )
                    } else {
                        val tempTo = getSendPhoneNumber(requestMsg)
                        if (isPhoneNumber(tempTo)) {
                            chatMMKV.putString("to", tempTo)
                            sendSmsNextStatus = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS
                            Template.render(
                                applicationContext,
                                "TPL_send_sms_chat",
                                mapOf(
                                    "SIM" to dualSim,
                                    "Content" to getString(R.string.enter_reply_content)
                                )
                            )
                        } else {
                            setSmsSendStatusStandby()
                            Template.render(
                                applicationContext,
                                "TPL_send_sms_chat",
                                mapOf(
                                    "SIM" to dualSim,
                                    "Content" to getString(R.string.unable_get_phone_number)
                                )
                            )
                        }
                    }
                }

                SEND_SMS_STATUS.WAITING_TO_SEND_STATUS, SEND_SMS_STATUS.READY_TO_SEND_STATUS -> {
                    if (!hasReplyToMessage) {
                        setSmsSendStatusStandby()
                        Template.render(
                            applicationContext,
                            "TPL_send_sms_chat",
                            mapOf(
                                "SIM" to dualSim,
                                "Content" to getString(R.string.please_reply_to_continue)
                            )
                        )
                    } else {
                        if (sendSmsNextStatus == SEND_SMS_STATUS.WAITING_TO_SEND_STATUS) {
                            chatMMKV.putString("content", requestMsg)
                        }
                        val keyboardMarkup = KeyboardMarkup().apply {
                            inlineKeyboard = arrayListOf(
                                getInlineKeyboardObj(
                                    getString(R.string.send_button),
                                    CALLBACK_DATA_VALUE.SEND
                                ),
                                getInlineKeyboardObj(
                                    getString(R.string.cancel_button),
                                    CALLBACK_DATA_VALUE.CANCEL
                                )
                            )
                        }
                        requestBody.replyMarkup = keyboardMarkup
                        val values = mapOf(
                            "SIM" to dualSim,
                            "To" to chatMMKV.getString("to", "").toString(),
                            "Content" to chatMMKV.getString("content", "").toString()
                        )
                        sendSmsNextStatus = SEND_SMS_STATUS.SEND_STATUS
                        Template.render(applicationContext, "TPL_send_sms", values)
                    }
                }

                else -> resultSend
            }
            requestBody.text = resultSend
        }

        // Handle USSD interactive mode
        if (!hasCommand && sendUssdNextStatus != SEND_USSD_STATUS.STANDBY_STATUS) {
            Log.d(logTag, "Entering interactive USSD sending mode, status=$sendUssdNextStatus")
            val ussdSlotTemp = chatMMKV.getInt("ussd_slot", -1)
            val dualSim = if (ussdSlotTemp != -1) "SIM${ussdSlotTemp + 1} " else ""

            var resultUssd = Template.render(
                applicationContext, "TPL_send_USSD_chat",
                mapOf("Content" to getString(R.string.failed_to_get_information))
            )
            Log.d(logTag, "USSD sending mode status: $sendUssdNextStatus")
            resultUssd = when (sendUssdNextStatus) {
                SEND_USSD_STATUS.CODE_INPUT_STATUS -> {
                    sendUssdNextStatus = SEND_USSD_STATUS.WAITING_TO_SEND_STATUS
                    Template.render(
                        applicationContext, "TPL_send_USSD_chat",
                        mapOf("Content" to "$dualSim${getString(R.string.enter_ussd_code)}")
                    )
                }

                SEND_USSD_STATUS.WAITING_TO_SEND_STATUS -> {
                    if (!hasReplyToMessage) {
                        setUssdSendStatusStandby()
                        Template.render(
                            applicationContext, "TPL_send_USSD_chat",
                            mapOf("Content" to getString(R.string.please_reply_to_continue))
                        )
                    } else {
                        val ussdCode = requestMsg.trim()
                        if (isValidUssdCode(ussdCode)) {
                            chatMMKV.putString("ussd_code", ussdCode)
                            val keyboardMarkup = KeyboardMarkup().apply {
                                inlineKeyboard = arrayListOf(
                                    getInlineKeyboardObj(
                                        getString(R.string.send_button),
                                        CALLBACK_DATA_VALUE.USSD_SEND
                                    ),
                                    getInlineKeyboardObj(
                                        getString(R.string.cancel_button),
                                        CALLBACK_DATA_VALUE.USSD_CANCEL
                                    )
                                )
                            }
                            requestBody.replyMarkup = keyboardMarkup
                            sendUssdNextStatus = SEND_USSD_STATUS.SEND_STATUS
                            Template.render(
                                applicationContext, "TPL_send_USSD_chat",
                                mapOf("Content" to "${dualSim}USSD: $ussdCode")
                            )
                        } else {
                            setUssdSendStatusStandby()
                            Template.render(
                                applicationContext, "TPL_send_USSD_chat",
                                mapOf("Content" to getString(R.string.invalid_ussd_code))
                            )
                        }
                    }
                }

                else -> resultUssd
            }
            requestBody.text = resultUssd
        }

        val requestUri = getUrl(
            botToken, "sendMessage"
        )
        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(Const.JSON)
        val sendRequest: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okHttpClient.newCall(sendRequest)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(logTag, "Send reply failed: ${e.message}", e)
                addResendLoop(applicationContext, requestBody.text)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val responseString = Objects.requireNonNull(response.body).string()
                if (response.code != 200) {
                    Log.e(logTag, "Send reply failed: ${response.code} $responseString")
                    addResendLoop(applicationContext, requestBody.text)
                }
                if (sendSmsNextStatus == SEND_SMS_STATUS.SEND_STATUS) {
                    chatMMKV.putLong("message_id", getMessageId(responseString))
                }
                if (sendUssdNextStatus == SEND_USSD_STATUS.SEND_STATUS) {
                    chatMMKV.putLong("ussd_message_id", getMessageId(responseString))
                }
            }
        })
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val notification = getNotificationObj(
            applicationContext, getString(R.string.chat_command_service_name)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Notify.CHAT_COMMAND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Notify.CHAT_COMMAND, notification)
        }
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    @SuppressLint("InvalidWakeLockTag", "WakelockTimeout", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(applicationContext)
        setSmsSendStatusStandby()
        setUssdSendStatusStandby()
        sharedPreferences = MMKV.defaultMMKV()
        chatId = sharedPreferences.getString("chat_id", "")!!
        botToken = sharedPreferences.getString("bot_token", "")!!
        botUsername = sharedPreferences.getString("bot_username", "")!!
        messageThreadId = sharedPreferences.getString("message_thread_id", "")!!
        okHttpClient = getOkhttpObj(
            sharedPreferences.getBoolean("doh_switch", true)
        )
        pollingHttpClient = okHttpClient.newBuilder()
            .readTimeout(65, TimeUnit.SECONDS)
            .writeTimeout(65, TimeUnit.SECONDS)
            .build()
        wifiLock = (Objects.requireNonNull(
            applicationContext.getSystemService(
                WIFI_SERVICE
            )
        ) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL, "bot_command_polling_wifi")
        wakelock =
            (Objects.requireNonNull(applicationContext.getSystemService(POWER_SERVICE)) as PowerManager).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "bot_command_polling"
            )
        wifiLock.setReferenceCounted(false)
        wakelock.setReferenceCounted(false)

        if (!wifiLock.isHeld) {
            wifiLock.acquire()
        }
        if (!wakelock.isHeld) {
            wakelock.acquire()
        }

        isRunning.set(true)
        threadMain = Thread(ThreadMainRunnable())
        threadMain.start()
        val intentFilter = IntentFilter()
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
    }


    private fun setSmsSendStatusStandby() {
        sendSmsNextStatus = SEND_SMS_STATUS.STANDBY_STATUS
        chatMMKV.remove("slot")
        chatMMKV.remove("to")
        chatMMKV.remove("content")
        chatMMKV.remove("message_id")
    }

    private fun setUssdSendStatusStandby() {
        sendUssdNextStatus = SEND_USSD_STATUS.STANDBY_STATUS
        chatMMKV.remove("ussd_slot")
        chatMMKV.remove("ussd_code")
        chatMMKV.remove("ussd_message_id")
    }

    private fun isValidUssdCode(code: String): Boolean {
        // USSD codes typically start with * or # and end with #
        // They can contain digits, *, and #
        val ussdPattern = Regex("^[*#][0-9*#]+#?\$")
        return code.isNotEmpty() && ussdPattern.matches(code)
    }

    @SuppressLint("MissingPermission")
    private fun handleSmsCallback(
        callbackData: String,
        messageId: Long,
        requestBody: RequestMessage
    ) {
        Log.d(logTag, "Handling SMS callback: $callbackData")
        val parts = callbackData.split(":")

        when {
            // Handle pagination: sms_page:type:pageNum
            callbackData.startsWith("sms_page:") && parts.size >= 3 -> {
                val smsType = parts[1]
                val pageStr = parts[2]
                if (pageStr == "current") return // Ignore current page button click

                val page = pageStr.toIntOrNull() ?: 0
                if (ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.READ_SMS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val (smsList, totalPages) = SMS.getSmsList(applicationContext, smsType, page, 5)
                    val typeLabel = when (smsType) {
                        "inbox" -> getString(R.string.sms_type_inbox)
                        "sent" -> getString(R.string.sms_type_sent)
                        else -> getString(R.string.sms_type_all)
                    }
                    requestBody.text = buildSmsListMessage(smsList, typeLabel)
                    val keyboardMarkup = KeyboardMarkup().apply {
                        inlineKeyboard =
                            createSmsListKeyboard(smsList.map { it.id }, page, totalPages, smsType)
                    }
                    requestBody.replyMarkup = keyboardMarkup
                    editMessage(messageId, requestBody)
                }
            }

            // Handle read SMS: sms_read:id
            callbackData.startsWith("sms_read:") && parts.size >= 2 -> {
                val smsId = parts[1].toLongOrNull()
                if (smsId != null && ActivityCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.READ_SMS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val sms = SMS.getSmsById(applicationContext, smsId)
                    if (sms != null) {
                        requestBody.text = buildSmsDetailMessage(sms)
                        val keyboardMarkup = KeyboardMarkup().apply {
                            inlineKeyboard = createSmsDetailKeyboard(smsId)
                        }
                        requestBody.replyMarkup = keyboardMarkup
                    } else {
                        requestBody.text = Template.render(
                            applicationContext, "TPL_system_message",
                            mapOf("Message" to getString(R.string.sms_not_found))
                        )
                    }
                    editMessage(messageId, requestBody)
                }
            }

            // Handle delete confirmation prompt: sms_del_confirm:id
            callbackData.startsWith("sms_del_confirm:") && parts.size >= 2 -> {
                val smsId = parts[1].toLongOrNull()
                if (smsId != null) {
                    requestBody.text = Template.render(
                        applicationContext, "TPL_system_message",
                        mapOf("Message" to getString(R.string.sms_delete_confirm) + "\n\nID: $smsId")
                    )
                    val keyboardMarkup = KeyboardMarkup().apply {
                        inlineKeyboard = createDeleteConfirmKeyboard(smsId)
                    }
                    requestBody.replyMarkup = keyboardMarkup
                    editMessage(messageId, requestBody)
                }
            }

            // Handle actual delete: sms_del:id
            callbackData.startsWith("sms_del:") && parts.size >= 2 -> {
                val smsId = parts[1].toLongOrNull()
                if (smsId != null) {
                    val success = SMS.deleteSmsById(applicationContext, smsId)
                    val message = if (success) {
                        getString(R.string.sms_deleted)
                    } else {
                        getString(R.string.sms_delete_failed)
                    }
                    requestBody.text = Template.render(
                        applicationContext, "TPL_system_message",
                        mapOf("Message" to message)
                    )
                    // Return to list
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.READ_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val (smsList, totalPages) = SMS.getSmsList(applicationContext, "all", 0, 5)
                        if (smsList.isNotEmpty()) {
                            requestBody.text = buildSmsListMessage(
                                smsList, getString(R.string.sms_type_all)
                            )
                            val keyboardMarkup = KeyboardMarkup().apply {
                                inlineKeyboard = createSmsListKeyboard(
                                    smsList.map { it.id },
                                    0,
                                    totalPages,
                                    "all"
                                )
                            }
                            requestBody.replyMarkup = keyboardMarkup
                        }
                    }
                    editMessage(messageId, requestBody)
                }
            }
        }
    }

    private fun buildSmsListMessage(smsList: List<SmsInfo>, typeLabel: String): String {
        val header = String.format(getString(R.string.sms_list_header), typeLabel)
        val builder = StringBuilder()
        builder.append(header).append("\n")
        builder.append("━━━━━━━━━━━━━━━\n")

        for (sms in smsList) {
            val typeIcon = if (sms.type == 1) "📥" else "📤"
            val preview = if (sms.body.length > 30) sms.body.take(30) + "..." else sms.body
            builder.append("$typeIcon #${sms.id}\n")
            builder.append("📞 ${sms.address}\n")
            builder.append("💬 $preview\n")
            builder.append("🕐 ${sms.getFormattedDate()}\n")
            builder.append("───────────────\n")
        }

        return builder.toString()
    }

    private fun buildSmsDetailMessage(sms: SmsInfo): String {
        val typeIcon = if (sms.type == 1) "📥" else "📤"
        val addressLabel =
            if (sms.type == 1) getString(R.string.sms_from) else getString(R.string.sms_to)

        return """
${getString(R.string.sms_detail_header)} $typeIcon #${sms.id}
━━━━━━━━━━━━━━━
$addressLabel ${sms.address}
${getString(R.string.sms_date)} ${sms.getFormattedDate()}
━━━━━━━━━━━━━━━
${getString(R.string.sms_content)}
${sms.body}
        """.trimIndent()
    }

    private fun editMessage(messageId: Long, requestBody: RequestMessage) {
        val requestUri = getUrl(botToken, "editMessageText")
        requestBody.messageId = messageId

        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(Const.JSON)
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(logTag, "Failed to edit message: ${e.message}", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code != 200) {
                    Log.e(logTag, "Failed to edit message: ${response.code}")
                }
            }
        })
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        isRunning.set(false)
        threadMain.interrupt()
        wifiLock.release()
        wakelock.release()
        stopForeground(true)
        super.onDestroy()
    }

    private inner class ThreadMainRunnable : Runnable {
        private val MIN_RETRY_DELAY_MS = 1000L
        private val MAX_RETRY_DELAY_MS = 30000L
        private val NETWORK_CHECK_INTERVAL_MS = 5000L

        override fun run() {
            Log.d(logTag, "Polling thread started")
            var retryDelayMs = MIN_RETRY_DELAY_MS

            while (isRunning.get()) {
                // Wait for network availability
                if (!waitForNetwork()) {
                    continue
                }

                val requestUri = getUrl(botToken, "getUpdates")
                val requestBody = PollingBody().apply {
                    this.offset = RequestOffset
                    this.timeout = if (firstRequest) 0 else 60
                }
                val body = Gson().toJson(requestBody).toRequestBody(Const.JSON)
                val request = Request.Builder().url(requestUri).post(body).build()

                try {
                    pollingHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val result = response.body.string()
                            val resultObj = JsonParser.parseString(result).asJsonObject
                            if (resultObj["ok"].asBoolean) {
                                val resultArray = resultObj["result"].asJsonArray
                                for (item in resultArray) {
                                    receiveHandle(item.asJsonObject, firstRequest)
                                }
                                firstRequest = false
                            }
                            // Reset retry delay on success
                            retryDelayMs = MIN_RETRY_DELAY_MS
                        } else {
                            Log.e(logTag, "Polling response error: ${response.code}")
                            sleepWithCheck(retryDelayMs)
                            retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                        }
                    }
                } catch (e: IOException) {
                    if (!isRunning.get()) {
                        Log.d(logTag, "Polling thread interrupted, exiting")
                        break
                    }
                    Log.e(logTag, "Polling error: ${e.message}", e)
                    sleepWithCheck(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                } catch (e: Exception) {
                    Log.e(logTag, "Unexpected error in polling loop", e)
                    sleepWithCheck(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            }
            Log.d(logTag, "Polling thread stopped")
        }

        private fun waitForNetwork(): Boolean {
            while (isRunning.get() && !checkNetworkStatus(applicationContext)) {
                Log.w(logTag, "No network available, waiting for recovery...")
                sleepWithCheck(NETWORK_CHECK_INTERVAL_MS)
            }
            return isRunning.get()
        }

        private fun sleepWithCheck(delayMs: Long) {
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.d(logTag, "Thread sleep interrupted")
            }
        }
    }


    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
