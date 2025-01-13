package com.qwe7002.telegram_sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.sumimakito.codeauxlib.CodeauxLibPortable
import com.google.gson.Gson
import com.qwe7002.telegram_sms.config.proxy
import com.qwe7002.telegram_sms.data_structure.RequestMessage
import com.qwe7002.telegram_sms.static_class.Logs
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.static_class.Other
import com.qwe7002.telegram_sms.static_class.Resend
import com.qwe7002.telegram_sms.static_class.SMS
import com.qwe7002.telegram_sms.static_class.Service
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.static_class.USSD
import com.qwe7002.telegram_sms.value.CcType
import com.qwe7002.telegram_sms.value.constValue
import io.paperdb.Paper
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects

class SMSReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        Paper.init(context)
        val TAG = "sms_receiver"
        Log.d(TAG, "Receive action: " + intent.action)
        val extras = intent.extras!!
        val sharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)
        if (!sharedPreferences.getBoolean("initialized", false)) {
            Log.i(TAG, "Uninitialized, SMS receiver is deactivated.")
            return
        }
        val isDefaultSmsApp = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED" && isDefaultSmsApp) {
            //When it is the default application, it will receive two broadcasts.
            Log.i(TAG, "reject: android.provider.Telephony.SMS_RECEIVED.")
            return
        }
        val botToken = sharedPreferences.getString("bot_token", "")
        val chatId = sharedPreferences.getString("chat_id", "")
        val messageThreadId = sharedPreferences.getString("message_thread_id", "")
        val requestUri = Network.getUrl(botToken.toString(), "sendMessage")

        var intentSlot = extras.getInt("slot", -1)
        val subId = extras.getInt("subscription", -1)
        if (Other.getActiveCard(context) >= 2 && intentSlot == -1) {
            val manager = SubscriptionManager.from(context)
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val info = manager.getActiveSubscriptionInfo(subId)
                intentSlot = info.simSlotIndex
            }
        }
        val slot = intentSlot
        val dualSim = Other.getDualSimCardDisplay(
            context,
            intentSlot,
            sharedPreferences.getBoolean("display_dual_sim_display_name", false)
        )

        val pdus = (extras["pdus"] as Array<*>?)!!
        val messages = arrayOfNulls<SmsMessage>(
            pdus.size
        )
        for (i in pdus.indices) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                messages[i] =
                    SmsMessage.createFromPdu(pdus[i] as ByteArray, extras.getString("format"))
            } else {
                messages[i] = SmsMessage.createFromPdu(pdus[i] as ByteArray)
            }
        }
        if (messages.isEmpty()) {
            Logs.writeLog(context, "Message length is equal to 0.")
            return
        }

        val messageBodyBuilder = StringBuilder()
        for (item in messages) {
            messageBodyBuilder.append(item!!.messageBody)
        }
        val textContent = messageBodyBuilder.toString()

        val messageAddress = messages[0]!!.originatingAddress!!
        val trustedPhoneNumber = sharedPreferences.getString("trusted_phone_number", null)
        var isTrustedPhone = false
        if (!trustedPhoneNumber.isNullOrEmpty()) {
            isTrustedPhone = messageAddress.contains(trustedPhoneNumber)
        }
        val requestBody = RequestMessage()
        requestBody.chatId = chatId.toString()
        requestBody.messageThreadId = messageThreadId.toString()

        var textContentHTML = textContent
        var isVerificationCode = false
        var verificationCode = ""
        if (sharedPreferences.getBoolean("verification_code", false) && !isTrustedPhone) {
            val verification = CodeauxLibPortable.find(context, textContent)
            if (verification != null) {
                requestBody.parseMode = "html"
                textContentHTML = textContent
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("&", "&amp;")
                    .replace(verification, "<code>$verification</code>")
                verificationCode = verification
                isVerificationCode = true
            }
        }
        if (isTrustedPhone) {
            Logs.writeLog(context, "SMS from trusted mobile phone detected")
            val messageCommand =
                textContent.lowercase(Locale.getDefault()).replace("_", "").replace("-", "")
            val commandList =
                messageCommand.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (commandList.isNotEmpty()) {
                val messageList =
                    textContent.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                when (commandList[0].trim { it <= ' ' }) {
                    "/restartservice" -> {
                        Thread {
                            Service.stopAllService(context)
                            Service.startService(
                                context,
                                sharedPreferences.getBoolean("battery_monitoring_switch", false),
                                sharedPreferences.getBoolean("chat_command", false)
                            )
                        }.start()
                        requestBody.text = Template.render(context,"TPL_system_message", mapOf("Message" to context.getString(R.string.restart_service)))
                    }

                    "/sendsms", "/sendsms1", "/sendsms2" -> {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.SEND_SMS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            Log.i(TAG, "No SMS permission.")
                            return
                        }
                        val messageInfo =
                            messageList[0].split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        if (messageInfo.size == 2) {
                            val msgSendTo = Other.getSendPhoneNumber(messageInfo[1])
                            if (Other.isPhoneNumber(msgSendTo)) {
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
                                if (Other.getActiveCard(context) > 1) {
                                    when (commandList[0].trim { it <= ' ' }) {
                                        "/sendsms1" -> sendSlot = 0
                                        "/sendsms2" -> sendSlot = 1
                                    }
                                }
                                Thread {
                                    SMS.sendSms(
                                        context,
                                        msgSendTo,
                                        msgSendContent.toString(),
                                        sendSlot,
                                        Other.getSubId(context, sendSlot)
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
                                USSD.sendUssd(context, messageList[1], subId)
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
            val blackListArray =
                Paper.book("system_config").read("block_keyword_list", ArrayList<String>())!!
            for (blackListItem in blackListArray) {
                if (blackListItem.isEmpty()) {
                    continue
                }

                if (textContent.contains(blackListItem)) {
                    val simpleDateFormat =
                        SimpleDateFormat(context.getString(R.string.time_format), Locale.UK)
                    val writeMessage =
                        requestBody.text + "\n" + context.getString(R.string.time) + simpleDateFormat.format(
                            Date(System.currentTimeMillis())
                        )
                    Paper.init(context)
                    val spamSmsList = Paper.book().read("spam_sms_list", ArrayList<String>())!!
                    if (spamSmsList.size >= 5) {
                        spamSmsList.removeAt(0)
                    }
                    spamSmsList.add(writeMessage)
                    Paper.book().write("spam_sms_list", spamSmsList)
                    Log.i(TAG, "Detected message contains blacklist keywords, add spam list")
                    return
                }
            }
        }

        val values =
            mapOf("SIM" to dualSim, "From" to messageAddress, "Content" to textContentHTML)
        val rawValues =
            mapOf("SIM" to dualSim, "From" to messageAddress, "Content" to textContent)
        requestBody.text = Template.render(context, "TPL_received_sms", values)
        val requestBodyText = Template.render(context, "TPL_received_sms", rawValues)
        CcSendJob.startJob(
            context,
            CcType.SMS,
            context.getString(R.string.receive_sms_title),
            requestBodyText,
            verificationCode
        )
        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(constValue.JSON)
        val okhttpObj = Network.getOkhttpObj(
            sharedPreferences.getBoolean("doh_switch", true),
            Paper.book("system_config").read("proxy_config", proxy())
        )
        val request: Request = Request.Builder().url(requestUri).method("POST", body).build()
        val call = okhttpObj.newCall(request)
        val errorHead = "Send SMS forward failed:"
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Logs.writeLog(context, errorHead + e.message)
                SMS.fallbackSMS(context, requestBodyText, subId)
                Resend.addResendLoop(context, requestBody.text)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val result = Objects.requireNonNull(response.body).string()
                if (response.code != 200) {
                    Logs.writeLog(context, errorHead + response.code + " " + result)
                    SMS.fallbackSMS(context, requestBodyText, subId)
                    Resend.addResendLoop(context, requestBody.text)
                } else {
                    if (!Other.isPhoneNumber(messageAddress)) {
                        Logs.writeLog(context, "[$messageAddress] Not a regular phone number.")
                        return
                    }
                    Other.addMessageList(Other.getMessageId(result), messageAddress, slot)
                }
            }
        })
    }

}


