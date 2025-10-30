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
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.Logs
import com.qwe7002.telegram_sms.static_class.Network
import com.qwe7002.telegram_sms.static_class.Other
import com.qwe7002.telegram_sms.static_class.Phone
import com.qwe7002.telegram_sms.static_class.Resend
import com.qwe7002.telegram_sms.static_class.SMS
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.static_class.USSD
import com.qwe7002.telegram_sms.value.CcType
import com.qwe7002.telegram_sms.value.Const
import com.tencent.mmkv.MMKV
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.Objects

class SMSReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        MMKV.initialize(context)
        val TAG = "sms_receiver"
        Log.d(TAG, "Receive action: " + intent.action)
        val extras = intent.extras!!
        val preferences = MMKV.defaultMMKV()
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
        val requestUri = Network.getUrl(context, botToken.toString(), "sendMessage")

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
        /* val dualSim = Other.getDualSimCardDisplay(
             context,
             intentSlot,
         )*/
        val dualSim = Phone.getSimDisplayName(context, slot)

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
            val commandList = messageCommand.split("\n").filter { it.isNotEmpty() }.toTypedArray()
            if (commandList.isNotEmpty()) {
                val messageList = textContent.split("\n").filter { it.isNotEmpty() }.toTypedArray()
                when (commandList[0].trim()) {
                    "/sendsms", "/sendsms1", "/sendsms2" -> {
                        val messageInfo =
                            messageList[0].split(" ").filter { it.isNotEmpty() }.toTypedArray()
                        if (messageInfo.size == 2) {
                            val msgSendTo = Other.getSendPhoneNumber(messageInfo[1])
                            if (Other.isPhoneNumber(msgSendTo)) {
                                val msgSendContent = messageList.drop(2).joinToString("\n")
                                var sendSlot = slot
                                if (Other.getActiveCard(context) > 1) {
                                    sendSlot = when (commandList[0].trim()) {
                                        "/sendsms1" -> 0
                                        "/sendsms2" -> 1
                                        else -> sendSlot
                                    }
                                }
                                Thread {
                                    SMS.sendSms(
                                        context,
                                        msgSendTo,
                                        msgSendContent,
                                        sendSlot,
                                        Other.getSubId(context, sendSlot)
                                    )
                                }.start()
                                return
                            }
                        }
                    }

                    "/sendussd" -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CALL_PHONE
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            if (messageList.size == 2) {
                                USSD.sendUssd(context, messageList[1], subId)
                                return
                            }
                        } else {
                            Log.i(TAG, "send_ussd: No permission.")
                            return
                        }
                    }
                }
            }
        }


        if (!isVerificationCode && !isTrustedPhone) {
            val blackListArray =
                preferences.getStringSet("block_keyword_list", setOf())?.toMutableList()
                    ?: mutableListOf()
            for (blackListItem in blackListArray) {
                if (textContent.contains(blackListItem)) {
                    Log.i(TAG, "Detected message contains blacklist keywords")
                    requestBody.disableNotification = true
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
        val body: RequestBody = Gson().toJson(requestBody).toRequestBody(Const.JSON)
        val okhttpObj = Network.getOkhttpObj(
            sharedPreferences.getBoolean("doh_switch", true)
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


