package com.qwe7002.telegram_sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.Process
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.qwe7002.telegram_sms.config.proxy
import com.qwe7002.telegram_sms.data_structure.PollingBody
import com.qwe7002.telegram_sms.data_structure.ReplyMarkupKeyboard.KeyboardMarkup
import com.qwe7002.telegram_sms.data_structure.ReplyMarkupKeyboard.getInlineKeyboardObj
import com.qwe7002.telegram_sms.data_structure.RequestMessage
import com.qwe7002.telegram_sms.data_structure.SMSRequestInfo
import com.qwe7002.telegram_sms.static_class.ChatCommand.getCommandList
import com.qwe7002.telegram_sms.static_class.ChatCommand.getInfo
import com.qwe7002.telegram_sms.static_class.Logs.readLog
import com.qwe7002.telegram_sms.static_class.Logs.writeLog
import com.qwe7002.telegram_sms.static_class.Network.checkNetworkStatus
import com.qwe7002.telegram_sms.static_class.Network.getOkhttpObj
import com.qwe7002.telegram_sms.static_class.Network.getUrl
import com.qwe7002.telegram_sms.static_class.Other.getActiveCard
import com.qwe7002.telegram_sms.static_class.Other.getDualSimCardDisplay
import com.qwe7002.telegram_sms.static_class.Other.getMessageId
import com.qwe7002.telegram_sms.static_class.Other.getNotificationObj
import com.qwe7002.telegram_sms.static_class.Other.getSendPhoneNumber
import com.qwe7002.telegram_sms.static_class.Other.getSubId
import com.qwe7002.telegram_sms.static_class.Other.isPhoneNumber
import com.qwe7002.telegram_sms.static_class.Other.parseStringToLong
import com.qwe7002.telegram_sms.static_class.Resend.addResendLoop
import com.qwe7002.telegram_sms.static_class.SMS.send
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.static_class.USSD.sendUssd
import com.qwe7002.telegram_sms.value.constValue
import com.qwe7002.telegram_sms.value.notifyId
import io.paperdb.Paper
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

class ChatService : Service() {
    @Suppress("ClassName")
    private object CALLBACK_DATA_VALUE {
        const val SEND: String = "send"
        const val CANCEL: String = "cancel"
    }

    @Suppress("ClassName")
    private object SEND_SMS_STATUS {
        const val STANDBY_STATUS: Int = -1
        const val PHONE_INPUT_STATUS: Int = 0
        const val MESSAGE_INPUT_STATUS: Int = 1
        const val WAITING_TO_SEND_STATUS: Int = 2
        const val READY_TO_SEND_STATUS: Int = 4
        const val SEND_STATUS: Int = 3
    }

    private lateinit var chatId: String
    private lateinit var botToken: String
    private lateinit var messageThreadId: String
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var killReceiver: StopReceiver
    private lateinit var wakelock: WakeLock
    private lateinit var wifiLock: WifiLock
    private lateinit var botUsername: String
    private val TAG = "chat_command_service"
    private var privacyMode = false

