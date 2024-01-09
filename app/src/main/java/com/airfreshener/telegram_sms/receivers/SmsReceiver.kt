package com.airfreshener.telegram_sms.receivers

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.TelegramSmsApp
import com.airfreshener.telegram_sms.model.RequestMessage
import com.airfreshener.telegram_sms.utils.LogUtils
import com.airfreshener.telegram_sms.utils.NetworkUtils.getOkhttpObj
import com.airfreshener.telegram_sms.utils.NetworkUtils.getUrl
import com.airfreshener.telegram_sms.utils.OkHttpUtils.toRequestBody
import com.airfreshener.telegram_sms.utils.OtherUtils.addMessageList
import com.airfreshener.telegram_sms.utils.OtherUtils.getActiveCard
import com.airfreshener.telegram_sms.utils.OtherUtils.getDualSimCardDisplay
import com.airfreshener.telegram_sms.utils.OtherUtils.getMessageId
import com.airfreshener.telegram_sms.utils.OtherUtils.getSendPhoneNumber
import com.airfreshener.telegram_sms.utils.OtherUtils.getSubId
import com.airfreshener.telegram_sms.utils.OtherUtils.isPhoneNumber
import com.airfreshener.telegram_sms.utils.OtherUtils.isReadPhoneStatePermissionGranted
import com.airfreshener.telegram_sms.utils.PaperUtils.DEFAULT_BOOK
import com.airfreshener.telegram_sms.utils.PaperUtils.SYSTEM_BOOK
import com.airfreshener.telegram_sms.utils.PaperUtils.tryRead
import com.airfreshener.telegram_sms.utils.ResendUtils.addResendLoop
import com.airfreshener.telegram_sms.utils.ServiceUtils.startService
import com.airfreshener.telegram_sms.utils.ServiceUtils.stopAllService
import com.airfreshener.telegram_sms.utils.SmsUtils
import com.airfreshener.telegram_sms.utils.UssdUtils
import com.github.sumimakito.codeauxlib.CodeauxLibPortable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Receive action: " + intent.action)
        val prefsRepository = (context.applicationContext as TelegramSmsApp).prefsRepository
        val extras = intent.extras ?: return
        if (!prefsRepository.getInitialized()) {
            Log.i(TAG, "Uninitialized, SMS receiver is deactivated.")
            return
        }
        val isDefaultSmsApp = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        assert(intent.action != null)
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED" && isDefaultSmsApp) {
            // When it is the default application, it will receive two broadcasts.
            Log.i(TAG, "reject: android.provider.Telephony.SMS_RECEIVED.")
            return
        }

        val settings = prefsRepository.getSettings()
        val botToken = settings.botToken
        val chatId = settings.chatId
        val requestUri = getUrl(botToken, "sendMessage")
        var intentSlot = extras.getInt("slot", -1)
        val subId = extras.getInt("subscription", -1)
        if (getActiveCard(context) >= 2 && intentSlot == -1) {
            val manager = SubscriptionManager.from(context)
            if (context.isReadPhoneStatePermissionGranted()) {
                val info = manager.getActiveSubscriptionInfo(subId)
                intentSlot = info.simSlotIndex
            }
        }
        val slot = intentSlot
        val dualSim = getDualSimCardDisplay(context, intentSlot, prefsRepository.getDisplayDualSim())
        val pdus = (extras["pdus"] as Array<Any>?)!!
        val messages = arrayOfNulls<SmsMessage>(pdus.size)
        for (i in pdus.indices) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                messages[i] =
                    SmsMessage.createFromPdu(pdus[i] as ByteArray, extras.getString("format"))
            } else {
                messages[i] = SmsMessage.createFromPdu(pdus[i] as ByteArray)
            }
        }
        if (messages.isEmpty()) {
            LogUtils.writeLog(context, "Message length is equal to 0.")
            return
        }
        val messageBodyBuilder = StringBuilder()
        for (item in messages) {
            messageBodyBuilder.append(item!!.messageBody)
        }
        val messageBody = messageBodyBuilder.toString()
        val messageAddress = messages[0]!!.originatingAddress!!
        if (isDefaultSmsApp) {
            Log.i(TAG, "onReceive: Write to the system database.")
            Thread {
                val values = ContentValues()
                values.put(Telephony.Sms.ADDRESS, messageBody)
                values.put(Telephony.Sms.BODY, messageAddress)
                values.put(Telephony.Sms.SUBSCRIPTION_ID, subId.toString())
                values.put(Telephony.Sms.READ, "1")
                context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            }.start()
        }
        val trustedPhoneNumber = settings.trustedPhoneNumber
        var isTrustedPhone = false
        if (trustedPhoneNumber.isNotEmpty()) {
            isTrustedPhone = messageAddress.contains(trustedPhoneNumber)
        }
        val requestBody = RequestMessage()
        requestBody.chat_id = chatId
        var messageBodyHtml = messageBody
        val messageHead = """
            [$dualSim${context.getString(R.string.receive_sms_head)}]
            ${context.getString(R.string.from)}$messageAddress
            ${context.getString(R.string.content)}
            """.trimIndent()
        var rawRequestBodyText = messageHead + messageBody
        var isVerificationCode = false
        if (settings.isVerificationCode && !isTrustedPhone) {
            if (messageBody.length <= 140) {
                val verification = codeAuxLib.find(messageBody)
                if (verification != null) {
                    requestBody.parse_mode = "html"
                    messageBodyHtml = messageBody
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("&", "&amp;")
                        .replace(verification, "<code>$verification</code>")
                    isVerificationCode = true
                }
            } else {
                LogUtils.writeLog(
                    context,
                    "SMS exceeds 140 characters, no verification code is recognized."
                )
            }
        }
        requestBody.text = messageHead + messageBodyHtml
        if (isTrustedPhone) {
            LogUtils.writeLog(context, "SMS from trusted mobile phone detected")
            val messageCommand =
                messageBody.lowercase(Locale.getDefault()).replace("_", "").replace("-", "")
            val commandList =
                messageCommand.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (commandList.isNotEmpty()) {
                val messageList =
                    messageBody.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                when (commandList[0].trim { it <= ' ' }) {
                    "/restartservice" -> {
                        Thread {
                            stopAllService(context)
                            startService(context, settings.isBatteryMonitoring, settings.isChatCommand)
                        }.start()
                        rawRequestBodyText = """
                        ${context.getString(R.string.system_message_head)}
                        ${context.getString(R.string.restart_service)}
                        """.trimIndent()
                        requestBody.text = rawRequestBodyText
                    }

                    "/sendsms", "/sendsms1", "/sendsms2" -> {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.SEND_SMS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            Log.i(TAG, "No SMS permission.")
                        } else {
                            val msgSendTo = getSendPhoneNumber(messageList[1])
                            if (isPhoneNumber(msgSendTo) && messageList.size > 2) {
                                val msgSendContent = StringBuilder()
                                var i = 2
                                while (i < messageList.size) {
                                    if (i != 2) {
                                        msgSendContent.append("\n")
                                    }
                                    msgSendContent.append(messageList[i])
                                    ++i
                                }
                                var sendSlot = slot
                                if (getActiveCard(context) > 1) {
                                    when (commandList[0].trim { it <= ' ' }) {
                                        "/sendsms1" -> sendSlot = 0
                                        "/sendsms2" -> sendSlot = 1
                                    }
                                }
                                val finalSendSlot = sendSlot
                                val finalSendSubId = getSubId(context, finalSendSlot)
                                Thread {
                                    SmsUtils.sendSms(
                                        context,
                                        msgSendTo,
                                        msgSendContent.toString(),
                                        finalSendSlot,
                                        finalSendSubId
                                    )
                                }
                                    .start()
                                return
                            }
                        }
                    }

                    "/sendussd" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CALL_PHONE
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            if (messageList.size == 2) {
                                UssdUtils.sendUssd(context, messageList[1], subId)
                                return
                            }
                        }
                    } else {
                        Log.i(TAG, "send_ussd: No permission.")
                        return
                    }
                }
            }
        }
        if (!isVerificationCode && !isTrustedPhone) {
            val blackListArray = SYSTEM_BOOK.tryRead("block_keyword_list", ArrayList<String>())
            for (blackListItem in blackListArray) {
                if (blackListItem.isEmpty()) {
                    continue
                }
                if (messageBody.contains(blackListItem)) {
                    val simpleDateFormat =
                        SimpleDateFormat(context.getString(R.string.time_format), Locale.UK)
                    val writeMessage = """
                        ${requestBody.text}
                        ${context.getString(R.string.time)}${simpleDateFormat.format(Date(System.currentTimeMillis()))}
                        """.trimIndent()
                    val spamSmsList: ArrayList<String> =
                        DEFAULT_BOOK.tryRead("spam_sms_list", ArrayList())
                    if (spamSmsList.size >= 5) {
                        spamSmsList.removeAt(0)
                    }
                    spamSmsList.add(writeMessage)
                    DEFAULT_BOOK.write("spam_sms_list", spamSmsList)
                    Log.i(TAG, "Detected message contains blacklist keywords, add spam list")
                    return
                }
            }
        }
        val body = requestBody.toRequestBody()
        val okHttpClient = getOkhttpObj(settings.isDnsOverHttp)
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okHttpClient.newCall(request)
        val errorHead = "Send SMS forward failed: "
        val finalRawRequestBodyText = rawRequestBodyText
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                LogUtils.writeLog(context, errorHead + e.message)
                SmsUtils.sendFallbackSms(context, finalRawRequestBodyText, subId)
                addResendLoop(context, requestBody.text)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body ?: return
                val result = responseBody.string()
                if (response.code != 200) {
                    LogUtils.writeLog(context, errorHead + response.code + " " + result)
                    SmsUtils.sendFallbackSms(context, finalRawRequestBodyText, subId)
                    addResendLoop(context, requestBody.text)
                } else {
                    if (!isPhoneNumber(messageAddress)) {
                        LogUtils.writeLog(context, "[$messageAddress] Not a regular phone number.")
                        return
                    }
                    addMessageList(getMessageId(result), messageAddress, slot)
                }
            }
        })
    }

    companion object {
        private val codeAuxLib = CodeauxLibPortable()
        private const val TAG = "SmsReceiver"
    }
}