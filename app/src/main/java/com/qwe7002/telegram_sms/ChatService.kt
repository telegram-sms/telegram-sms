package com.qwe7002.telegram_sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
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
import com.qwe7002.telegram_sms.MMKV.CHAT_INFO_ID
import com.qwe7002.telegram_sms.MMKV.SESSION_ID
import com.qwe7002.telegram_sms.data_structure.ChatSession
import com.qwe7002.telegram_sms.data_structure.SMSRequestInfo
import com.qwe7002.telegram_sms.data_structure.telegram.PollingBody
import com.qwe7002.telegram_sms.data_structure.telegram.ReplyMarkupKeyboard.ForceReply
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
import com.qwe7002.telegram_sms.value.JSON
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
        private val logTag = "${TAG}.ChatService"
        private var RequestOffset: Long = 0
        private lateinit var sharedPreferences: MMKV
        private lateinit var threadMain: Thread
        private var firstRequest = true

        // Sessions older than this (no user interaction) are treated as abandoned and purged.
        private const val SESSION_TTL_MS = 60 * 60 * 1000L

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

    // Steps of an interactive session (stored in ChatSession.step). SMS_* and USSD_* live
    // in one space; ChatSession.type disambiguates which flow a record belongs to.
    @Suppress("ClassName")
    private object SESSION_STEP {
        const val SMS_SIM_SELECT: Int = 0     // awaiting SIM1/SIM2 inline-button tap
        const val SMS_PHONE_INPUT: Int = 1    // awaiting phone number (ForceReply)
        const val SMS_CONTENT_INPUT: Int = 2  // awaiting message body (ForceReply)
        const val SMS_CONFIRM: Int = 3        // awaiting Send/Cancel inline-button tap
        const val USSD_SIM_SELECT: Int = 10   // awaiting SIM1/SIM2 inline-button tap
        const val USSD_CODE_INPUT: Int = 11   // awaiting USSD code (reply)
        const val USSD_CONFIRM: Int = 12      // awaiting Send/Cancel inline-button tap
    }

    private lateinit var chatId: String
    private lateinit var botToken: String
    private lateinit var messageThreadId: String
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var pollingHttpClient: OkHttpClient
    private lateinit var wakelock: WakeLock
    private lateinit var wifiLock: WifiLock
    // Plain default (not lateinit) so that any unexpected access before onCreate finishes
    // — e.g. due to a service-restart race — degrades to "no privacy-mode match" rather than
    // crashing with UninitializedPropertyAccessException (issue #49).
    private var botUsername: String = ""
    private val isRunning = AtomicBoolean(false)

    private val chatInfoMMKV = MMKV.mmkvWithID(CHAT_INFO_ID)
    private val sessionMMKV = MMKV.mmkvWithID(SESSION_ID)

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
        var callbackData = ""
        var callbackMessageId: Long = -1
        if (resultObj.has("callback_query")) {
            messageType = "callback_query"
            val callbackQuery = resultObj["callback_query"].asJsonObject
            // `data` is optional on a callback_query (e.g. game callbacks) — guard it.
            if (callbackQuery.has("data")) {
                callbackData = callbackQuery["data"].asString
            }
            if (callbackQuery.has("message")) {
                val callbackMessage = callbackQuery["message"].asJsonObject
                callbackMessageId = callbackMessage["message_id"].asLong
                // Authorize by the chat the keyboard lives in — the message-path checks
                // below are bypassed by the early callback dispatch, so without this any
                // group member who can see the buttons could trigger a send/cancel/delete.
                val callbackChatId =
                    if (callbackMessage.has("chat")) {
                        callbackMessage["chat"].asJsonObject["id"].asString
                    } else {
                        ""
                    }
                if (callbackChatId.isNotEmpty() && callbackChatId != chatId) {
                    Log.w(logTag, "Callback chat not authorized: $callbackChatId")
                    return
                }
            }
        }

        // Handle SMS management callbacks
        if (messageType == "callback_query" && callbackData.startsWith("sms_")) {
            handleSmsCallback(callbackData, callbackMessageId, requestBody)
            return
        }

        // Inline-button taps on an interactive prompt: resolve the session by the message
        // the keyboard is attached to (callback_query.message.message_id). No live record
        // for that id means a stale/duplicate/expired keyboard — ignore it. This also keeps
        // us from falling through to message handling, where jsonObject is unset for callbacks.
        if (messageType == "callback_query") {
            val session = loadSession(callbackMessageId)
            if (session == null) {
                Log.d(logTag, "Callback for unknown/expired session, ignoring")
                return
            }
            handleSessionCallback(session, callbackMessageId, callbackData, requestBody)
            return
        }
        // Updates that carry neither a message nor a channel_post (edited_message,
        // my_chat_member, etc.) leave jsonObject uninitialized — ignore them.
        if (!resultObj.has("message") && !resultObj.has("channel_post")) {
            Log.d(logTag, "Update without message content, ignoring")
            return
        }

        val isPrivate = messageType == "private"
        // The bot-origin check looks at the sender ("from"); authorization compares the
        // chat id ("chat"), so chat takes priority when both are present (original behavior).
        if (jsonObject.has("from")) {
            val senderObj = jsonObject["from"].asJsonObject
            if (!isPrivate && senderObj["is_bot"].asBoolean) {
                Log.d(logTag, "Message from bot ignored")
                return
            }
        }
        val fromObj: JsonObject = when {
            jsonObject.has("chat") -> jsonObject["chat"].asJsonObject
            jsonObject.has("from") -> jsonObject["from"].asJsonObject
            else -> {
                Log.w(logTag, "Update has neither chat nor from, ignoring")
                return
            }
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
        if (jsonObject.has("entities")) {
            val entities = jsonObject["entities"].asJsonArray
            // Don't assume the command is the first entity — a leading mention/format
            // entity would otherwise hide it. Find the first bot_command anywhere.
            val commandEntity = entities.firstOrNull {
                val obj = it.asJsonObject
                obj.has("type") && obj["type"].asString == "bot_command"
            }?.asJsonObject
            if (commandEntity != null) {
                val commandOffset = commandEntity["offset"].asInt
                val commandEndOffset = commandOffset + commandEntity["length"].asInt
                val tempCommand =
                    requestMsg.substring(commandOffset, commandEndOffset).trim { it <= ' ' }
                val tempCommandLowercase =
                    tempCommand.lowercase(Locale.getDefault()).replace("_", "")
                command = tempCommandLowercase
                if (tempCommandLowercase.contains("@")) {
                    val commandAtLocation = tempCommandLowercase.indexOf("@")
                    command = tempCommandLowercase.substring(0, commandAtLocation)
                    currentBotUsername = tempCommand.substring(commandAtLocation + 1)
                }
            }
        }
        val hasReplyToMessage = jsonObject.has("reply_to_message")
        var isReplyToBot = false
        var replyToBotMsgId = -1L
        if (hasReplyToMessage) {
            val replyToMessage = jsonObject["reply_to_message"].asJsonObject
            if (replyToMessage.has("message_id")) {
                replyToBotMsgId = replyToMessage["message_id"].asLong
            }
            if (replyToMessage.has("from")) {
                val replyFrom = replyToMessage["from"].asJsonObject
                val replyFromUsername =
                    if (replyFrom.has("username")) replyFrom["username"].asString else ""
                if (botUsername.isNotEmpty() &&
                    replyFromUsername.equals(botUsername, ignoreCase = true)
                ) {
                    isReplyToBot = true
                }
            }
        }
        if (!isPrivate && currentBotUsername != botUsername && !isReplyToBot) {
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
                startUssdFlow(requestMsg, jsonObject, requestBody)
                return
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
                startSmsFlow(requestMsg, jsonObject, requestBody)
                return
            }

            else -> {
                // A reply that continues an interactive session (resolved by the bot prompt
                // message id the user replied to).
                if (hasReplyToMessage) {
                    val session = loadSession(replyToBotMsgId)
                    if (session != null) {
                        handleSessionText(session, replyToBotMsgId, requestMsg, jsonObject, requestBody)
                        return
                    }
                    // A reply to a forwarded SMS notification starts a confirm session.
                    if (startSmsReplyFromNotification(jsonObject, requestMsg, requestBody)) {
                        return
                    }
                }
                if (!isPrivate) {
                    if (messageType != "supergroup" || messageThreadId.isEmpty()) {
                        Log.d(logTag, "Non-private conversation without topic, ignoring message")
                        return
                    }
                }
                requestBody.text = Template.render(
                    applicationContext, "TPL_system_message",
                    mapOf("Message" to getString(R.string.unknown_command))
                )
                hasCommand = true
            }
        }

        // Stateless commands (and the unknown-command fallback) set hasCommand and a text
        // body above; fire-and-forget the reply. Interactive /sendsms and /sendussd flows
        // returned earlier after their own session-tracked sync sends.
        if (hasCommand) {
            asyncSend(requestBody)
        }
    }

    // --- Interactive session machinery (keyed by bot prompt message id) ---------------

    /**
     * Send [requestBody] synchronously and return the resulting Telegram message id, or -1
     * on failure. Used by interactive flows that must key a [ChatSession] under the id of
     * the prompt they just sent. Failures are queued for resend, matching [asyncSend].
     */
    private fun sendMessageForId(requestBody: RequestMessage): Long {
        val requestUri = getUrl(botToken, "sendMessage")
        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(JSON)
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val responseString = response.body.string()
                if (response.code != 200) {
                    Log.e(logTag, "Send reply failed: ${response.code} $responseString")
                    addResendLoop(applicationContext, requestBody.text)
                    -1L
                } else {
                    getMessageId(responseString)
                }
            }
        } catch (e: IOException) {
            Log.e(logTag, "Send reply failed: ${e.message}", e)
            addResendLoop(applicationContext, requestBody.text)
            -1L
        }
    }

    /** Fire-and-forget send for stateless replies. */
    private fun asyncSend(requestBody: RequestMessage) {
        val requestUri = getUrl(botToken, "sendMessage")
        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(JSON)
        val sendRequest: Request = Request.Builder().url(requestUri).method("POST", body).build()
        okHttpClient.newCall(sendRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(logTag, "Send reply failed: ${e.message}", e)
                addResendLoop(applicationContext, requestBody.text)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseString = it.body.string()
                    if (it.code != 200) {
                        Log.e(logTag, "Send reply failed: ${it.code} $responseString")
                        addResendLoop(applicationContext, requestBody.text)
                    }
                }
            }
        })
    }

    private fun loadSession(messageId: Long): ChatSession? {
        if (messageId <= 0) return null
        val raw = sessionMMKV.getString(messageId.toString(), null) ?: return null
        return try {
            val session = Gson().fromJson(raw, ChatSession::class.java)
            if (System.currentTimeMillis() - session.createdAt > SESSION_TTL_MS) {
                sessionMMKV.remove(messageId.toString())
                null
            } else {
                session
            }
        } catch (e: Exception) {
            Log.e(logTag, "Failed to parse session $messageId: ${e.message}", e)
            sessionMMKV.remove(messageId.toString())
            null
        }
    }

    private fun saveSession(messageId: Long, session: ChatSession) {
        if (messageId <= 0) return
        session.createdAt = System.currentTimeMillis()
        sessionMMKV.putString(messageId.toString(), Gson().toJson(session))
    }

    private fun deleteSession(messageId: Long) {
        if (messageId <= 0) return
        sessionMMKV.remove(messageId.toString())
    }

    private fun purgeExpiredSessions() {
        val keys = sessionMMKV.allKeys() ?: return
        val now = System.currentTimeMillis()
        for (key in keys) {
            val raw = sessionMMKV.getString(key, null)
            if (raw == null) {
                sessionMMKV.remove(key)
                continue
            }
            try {
                val session = Gson().fromJson(raw, ChatSession::class.java)
                if (now - session.createdAt > SESSION_TTL_MS) {
                    sessionMMKV.remove(key)
                }
            } catch (e: Exception) {
                sessionMMKV.remove(key)
            }
        }
    }

    private fun smsConfirmKeyboard() = KeyboardMarkup().apply {
        inlineKeyboard = arrayListOf(
            getInlineKeyboardObj(getString(R.string.send_button), CALLBACK_DATA_VALUE.SEND),
            getInlineKeyboardObj(getString(R.string.cancel_button), CALLBACK_DATA_VALUE.CANCEL)
        )
    }

    private fun ussdConfirmKeyboard() = KeyboardMarkup().apply {
        inlineKeyboard = arrayListOf(
            getInlineKeyboardObj(getString(R.string.send_button), CALLBACK_DATA_VALUE.USSD_SEND),
            getInlineKeyboardObj(getString(R.string.cancel_button), CALLBACK_DATA_VALUE.USSD_CANCEL)
        )
    }

    private fun simSelectKeyboard() = KeyboardMarkup().apply {
        inlineKeyboard = arrayListOf(
            getInlineKeyboardObj("SIM 1", CALLBACK_DATA_VALUE.SIM1),
            getInlineKeyboardObj("SIM 2", CALLBACK_DATA_VALUE.SIM2)
        )
    }

    /** Start an interactive /sendsms flow, creating and persisting a session. */
    private fun startSmsFlow(requestMsg: String, jsonObject: JsonObject, requestBody: RequestMessage) {
        purgeExpiredSessions()
        var sendSlot = -1
        val isDualSim = getActiveCard(applicationContext) > 1
        val commandParts = requestMsg.split(" ", "\n", limit = 3).filter { it.isNotEmpty() }
        val baseCommand = commandParts[0].trim()
        if (isDualSim) {
            when (baseCommand) {
                "/sendsms1" -> sendSlot = 0
                "/sendsms2" -> sendSlot = 1
                "/sendsms" -> if (commandParts.size > 1) when (commandParts[1].trim()) {
                    "1" -> sendSlot = 0
                    "2" -> sendSlot = 1
                }
            }
        }
        val commandMessageId = jsonObject["message_id"].asLong
        val simSpecified = sendSlot != -1 && baseCommand == "/sendsms" &&
                commandParts.size > 1 && (commandParts[1] == "1" || commandParts[1] == "2")
        val msgSendList =
            requestMsg.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val phoneLineIndex = if (simSpecified) 2 else 1
        val hasPhoneNumber = msgSendList.size > phoneLineIndex

        if (hasPhoneNumber) {
            val msgSendTo = getSendPhoneNumber(msgSendList[phoneLineIndex])
            if (isPhoneNumber(msgSendTo)) {
                val sendContent = msgSendList.drop(phoneLineIndex + 1).joinToString("\n")
                val dualSim = if (sendSlot != -1) "SIM${sendSlot + 1} " else ""
                requestBody.replyMarkup = smsConfirmKeyboard()
                requestBody.text = Template.render(
                    applicationContext, "TPL_send_sms",
                    mapOf("SIM" to dualSim, "To" to msgSendTo, "Content" to sendContent)
                )
                val session = ChatSession().apply {
                    type = ChatSession.TYPE_SMS
                    step = SESSION_STEP.SMS_CONFIRM
                    slot = sendSlot
                    to = msgSendTo
                    content = sendContent
                    this.commandMessageId = commandMessageId
                }
                val id = sendMessageForId(requestBody)
                if (id > 0) saveSession(id, session)
            } else {
                requestBody.text = Template.render(
                    applicationContext, "TPL_send_sms_chat",
                    mapOf("SIM" to "", "Content" to getString(R.string.unable_get_phone_number))
                )
                asyncSend(requestBody)
            }
            return
        }

        // Interactive mode.
        Log.d(logTag, "Entering interactive SMS sending mode")
        if (isDualSim && sendSlot == -1) {
            requestBody.replyMarkup = simSelectKeyboard()
            requestBody.text = Template.render(
                applicationContext, "TPL_send_sms_chat",
                mapOf("SIM" to "", "Content" to getString(R.string.select_sim_card))
            )
            val session = ChatSession().apply {
                type = ChatSession.TYPE_SMS
                step = SESSION_STEP.SMS_SIM_SELECT
                this.commandMessageId = commandMessageId
            }
            val id = sendMessageForId(requestBody)
            if (id > 0) saveSession(id, session)
        } else {
            val dualSim = if (sendSlot != -1) "SIM${sendSlot + 1} " else ""
            requestBody.text = Template.render(
                applicationContext, "TPL_send_sms_chat",
                mapOf("SIM" to dualSim, "Content" to getString(R.string.enter_reply_number))
            )
            requestBody.replyMarkup = ForceReply()
            requestBody.replyToMessageId = commandMessageId
            requestBody.allowSendingWithoutReply = true
            val session = ChatSession().apply {
                type = ChatSession.TYPE_SMS
                step = SESSION_STEP.SMS_PHONE_INPUT
                slot = sendSlot
                this.commandMessageId = commandMessageId
            }
            val id = sendMessageForId(requestBody)
            if (id > 0) saveSession(id, session)
        }
    }

    /** Start an interactive /sendussd flow, creating and persisting a session. */
    private fun startUssdFlow(requestMsg: String, jsonObject: JsonObject, requestBody: RequestMessage) {
        if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    ActivityCompat.checkSelfPermission(
                        applicationContext, Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED)
        ) {
            requestBody.text = Template.render(
                applicationContext, "TPL_system_message",
                mapOf("Message" to getString(R.string.unknown_command))
            )
            asyncSend(requestBody)
            return
        }
        purgeExpiredSessions()
        val isDualSim = getActiveCard(applicationContext) > 1
        val commandList = requestMsg.split(" ").filter { it.isNotEmpty() }
        val baseCommand = commandList[0].trim()
        val commandMessageId = jsonObject["message_id"].asLong
        var ussdSlot = -1
        var codeIndex = 1
        if (isDualSim) {
            when (baseCommand) {
                "/sendussd1" -> ussdSlot = 0
                "/sendussd2" -> ussdSlot = 1
                "/sendussd" -> if (commandList.size > 1) when (commandList[1].trim()) {
                    "1" -> { ussdSlot = 0; codeIndex = 2 }
                    "2" -> { ussdSlot = 1; codeIndex = 2 }
                }
            }
        }

        if (commandList.size > codeIndex) {
            val ussdCode = commandList[codeIndex]
            if (isValidUssdCode(ussdCode)) {
                val dualSim = if (ussdSlot != -1) "SIM${ussdSlot + 1} " else ""
                requestBody.replyMarkup = ussdConfirmKeyboard()
                requestBody.text = Template.render(
                    applicationContext, "TPL_system_message",
                    mapOf("Message" to "${dualSim}USSD: $ussdCode")
                )
                val session = ChatSession().apply {
                    type = ChatSession.TYPE_USSD
                    step = SESSION_STEP.USSD_CONFIRM
                    slot = ussdSlot
                    this.ussdCode = ussdCode
                }
                val id = sendMessageForId(requestBody)
                if (id > 0) saveSession(id, session)
            } else {
                requestBody.text = Template.render(
                    applicationContext, "TPL_system_message",
                    mapOf("Message" to getString(R.string.invalid_ussd_code))
                )
                asyncSend(requestBody)
            }
            return
        }

        // Interactive mode.
        Log.d(logTag, "Entering interactive USSD sending mode")
        if (isDualSim && ussdSlot == -1) {
            requestBody.replyMarkup = simSelectKeyboard()
            requestBody.text = Template.render(
                applicationContext, "TPL_send_USSD_chat",
                mapOf("Content" to getString(R.string.select_sim_card))
            )
            val session = ChatSession().apply {
                type = ChatSession.TYPE_USSD
                step = SESSION_STEP.USSD_SIM_SELECT
                this.commandMessageId = commandMessageId
            }
            val id = sendMessageForId(requestBody)
            if (id > 0) saveSession(id, session)
        } else {
            val dualSim = if (ussdSlot != -1) "SIM${ussdSlot + 1} " else ""
            requestBody.text = Template.render(
                applicationContext, "TPL_send_USSD_chat",
                mapOf("Content" to "$dualSim${getString(R.string.enter_ussd_code)}")
            )
            // ForceReply so the user's code reply auto-binds to this prompt and resolves
            // the session by reply_to_message.message_id.
            requestBody.replyMarkup = ForceReply()
            requestBody.replyToMessageId = commandMessageId
            requestBody.allowSendingWithoutReply = true
            val session = ChatSession().apply {
                type = ChatSession.TYPE_USSD
                step = SESSION_STEP.USSD_CODE_INPUT
                slot = ussdSlot
                this.commandMessageId = commandMessageId
            }
            val id = sendMessageForId(requestBody)
            if (id > 0) saveSession(id, session)
        }
    }

    /**
     * A reply to a forwarded SMS notification (resolved via chatInfoMMKV -> SMSRequestInfo)
     * starts a confirm session. Returns true if it handled the message.
     */
    private fun startSmsReplyFromNotification(
        jsonObject: JsonObject,
        requestMsg: String,
        requestBody: RequestMessage
    ): Boolean {
        if (requestMsg.isEmpty()) return false
        val replyToMessage = jsonObject["reply_to_message"].asJsonObject
        val saveItemString =
            chatInfoMMKV.getString(replyToMessage["message_id"].asString, null) ?: return false
        val content = stripBotEntities(requestMsg, jsonObject)
        if (content.isEmpty()) return false
        val saveItem = Gson().fromJson(saveItemString, SMSRequestInfo::class.java)
        val dualSim = if (saveItem.card != -1) "SIM${saveItem.card + 1} " else ""
        requestBody.replyMarkup = smsConfirmKeyboard()
        requestBody.text = Template.render(
            applicationContext, "TPL_send_sms",
            mapOf("SIM" to dualSim, "To" to saveItem.phone, "Content" to content)
        )
        val session = ChatSession().apply {
            type = ChatSession.TYPE_SMS
            step = SESSION_STEP.SMS_CONFIRM
            slot = saveItem.card
            to = saveItem.phone
            this.content = content
        }
        val id = sendMessageForId(requestBody)
        if (id > 0) saveSession(id, session)
        return true
    }

    /** Drive a session forward from a text reply (phone / content / USSD code input). */
    private fun handleSessionText(
        session: ChatSession,
        promptMsgId: Long,
        requestMsg: String,
        jsonObject: JsonObject,
        requestBody: RequestMessage
    ) {
        // The prompt has now been answered; its session record is superseded below.
        deleteSession(promptMsgId)
        val anchorId = if (session.commandMessageId != 0L) {
            session.commandMessageId
        } else {
            jsonObject["message_id"].asLong
        }
        when (session.step) {
            SESSION_STEP.SMS_PHONE_INPUT -> {
                val dualSim = if (session.slot != -1) "SIM${session.slot + 1} " else ""
                val tempTo = getSendPhoneNumber(requestMsg)
                if (isPhoneNumber(tempTo)) {
                    session.to = tempTo
                    session.step = SESSION_STEP.SMS_CONTENT_INPUT
                    requestBody.text = Template.render(
                        applicationContext, "TPL_send_sms_chat",
                        mapOf("SIM" to dualSim, "Content" to getString(R.string.enter_reply_content))
                    )
                    requestBody.replyMarkup = ForceReply()
                    requestBody.replyToMessageId = anchorId
                    requestBody.allowSendingWithoutReply = true
                    val id = sendMessageForId(requestBody)
                    if (id > 0) saveSession(id, session)
                } else {
                    requestBody.text = Template.render(
                        applicationContext, "TPL_send_sms_chat",
                        mapOf("SIM" to dualSim, "Content" to getString(R.string.unable_get_phone_number))
                    )
                    asyncSend(requestBody)
                }
            }

            SESSION_STEP.SMS_CONTENT_INPUT -> {
                val dualSim = if (session.slot != -1) "SIM${session.slot + 1} " else ""
                session.content = requestMsg
                session.step = SESSION_STEP.SMS_CONFIRM
                requestBody.replyMarkup = smsConfirmKeyboard()
                requestBody.text = Template.render(
                    applicationContext, "TPL_send_sms",
                    mapOf("SIM" to dualSim, "To" to session.to, "Content" to session.content)
                )
                val id = sendMessageForId(requestBody)
                if (id > 0) saveSession(id, session)
            }

            SESSION_STEP.USSD_CODE_INPUT -> {
                val dualSim = if (session.slot != -1) "SIM${session.slot + 1} " else ""
                val ussdCode = requestMsg.trim()
                if (isValidUssdCode(ussdCode)) {
                    session.ussdCode = ussdCode
                    session.step = SESSION_STEP.USSD_CONFIRM
                    requestBody.replyMarkup = ussdConfirmKeyboard()
                    requestBody.text = Template.render(
                        applicationContext, "TPL_send_USSD_chat",
                        mapOf("Content" to "${dualSim}USSD: $ussdCode")
                    )
                    val id = sendMessageForId(requestBody)
                    if (id > 0) saveSession(id, session)
                } else {
                    requestBody.text = Template.render(
                        applicationContext, "TPL_send_USSD_chat",
                        mapOf("Content" to getString(R.string.invalid_ussd_code))
                    )
                    asyncSend(requestBody)
                }
            }

            else -> Log.w(logTag, "Text reply for non-text session step ${session.step}, ignoring")
        }
    }

    /** Drive a session forward from an inline-button tap (SIM select / send / cancel). */
    @SuppressLint("MissingPermission")
    private fun handleSessionCallback(
        session: ChatSession,
        msgId: Long,
        callbackData: String,
        requestBody: RequestMessage
    ) {
        when (session.step) {
            SESSION_STEP.SMS_SIM_SELECT -> {
                val slot = when (callbackData) {
                    CALLBACK_DATA_VALUE.SIM1 -> 0
                    CALLBACK_DATA_VALUE.SIM2 -> 1
                    else -> return
                }
                val simLabel = "SIM${slot + 1} "
                session.slot = slot
                session.step = SESSION_STEP.SMS_PHONE_INPUT
                // Acknowledge the choice on the keyboard message (clears the inline keyboard).
                val ackBody = RequestMessage().apply {
                    chatId = this@ChatService.chatId
                    messageThreadId = this@ChatService.messageThreadId
                    messageId = msgId
                    text = Template.render(
                        applicationContext, "TPL_send_sms_chat",
                        mapOf("SIM" to simLabel, "Content" to "✅")
                    )
                }
                callTelegramApi("editMessageText", ackBody)
                deleteSession(msgId)
                // Follow-up ForceReply prompt, anchored to the original command.
                requestBody.text = Template.render(
                    applicationContext, "TPL_send_sms_chat",
                    mapOf("SIM" to simLabel, "Content" to getString(R.string.enter_reply_number))
                )
                requestBody.replyMarkup = ForceReply()
                if (session.commandMessageId != 0L) {
                    requestBody.replyToMessageId = session.commandMessageId
                    requestBody.allowSendingWithoutReply = true
                }
                val id = sendMessageForId(requestBody)
                if (id > 0) saveSession(id, session)
            }

            SESSION_STEP.SMS_CONFIRM -> {
                var slot = session.slot
                val dualSim = Phone.getSimDisplayName(applicationContext, slot)
                if (callbackData != CALLBACK_DATA_VALUE.SEND) {
                    requestBody.text = Template.render(
                        applicationContext, "TPL_send_sms",
                        mapOf("SIM" to dualSim, "To" to session.to, "Content" to session.content)
                    ) + "\n" + getString(R.string.status) + getString(R.string.cancel_button)
                    requestBody.messageId = msgId
                    callTelegramApi("editMessageText", requestBody)
                    deleteSession(msgId)
                    return
                }
                requestBody.text = Template.render(
                    applicationContext, "TPL_send_sms",
                    mapOf("SIM" to dualSim, "To" to session.to, "Content" to session.content)
                ) + "\n" + getString(R.string.status) + getString(R.string.sending)
                requestBody.messageId = msgId
                callTelegramApi("editMessageText", requestBody)
                deleteSession(msgId)

                var subId = -1
                if (getActiveCard(applicationContext) == 1) {
                    slot = -1
                } else if (slot >= 0) {
                    subId = getSubId(applicationContext, slot)
                }
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.SEND_SMS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    send(applicationContext, session.to, session.content, slot, subId, msgId)
                }
            }

            SESSION_STEP.USSD_SIM_SELECT -> {
                val slot = when (callbackData) {
                    CALLBACK_DATA_VALUE.SIM1 -> 0
                    CALLBACK_DATA_VALUE.SIM2 -> 1
                    else -> return
                }
                val simLabel = "SIM${slot + 1} "
                session.slot = slot
                session.step = SESSION_STEP.USSD_CODE_INPUT
                // Acknowledge the choice on the keyboard message (clears the inline keyboard).
                val ackBody = RequestMessage().apply {
                    chatId = this@ChatService.chatId
                    messageThreadId = this@ChatService.messageThreadId
                    messageId = msgId
                    text = Template.render(
                        applicationContext, "TPL_send_USSD_chat",
                        mapOf("Content" to "$simLabel✅")
                    )
                }
                callTelegramApi("editMessageText", ackBody)
                deleteSession(msgId)
                // Follow-up ForceReply prompt so the code reply auto-binds and resolves the
                // session by its new message id (editMessageText can't carry ForceReply).
                requestBody.text = Template.render(
                    applicationContext, "TPL_send_USSD_chat",
                    mapOf("Content" to "$simLabel${getString(R.string.enter_ussd_code)}")
                )
                requestBody.replyMarkup = ForceReply()
                if (session.commandMessageId != 0L) {
                    requestBody.replyToMessageId = session.commandMessageId
                    requestBody.allowSendingWithoutReply = true
                }
                val id = sendMessageForId(requestBody)
                if (id > 0) saveSession(id, session)
            }

            SESSION_STEP.USSD_CONFIRM -> {
                val ussdSlot = session.slot
                val dualSim = if (ussdSlot != -1) "SIM${ussdSlot + 1} " else ""
                if (callbackData != CALLBACK_DATA_VALUE.USSD_SEND) {
                    requestBody.text = Template.render(
                        applicationContext, "TPL_send_USSD_chat",
                        mapOf(
                            "Content" to "${dualSim}USSD: ${session.ussdCode}\n${getString(R.string.status)}${
                                getString(R.string.cancel_button)
                            }"
                        )
                    )
                    requestBody.messageId = msgId
                    callTelegramApi("editMessageText", requestBody)
                    deleteSession(msgId)
                    return
                }
                requestBody.text = Template.render(
                    applicationContext, "TPL_send_USSD_chat",
                    mapOf(
                        "Content" to "${dualSim}USSD: ${session.ussdCode}\n${getString(R.string.status)}${
                            getString(R.string.sending)
                        }"
                    )
                )
                requestBody.messageId = msgId
                callTelegramApi("editMessageText", requestBody)
                deleteSession(msgId)

                var subId = -1
                if (getActiveCard(applicationContext) > 1 && ussdSlot >= 0) {
                    subId = getSubId(applicationContext, ussdSlot)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    sendUssd(applicationContext, session.ussdCode, subId, msgId)
                }
            }

            else -> Log.w(logTag, "Callback for non-button session step ${session.step}, ignoring")
        }
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
        // Static flag survives across service restarts in the same process — reset it so
        // the first poll after a restart uses the quick (timeout=0) drain as intended.
        firstRequest = true
        purgeExpiredSessions()
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
    }


    private fun callTelegramApi(method: String, requestBody: RequestMessage) {
        val requestUri = getUrl(botToken, method)
        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(JSON)
        val okhttpObj = getOkhttpObj(sharedPreferences.getBoolean("doh_switch", false))
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        try {
            okhttpObj.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    throw IOException(response.code.toString())
                }
            }
        } catch (e: IOException) {
            Log.e(logTag, "Failed to call Telegram API $method: ${e.message}", e)
        }
    }

    private fun isValidUssdCode(code: String): Boolean {
        // USSD codes typically start with * or # and end with #
        // They can contain digits, *, and #
        val ussdPattern = Regex("^[*#][0-9*#]+#?\$")
        return code.isNotEmpty() && ussdPattern.matches(code)
    }

    private fun stripBotEntities(text: String, jsonObject: JsonObject): String {
        if (!jsonObject.has("entities")) return text.trim()
        val entities = jsonObject["entities"].asJsonArray
        val ranges = entities.mapNotNull { entity ->
            val obj = entity.asJsonObject
            val type = obj["type"].asString
            if (type == "bot_command" || type == "mention") {
                val offset = obj["offset"].asInt
                offset to offset + obj["length"].asInt
            } else null
        }.sortedByDescending { it.first }
        var result = text
        for ((start, end) in ranges) {
            if (start in 0..result.length && end in start..result.length) {
                result = result.substring(0, start) + result.substring(end)
            }
        }
        return result.trim()
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

        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(JSON)
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(logTag, "Failed to edit message: ${e.message}", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.code != 200) {
                        Log.e(logTag, "Failed to edit message: ${it.code}")
                    }
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
                val body = Gson().toJson(requestBody).toRequestBody(JSON)
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