    private fun receiveHandle(resultObj: JsonObject, getIdOnly: Boolean) {
        val updateId = resultObj["update_id"].asLong
        RequestOffset = updateId + 1
        if (getIdOnly) {
            Log.d(TAG, "receive_handle: get_id_only")
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
        if (resultObj.has("callback_query")) {
            messageType = "callback_query"
            val callbackQuery = resultObj["callback_query"].asJsonObject
            callbackData = callbackQuery["data"].asString
        }
        if (messageType == "callback_query" && sendSmsNextStatus != SEND_SMS_STATUS.STANDBY_STATUS) {
            var slot = Paper.book("send_temp").read("slot", -1)!!
            val messageId =
                Paper.book("send_temp").read("message_id", -1L)!!
            val to = Paper.book("send_temp").read("to", "").toString()
            val content = Paper.book("send_temp").read("content", "").toString()
            if (callbackData != CALLBACK_DATA_VALUE.SEND) {
                setSmsSendStatusStandby()
                val requestUri = getUrl(
                    botToken, "editMessageText"
                )
                val dualSim = getDualSimCardDisplay(
                    applicationContext,
                    slot,
                    sharedPreferences.getBoolean("display_dual_sim_display_name", false)
                )
                requestBody.text = Template.render(
                    applicationContext,
                    "TPL_send_sms",
                    mapOf("SIM" to dualSim, "To" to to, "Content" to content)
                ) + "\n" + getString(R.string.status) + getString(R.string.cancel_button)
                requestBody.messageId = messageId
                val gson = Gson()
                val requestBodyRaw = gson.toJson(requestBody)
                val body: RequestBody = requestBodyRaw.toRequestBody(constValue.JSON)
                val okhttpObj = getOkhttpObj(
                    sharedPreferences.getBoolean("doh_switch", true),
                    Paper.book("system_config").read("proxy_config", proxy())
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
                    e.printStackTrace()
                    writeLog(applicationContext, "failed to send message:" + e.message)
                }
                return
            }
            var subId = -1
            if (getActiveCard(applicationContext) == 1) {
                slot = -1
            } else {
                subId = getSubId(applicationContext, slot)
            }
            send(applicationContext, to, content, slot, subId, messageId)
            setSmsSendStatusStandby()
            return
        }
        lateinit var fromObj: JsonObject
        val isPrivate = messageType == "private"
        if (jsonObject.has("from")) {
            fromObj = jsonObject["from"].asJsonObject
            if (!isPrivate && fromObj["is_bot"].asBoolean) {
                Log.i(TAG, "receive_handle: receive from bot.")
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
                Log.i(TAG, "Topic ID[$fromTopicId] not allow.")
                return
            }
        }
        if (chatId != fromId) {
            Log.i(TAG, "Chat ID[$fromId] not allow.")
            return
        }
        var command = ""
        var currentBotUsername = ""
        var requestMsg = ""
        if (jsonObject.has("text")) {
            requestMsg = jsonObject["text"].asString
        }
        if (jsonObject.has("reply_to_message")) {
            val saveItem = Paper.book().read<SMSRequestInfo>(
                jsonObject["reply_to_message"].asJsonObject["message_id"].asString,
                null
            )
            if (saveItem != null && requestMsg.isNotEmpty()) {
                val phoneNumber = saveItem.phone
                val cardSlot = saveItem.card
                sendSmsNextStatus = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS
                Paper.book("send_temp").write("slot", cardSlot).write("to", phoneNumber)
                    .write("content", requestMsg)
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
        if (!isPrivate && privacyMode && currentBotUsername != botUsername) {
            Log.i(TAG, "receive_handle: Privacy mode, no username found.")
            return
        }
        Log.d(TAG, "receive_handle: $command")
        var hasCommand = false
        when (command) {
            "/help", "/start", "/commandlist" -> {
                requestBody.text = getCommandList(
                    applicationContext, command, isPrivate, privacyMode,
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
                    var getLine = Integer.getInteger(commands[1])?.toInt()!!
                    if (getLine > 50) {
                        getLine = 50
                    }
                    line = getLine
                }
                //requestBody.text =
                //getString(R.string.system_message_head) + readLog(applicationContext, line)
                requestBody.text = Template.render(
                    applicationContext, "TPL_system_message",
                    mapOf("Message" to readLog(applicationContext, line))
                )
                hasCommand = true
            }

            "/sendussd", "/sendussd1", "/sendussd2" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.CALL_PHONE
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val commandList =
                            requestMsg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        var subId = -1
                        if (getActiveCard(applicationContext) == 2) {
                            if (command == "/sendussd2") {
                                subId = getSubId(
                                    applicationContext, 1
                                )
                            }
                        }
                        if (commandList.size == 2) {
                            sendUssd(applicationContext, commandList[1], subId)
                            return
                        }
                    }
                }
                //requestBody.text =
                //getString(R.string.system_message_head) + "\n" + getString(R.string.unknown_command)
                requestBody.text = Template.render(
                    applicationContext, "TPL_system_message",
                    mapOf("Message" to getString(R.string.unknown_command))
                )
            }

            "/getspamsms" -> {
                val spamSMSHistory = Paper.book().read("spam_sms_list", ArrayList<String>())
                checkNotNull(spamSMSHistory)
                if (spamSMSHistory.isNotEmpty()) {
                    Thread {
                        if (checkNetworkStatus(applicationContext)) {
                            val okhttpObj =
                                getOkhttpObj(
                                    sharedPreferences.getBoolean(
                                        "doh_switch",
                                        true
                                    ),
                                    Paper.book("system_config")
                                        .read("proxy_config", proxy())
                                )
                            for (item in spamSMSHistory) {
                                val sendSmsRequestBody = RequestMessage()
                                sendSmsRequestBody.chatId = chatId
                                sendSmsRequestBody.text = item
                                sendSmsRequestBody.messageThreadId = messageThreadId
                                val requestUri =
                                    getUrl(
                                        botToken, "sendMessage"
                                    )
                                val requestBodyJson = Gson().toJson(sendSmsRequestBody)
                                val body: RequestBody =
                                    requestBodyJson.toRequestBody(constValue.JSON)
                                val requestObj: Request =
                                    Request.Builder().url(requestUri).method("POST", body).build()
                                val call = okhttpObj.newCall(requestObj)
                                call.enqueue(object : Callback {
                                    override fun onFailure(call: Call, e: IOException) {
                                        Log.d(TAG, "onFailure: " + e.message)
                                        e.printStackTrace()
                                        writeLog(applicationContext, e.message!!)
                                    }

                                    override fun onResponse(
                                        call: Call,
                                        response: Response
                                    ) {
                                        Log.d(TAG, "onResponse: " + response.code)
                                    }
                                })
                                val resendListLocal =
                                    checkNotNull(
                                        Paper.book()
                                            .read(
                                                "spam_sms_list",
                                                ArrayList<String>()
                                            )
                                    )
                                resendListLocal.remove(item)
                                Paper.book().write(
                                    "spam_sms_list",
                                    resendListLocal
                                )
                            }
                        }
                        writeLog(applicationContext, "Send spam message is complete.")
                    }.start()
                    return
                } else {
                    /*requestBody.text =
                        getString(R.string.system_message_head) + "\n" + getString(R.string.no_spam_history)*/
                    requestBody.text = Template.render(
                        applicationContext, "TPL_system_message",
                        mapOf("Message" to getString(R.string.no_spam_history))
                    )
                }
            }

            "/sendsms", "/sendsms1", "/sendsms2" -> {
                var sendSlot = -1
                if (getActiveCard(applicationContext) > 1) {
                    sendSlot = 0
                    if (command == "/sendsms2") {
                        sendSlot = 1
                    }
                }
                val msgSendList =
                    requestMsg.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                Log.i(TAG, "msgSendList: " + msgSendList.size)
                if (msgSendList.size > 1) {
                    sendSmsNextStatus = SEND_SMS_STATUS.READY_TO_SEND_STATUS
                    val msgSendTo = getSendPhoneNumber(
                        msgSendList[1]
                    )
                    if (isPhoneNumber(msgSendTo)) {
                        Paper.book("send_temp").write("to", msgSendTo)
                        val sendContent = msgSendList.drop(2).joinToString("\n")
                        if (getActiveCard(applicationContext) == 1) {
                            Paper.book("send_temp").read("slot", -1)
                            Paper.book("send_temp").write("content", sendContent)
                        } else {
                            val subId = getSubId(applicationContext, sendSlot)
                            Paper.book("send_temp").read("slot", subId)
                            Paper.book("send_temp").write("content", sendContent)
                        }
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

                } else {
                    Log.i(TAG, "Enter the interactive SMS sending mode")
                    Log.i(TAG, "receiveHandle: $messageType")
                    sendSmsNextStatus = SEND_SMS_STATUS.PHONE_INPUT_STATUS
                    Paper.book("send_temp").write("slot", sendSlot)
                }
            }

            else -> {
                if (!isPrivate && sendSmsNextStatus == -1) {
                    if (messageType != "supergroup" || messageThreadId.isEmpty()) {
                        Log.i(
                            TAG,
                            "receive_handle: The conversation is not Private and does not prompt an error."
                        )
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
            Log.i(TAG, "receiveHandle: Enter the state of standby.")
            setSmsSendStatusStandby()
        }
        if (!hasCommand && sendSmsNextStatus != -1) {
            Log.i(TAG, "receive_handle: Enter the interactive SMS sending mode.")
            val sendSlotTemp = Paper.book("send_temp").read("slot", -1)!!
            val dualSim = if (sendSlotTemp != -1) "SIM${sendSlotTemp + 1} " else ""

            var resultSend = Template.render(
                applicationContext,
                "TPL_send_sms_chat",
                mapOf("SIM" to dualSim, "Content" to getString(R.string.failed_to_get_information))
            )
            Log.d(TAG, "Sending mode status: $sendSmsNextStatus")
            resultSend = when (sendSmsNextStatus) {
                SEND_SMS_STATUS.PHONE_INPUT_STATUS -> {
                    sendSmsNextStatus = SEND_SMS_STATUS.MESSAGE_INPUT_STATUS
                    Template.render(
                        applicationContext,
                        "TPL_send_sms_chat",
                        mapOf("SIM" to dualSim, "Content" to getString(R.string.enter_number))
                    )
                }

                SEND_SMS_STATUS.MESSAGE_INPUT_STATUS -> {
                    val tempTo = getSendPhoneNumber(requestMsg)
                    if (isPhoneNumber(tempTo)) {
                        Paper.book("send_temp").write("to", tempTo)
                        sendSmsNextStatus = SEND_SMS_STATUS.WAITING_TO_SEND_STATUS
                        Template.render(
                            applicationContext,
                            "TPL_send_sms_chat",
                            mapOf("SIM" to dualSim, "Content" to getString(R.string.enter_content))
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

                SEND_SMS_STATUS.WAITING_TO_SEND_STATUS, SEND_SMS_STATUS.READY_TO_SEND_STATUS -> {
                    Paper.book("send_temp").write("content", requestMsg)
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
                        "To" to Paper.book("send_temp").read("to", "").toString(),
                        "Content" to Paper.book("send_temp").read("content", "").toString()
                    )
                    sendSmsNextStatus = SEND_SMS_STATUS.SEND_STATUS
                    Template.render(applicationContext, "TPL_send_sms", values)
                }

                else -> resultSend
            }
            requestBody.text = resultSend
        }

        val requestUri = getUrl(
            botToken, "sendMessage"
        )
        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(constValue.JSON)
        val sendRequest: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okHttpClient.newCall(sendRequest)
        val errorHead = "Send reply failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                writeLog(applicationContext, errorHead + e.message)
                addResendLoop(applicationContext, requestBody.text)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val responseString = Objects.requireNonNull(response.body).string()
                if (response.code != 200) {
                    writeLog(applicationContext, errorHead + response.code + " " + responseString)
                    addResendLoop(applicationContext, requestBody.text)
                }
                if (sendSmsNextStatus == SEND_SMS_STATUS.SEND_STATUS) {
                    Paper.book("send_temp").write("message_id", getMessageId(responseString))
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
                notifyId.CHAT_COMMAND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(notifyId.CHAT_COMMAND, notification)
        }
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    @SuppressLint("InvalidWakeLockTag", "WakelockTimeout", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        Paper.init(applicationContext)
        setSmsSendStatusStandby()
        sharedPreferences = applicationContext.getSharedPreferences("data", MODE_PRIVATE)
        chatId = sharedPreferences.getString("chat_id", "").toString()
        botToken = sharedPreferences.getString("bot_token", "").toString()
        messageThreadId = sharedPreferences.getString("message_thread_id", "").toString()
        okHttpClient = getOkhttpObj(
            sharedPreferences.getBoolean("doh_switch", true),
            Paper.book("system_config").read("proxy_config", proxy())
        )
        privacyMode = sharedPreferences.getBoolean("privacy_mode", false)
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

        threadMain = Thread(ThreadMainRunnable())
        threadMain.start()
        val intentFilter = IntentFilter()
        intentFilter.addAction(constValue.BROADCAST_STOP_SERVICE)
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        killReceiver = StopReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(killReceiver, intentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(killReceiver, intentFilter)
        }
    }

    private val me: Boolean
        get() {
            val requestUri = getUrl(botToken, "getMe")
            val request = Request.Builder().url(requestUri).build()
            return try {
                val response = okHttpClient.newCall(request).execute()
                if (response.code == 200) {
                    val result = response.body.string()
                    val resultObj = JsonParser.parseString(result).asJsonObject
                    if (resultObj["ok"].asBoolean) {
                        botUsername = resultObj["result"].asJsonObject["username"].asString
                        Paper.book().write("bot_username", botUsername)
                        Log.d(TAG, "bot_username: $botUsername")
                        writeLog(applicationContext, "Get the bot username: $botUsername")
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            } catch (e: IOException) {
                e.printStackTrace()
                writeLog(applicationContext, "Get username failed: ${e.message}")
                false
            }
        }

    private fun setSmsSendStatusStandby() {
        Log.d(TAG, "set_sms_send_status_standby")
        sendSmsNextStatus = SEND_SMS_STATUS.STANDBY_STATUS
        Paper.book("send_temp").destroy()
    }

    @Suppress("DEPRECATION")
    override fun onDestroy() {
        wifiLock.release()
        wakelock.release()
        unregisterReceiver(killReceiver)
        stopForeground(true)
        super.onDestroy()
    }

private inner class ThreadMainRunnable : Runnable {
    override fun run() {
        Log.d(TAG, "run: thread main start")
        if (parseStringToLong(chatId) < 0) {
            botUsername = Paper.book().read<String>("bot_username", "").toString()
            if (botUsername.isEmpty()) {
                while (!me) {
                    writeLog(applicationContext, "Failed to get bot Username, Wait 5 seconds and try again.")
                    Thread.sleep(5000)
                }
            }
            Log.i(TAG, "run: The Bot Username is loaded. The Bot Username is: $botUsername")
        }
        while (true) {
            val timeout = 60
            val httpTimeout = 65
            val okhttpClientNew = okHttpClient.newBuilder()
                .readTimeout(httpTimeout.toLong(), TimeUnit.SECONDS)
                .writeTimeout(httpTimeout.toLong(), TimeUnit.SECONDS)
                .build()
            val requestUri = getUrl(botToken, "getUpdates")
            val requestBody = PollingBody().apply {
                this.offset = RequestOffset
                this.timeout = if (firstRequest) 0 else timeout
            }
            val body = Gson().toJson(requestBody).toRequestBody(constValue.JSON)
            val request = Request.Builder().url(requestUri).post(body).build()
            try {
                val response = okhttpClientNew.newCall(request).execute()
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
                } else {
                    writeLog(applicationContext, "Chat command response code: ${response.code}")
                }
            } catch (e: IOException) {
                e.printStackTrace()
                if (!checkNetworkStatus(applicationContext)) {
                    writeLog(applicationContext, "No network connections available, Wait for the network to recover.")
                    Log.d(TAG, "run: break loop.")
                    break
                }
                writeLog(applicationContext, "Connection to the Telegram API service failed.")
                Thread.sleep(5000)
            }
        }
    }
}


    override fun onBind(intent: Intent): IBinder? {
        return null
    }


    private inner class StopReceiver : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "onReceive: " + intent.action)
            checkNotNull(intent.action)
            when (intent.action) {
                constValue.BROADCAST_STOP_SERVICE -> {
                    Log.i(TAG, "Received stop signal, quitting now...")
                    stopSelf()
                    Process.killProcess(Process.myPid())
                }

                ConnectivityManager.CONNECTIVITY_ACTION -> if (checkNetworkStatus(context)) {
                    if (!threadMain.isAlive) {
                        writeLog(context, "Network connections has been restored.")
                        threadMain = Thread(ThreadMainRunnable())
                        threadMain.start()
                    }
                }
            }
        }
    }

    companion object {
        private var RequestOffset: Long = 0
        private lateinit var sharedPreferences: SharedPreferences
        private var sendSmsNextStatus = SEND_SMS_STATUS.STANDBY_STATUS
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
    }
}

