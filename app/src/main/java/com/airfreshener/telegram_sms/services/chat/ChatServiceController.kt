package com.airfreshener.telegram_sms.services.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.common.data.LogRepository
import com.airfreshener.telegram_sms.common.data.PrefsRepository
import com.airfreshener.telegram_sms.common.data.TelegramRepository
import com.airfreshener.telegram_sms.common.data.UssdRepository
import com.airfreshener.telegram_sms.model.ReplyMarkupKeyboard
import com.airfreshener.telegram_sms.model.SmsRequestInfo
import com.airfreshener.telegram_sms.utils.Consts
import com.airfreshener.telegram_sms.utils.NetworkUtils
import com.airfreshener.telegram_sms.utils.OtherUtils
import com.airfreshener.telegram_sms.utils.PaperUtils
import com.airfreshener.telegram_sms.utils.PaperUtils.tryRead
import com.airfreshener.telegram_sms.utils.SmsUtils
import com.airfreshener.telegram_sms.utils.isNumeric
import com.google.gson.JsonObject
import java.util.Locale

class ChatServiceController(
    private val appContext: Context,
    private val prefsRepository: PrefsRepository,
    private val logRepository: LogRepository,
    private val telegramRepository: TelegramRepository,
    private val ussdRepository: UssdRepository,
) {

    var botUsername: String = ""
    var sendSmsNextStatus = Consts.SEND_SMS_STATUS.STANDBY_STATUS
    var offset: Long = 0
    var magnification = 1
    var errorMagnification = 1

    init {
        setSmsSendStatusStandby()
    }

    fun receiveHandle(resultObj: JsonObject, getIdOnly: Boolean) {
        val appContext = appContext
        val updateId = resultObj["update_id"].asLong
        offset = updateId + 1
        if (getIdOnly) {
            Log.d(TAG, "receive_handle: get_id_only")
            return
        }
        val sendBook = PaperUtils.getSendTempBook()
        var messageType = ""
        val settings = prefsRepository.getSettings()
        var messageObj: JsonObject? = null
        if (resultObj.has("message")) {
            messageObj = resultObj["message"].asJsonObject
            messageType = messageObj["chat"].asJsonObject["type"].asString
        }
        if (resultObj.has("channel_post")) {
            messageType = "channel"
            messageObj = resultObj["channel_post"].asJsonObject
        }
        var callbackData: String? = null
        if (resultObj.has("callback_query")) {
            messageType = "callback_query"
            val callbackQuery = resultObj["callback_query"].asJsonObject
            callbackData = callbackQuery["data"].asString
        }
        if (messageType == "callback_query" && sendSmsNextStatus != Consts.SEND_SMS_STATUS.STANDBY_STATUS) {
            var slot = sendBook.tryRead("slot", -1)
            val messageId = sendBook.tryRead("message_id", -1L)
            val to = sendBook.tryRead("to", "")
            val content = sendBook.tryRead("content", "")
            assert(callbackData != null)
            if (callbackData != Consts.CALLBACK_DATA_VALUE.SEND) {
                setSmsSendStatusStandby()
                val message = getSendSmsHead() + "\n" +
                        appContext.getString(R.string.to) + to + "\n" +
                        appContext.getString(R.string.content) + content + "\n" +
                        appContext.getString(R.string.status) + appContext.getString(R.string.cancel_button)
                telegramRepository.sendMessage(message = message, messageId = messageId)
                return
            }
            var subId = -1
            if (OtherUtils.getActiveCard(appContext) == 1) {
                slot = -1
            } else {
                subId = OtherUtils.getSubId(appContext, slot)
            }
            SmsUtils.sendSms(
                context = appContext,
                sendTo = to,
                content = content,
                slot = slot,
                subId = subId,
                messageId = messageId,
            )
            setSmsSendStatusStandby()
            return
        }
        if (messageObj == null) {
            logRepository.writeLog("Request type is not allowed by security policy.")
            return
        }
        var fromObj: JsonObject? = null
        val messageTypeIsPrivate = messageType == "private"
        if (messageObj.has("from")) {
            fromObj = messageObj["from"].asJsonObject
            if (!messageTypeIsPrivate && fromObj["is_bot"].asBoolean) {
                Log.i(TAG, "receive_handle: receive from bot.")
                return
            }
        }
        if (messageObj.has("chat")) {
            fromObj = messageObj["chat"].asJsonObject
        }
        assert(fromObj != null)
        val fromId = fromObj!!["id"].asString
        if (settings.chatId != fromId) {
            logRepository.writeLog("Chat ID[$fromId] not allow.")
            return
        }
        var commandBotUsername = ""
        var requestMsg = ""
        if (messageObj.has("text")) {
            requestMsg = messageObj["text"].asString
        }
        if (messageObj.has("reply_to_message")) {
            val saveItem = PaperUtils.getDefaultBook().read<SmsRequestInfo>(
                messageObj["reply_to_message"].asJsonObject["message_id"].asString,
                null
            )
            if (saveItem != null && requestMsg.isNotEmpty()) {
                val phoneNumber = saveItem.phone ?: "-"
                val cardSlot = saveItem.card
                sendSmsNextStatus = Consts.SEND_SMS_STATUS.WAITING_TO_SEND_STATUS
                sendBook
                    .write("slot", cardSlot)
                    .write("to", phoneNumber)
                    .write("content", requestMsg)
            }
            if (!messageTypeIsPrivate) {
                Log.i(TAG, "receive_handle: The message id could not be found, ignored.")
                return
            }
        }
        var command = ""
        if (messageObj.has("entities")) {
            val tempCommand: String
            val tempCommandLowercase: String
            val entitiesArr = messageObj["entities"].asJsonArray
            val entitiesObjCommand = entitiesArr[0].asJsonObject
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
                    commandBotUsername = tempCommand.substring(commandAtLocation + 1)
                }
            }
        }
        if (!messageTypeIsPrivate && settings.isPrivacyMode && commandBotUsername != botUsername) {
            Log.i(TAG, "receive_handle: Privacy mode, no username found.")
            return
        }
        Log.d(TAG, "receive_handle: $command")
        var message: String
        var hasCommand = false
        when (command) {
            "/help", "/start", "/commandlist" -> {
                var smsCommand = appContext.getString(R.string.sendsms)
                if (OtherUtils.getActiveCard(appContext) == 2) {
                    smsCommand = appContext.getString(R.string.sendsms_dual)
                }
                smsCommand += """
                
                ${appContext.getString(R.string.get_spam_sms)}
                """.trimIndent()
                var ussdCommand = ""
                if (ActivityCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ussdCommand = """
                            ${appContext.getString(R.string.send_ussd_command)}
                            """.trimIndent()
                        if (OtherUtils.getActiveCard(appContext) == 2) {
                            ussdCommand = """
                                ${appContext.getString(R.string.send_ussd_dual_command)}
                                """.trimIndent()
                        }
                    }
                }
                if (command == "/commandlist") {
                    message = """${appContext.getString(R.string.available_command)}
                        $smsCommand$ussdCommand""".trimIndent()
                        .replace("/", "")
                } else {
                    var result = """
                        ${appContext.getString(R.string.available_command)}
                        $smsCommand$ussdCommand
                        """.trimIndent()
                    if (!messageTypeIsPrivate && settings.isPrivacyMode && botUsername != "") {
                        result = result.replace(" -", "@$botUsername -")
                    }
                    message = result
                    hasCommand = true
                }
            }

            "/ping", "/getinfo" -> {
                var cardInfo = ""
                if (ActivityCompat.checkSelfPermission(
                        appContext,
                        Manifest.permission.READ_PHONE_STATE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    cardInfo = "SIM: ${OtherUtils.getSimDisplayName(appContext, 0)}"
                    if (OtherUtils.getActiveCard(appContext) == 2) {
                        cardInfo += "\nSIM2: ${OtherUtils.getSimDisplayName(appContext, 1)}"
                    }
                }
                val spamList = PaperUtils.getDefaultBook().read("spam_sms_list", ArrayList<String>())!!
                val spamCount = "${appContext.getString(R.string.spam_count_title)}${spamList.size}"
                message = """
                    ${appContext.getString(R.string.current_battery_level)}${ChatServiceUtils.getBatteryInfo(appContext)}
                    ${appContext.getString(R.string.current_network_connection_status)}${
                    ChatServiceUtils.getNetworkType(
                        appContext
                    )
                }
                    $cardInfo
                    $spamCount
                    """.trimIndent()
                hasCommand = true
            }

            "/log" -> {
                val cmdList: Array<String?> =
                    requestMsg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                var line = 10
                if (cmdList.size == 2 && cmdList[1].isNumeric()) {
                    val lineCommand = cmdList.getOrNull(1)?.toIntOrNull() ?: line
                    line = lineCommand.coerceAtMost(50)
                }
                message = logRepository.logs.value.takeLast(line).joinToString(separator = "\n")
                hasCommand = true
            }

            "/sendussd", "/sendussd1", "/sendussd2" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (ActivityCompat.checkSelfPermission(
                            appContext,
                            Manifest.permission.CALL_PHONE
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val commandList =
                            requestMsg.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        var subId = -1
                        if (OtherUtils.getActiveCard(appContext) == 2) {
                            if (command == "/sendussd2") {
                                subId = OtherUtils.getSubId(appContext, 1)
                            }
                        }
                        if (commandList.size == 2) {
                            ussdRepository.sendUssd(commandList[1], subId)
                            return
                        }
                    } else {
                        telegramRepository.sendMessage("Can't get permission to make call")
                        return
                    }
                } else {
                    telegramRepository.sendMessage("Device does not support sending ussd request automatically")
                    return
                }
                message = appContext.getString(R.string.unknown_command)
            }

            "/getspamsms" -> {
                val spamSmsList = PaperUtils.getDefaultBook().read("spam_sms_list", ArrayList<String>())!!
                if (spamSmsList.size == 0) {
                    message = appContext.getString(R.string.no_spam_history)
                } else {
                    sendSpamSmsMessage(spamSmsList)
                    return
                }
            }

            "/sendsms", "/sendsms1", "/sendsms2" -> {
                val msgSendList =
                    requestMsg.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (msgSendList.size > 2) {
                    val msgSendTo = OtherUtils.getSendPhoneNumber(msgSendList[1])
                    if (OtherUtils.isPhoneNumber(msgSendTo)) {
                        val msgSendContent = StringBuilder()
                        var i = 2
                        while (i < msgSendList.size) {
                            if (msgSendList.size != 3 && i != 2) {
                                msgSendContent.append("\n")
                            }
                            msgSendContent.append(msgSendList[i])
                            ++i
                        }
                        if (OtherUtils.getActiveCard(appContext) == 1) {
                            SmsUtils.sendSms(appContext, msgSendTo, msgSendContent.toString(), -1, -1)
                            return
                        }
                        var sendSlot = -1
                        if (OtherUtils.getActiveCard(appContext) > 1) {
                            sendSlot = 0
                            if (command == "/sendsms2") {
                                sendSlot = 1
                            }
                        }
                        val subId = OtherUtils.getSubId(appContext, sendSlot)
                        if (subId != -1) {
                            SmsUtils.sendSms(
                                appContext,
                                msgSendTo,
                                msgSendContent.toString(),
                                sendSlot,
                                subId
                            )
                            return
                        }
                    }
                } else {
                    sendSmsNextStatus = Consts.SEND_SMS_STATUS.PHONE_INPUT_STATUS
                    var sendSlot = -1
                    if (OtherUtils.getActiveCard(appContext) > 1) {
                        sendSlot = 0
                        if (command == "/sendsms2") {
                            sendSlot = 1
                        }
                    }
                    sendBook.write("slot", sendSlot)
                }
                message = getSendSmsHead() + "\n" + appContext.getString(R.string.failed_to_get_information)
            }

            else -> {
                if (!messageTypeIsPrivate && sendSmsNextStatus == -1) {
                    Log.i(TAG, "receive_handle: The conversation is not Private and does not prompt an error.")
                    return
                }
                message = appContext.getString(R.string.unknown_command)
            }
        }
        if (hasCommand) {
            setSmsSendStatusStandby()
        }
        var keyboardMarkup: ReplyMarkupKeyboard.KeyboardMarkup? = null
        if (!hasCommand && sendSmsNextStatus != -1) {
            Log.i(TAG, "receive_handle: Enter the interactive SMS sending mode.")
            val resultSend: String
            Log.d(TAG, "Sending mode status: $sendSmsNextStatus")
            when (sendSmsNextStatus) {
                Consts.SEND_SMS_STATUS.PHONE_INPUT_STATUS -> {
                    sendSmsNextStatus = Consts.SEND_SMS_STATUS.MESSAGE_INPUT_STATUS
                    resultSend = appContext.getString(R.string.enter_number)
                }

                Consts.SEND_SMS_STATUS.MESSAGE_INPUT_STATUS -> {
                    val tempTo = OtherUtils.getSendPhoneNumber(requestMsg)
                    if (OtherUtils.isPhoneNumber(tempTo)) {
                        sendBook.write("to", tempTo)
                        resultSend = appContext.getString(R.string.enter_content)
                        sendSmsNextStatus = Consts.SEND_SMS_STATUS.WAITING_TO_SEND_STATUS
                    } else {
                        setSmsSendStatusStandby()
                        resultSend = appContext.getString(R.string.unable_get_phone_number)
                    }
                }

                Consts.SEND_SMS_STATUS.WAITING_TO_SEND_STATUS -> {
                    sendBook.write("content", requestMsg)
                    keyboardMarkup = ReplyMarkupKeyboard.KeyboardMarkup().apply {
                        inline_keyboard = ReplyMarkupKeyboard.getInlineKeyboardObj(
                            appContext.getString(R.string.send_button) to Consts.CALLBACK_DATA_VALUE.SEND,
                            appContext.getString(R.string.cancel_button) to Consts.CALLBACK_DATA_VALUE.CANCEL
                        )
                    }
                    resultSend = appContext.getString(R.string.to) + sendBook.tryRead("to", "") + "\n" +
                        appContext.getString(R.string.content) + sendBook.tryRead("content", "")
                    sendSmsNextStatus = Consts.SEND_SMS_STATUS.SEND_STATUS
                }
                else -> {
                    resultSend = appContext.getString(R.string.failed_to_get_information)
                }
            }
            message = getSendSmsHead() + "\n" + resultSend
        }
        telegramRepository.sendMessage(
            message = message,
            keyboardMarkup = keyboardMarkup,
            resendOnFail = true,
            onSuccess = { responseMessageId ->
                if (sendSmsNextStatus == Consts.SEND_SMS_STATUS.SEND_STATUS) {
                    sendBook.write("message_id", responseMessageId)
                }
            },
        )
    }

    private fun sendSpamSmsMessage(spamSmsList: ArrayList<String>) {
        if (NetworkUtils.checkNetworkStatus(appContext)) {
            telegramRepository.sendMessage(
                message = "Spam sms list:\n" + spamSmsList.joinToString(separator = "\n"),
                onSuccess = {
                    PaperUtils.getDefaultBook().write("spam_sms_list", emptyList<String>())
                    logRepository.writeLog("Send spam message is complete.")
                }
            )
        }
    }

    private fun setSmsSendStatusStandby() {
        Log.d(TAG, "set_sms_send_status_standby: ")
        sendSmsNextStatus = Consts.SEND_SMS_STATUS.STANDBY_STATUS
        PaperUtils.getSendTempBook().destroy()
    }

    private fun getSendSmsHead(): String {
        var dualSim = ""
        val sendSlotTemp = PaperUtils.getSendTempBook().tryRead("slot", -1)
        if (sendSlotTemp != -1) {
            dualSim = "SIM" + (sendSlotTemp + 1) + " "
        }
        return "[" + dualSim + appContext.getString(R.string.send_sms_head) + "]"
    }

    companion object {
        private val TAG = ChatServiceController::class.java.simpleName
    }
}
