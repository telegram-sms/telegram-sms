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
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.Other
import com.qwe7002.telegram_sms.static_class.Phone
import com.qwe7002.telegram_sms.static_class.SMS
import com.qwe7002.telegram_sms.static_class.TelegramApi
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.static_class.USSD
import com.qwe7002.telegram_sms.value.CcType
import com.qwe7002.telegram_sms.value.TAG
import com.tencent.mmkv.MMKV
import java.util.Locale

class SMSReceiver : BroadcastReceiver() {
    private val logTag = "${TAG}.SMSReceiver"

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(logTag, "Receive action: " + intent.action)
        val extras = intent.extras!!
        val preferences = MMKV.defaultMMKV()
        if (!preferences.getBoolean("initialized", false)) {
            Log.i(logTag, "Uninitialized, SMS receiver is deactivated.")
            return
        }
        val isDefaultSmsApp = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED" && isDefaultSmsApp) {
            //When it is the default application, it will receive two broadcasts.
            Log.i(logTag, "reject: android.provider.Telephony.SMS_RECEIVED.")
            return
        }

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
        val dualSim = try {
            Phone.getSimDisplayName(context, slot)
        } catch (e: SecurityException) {
            Log.e(logTag, "Failed to get SIM display name due to missing permission: ${e.message}",e)
            ""
        }

        val pdus = (extras["pdus"] as Array<*>?)!!
        val messages = arrayOfNulls<SmsMessage>(
            pdus.size
        )
        for (i in pdus.indices) {
            messages[i] =
                SmsMessage.createFromPdu(pdus[i] as ByteArray, extras.getString("format"))
        }
        if (messages.isEmpty()) {
            Log.w(logTag, "Message length is equal to 0.")
            return
        }

        val messageBodyBuilder = StringBuilder()
        for (item in messages) {
            messageBodyBuilder.append(item!!.messageBody)
        }
        val textContent = messageBodyBuilder.toString()

        val messageAddress = messages[0]!!.originatingAddress!!
        // Some carriers ship SMS with a 0/garbage SMSC timestamp; in that case fall back to the
        // local receive time so the {{Time}} placeholder is never empty.
        val smsTimestamp = messages[0]!!.timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val messageTime = Other.formatTimestamp(smsTimestamp)
        val trustedPhoneNumber = preferences.getString("trusted_phone_number", null)
        var isTrustedPhone = false
        if (!trustedPhoneNumber.isNullOrEmpty()) {
            isTrustedPhone = messageAddress.contains(trustedPhoneNumber)
        }
        val requestBody = RequestMessage()

        var textContentHTML = textContent
        var isVerificationCode = false
        var verificationCode = ""
        if (preferences.getBoolean("verification_code", false) && !isTrustedPhone) {
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
            Log.i(logTag, "SMS from trusted mobile phone detected")
            val messageCommand =
                textContent.lowercase(Locale.getDefault()).replace("_", "").replace("-", "")
            val commandList = messageCommand.split("\n").filter { it.isNotEmpty() }.toTypedArray()
            if (commandList.isNotEmpty()) {
                val messageList = textContent.split("\n").filter { it.isNotEmpty() }.toTypedArray()
                when (commandList[0].trim()) {
                    "/sendsms" -> {
                        val messageInfo =
                            messageList[0].split(" ").filter { it.isNotEmpty() }.toTypedArray()

                        // Determine which element contains the phone number
                        // Format can be: /sendsms phone or /sendsms 1 phone or /sendsms1 phone
                        val baseCommand = commandList[0].trim()
                        var phoneIndex = 1
                        var sendSlot = slot

                        if (Other.getActiveCard(context) > 1) {
                            when (baseCommand) {
                                "/sendsms" -> {
                                    // Check if SIM card number is specified
                                    if (messageInfo.size >= 3) {
                                        when (messageInfo[1]) {
                                            "1" -> {
                                                sendSlot = 0
                                                phoneIndex = 2
                                            }
                                            "2" -> {
                                                sendSlot = 1
                                                phoneIndex = 2
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (messageInfo.size > phoneIndex) {
                            val msgSendTo = Other.getSendPhoneNumber(messageInfo[phoneIndex])
                            if (Other.isPhoneNumber(msgSendTo)) {
                                val contentStartLine = if (phoneIndex == 2) 2 else 1
                                val msgSendContent = messageList.drop(contentStartLine + 1).joinToString("\n")
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
                            val messageInfo =
                                messageList[0].split(" ").filter { it.isNotEmpty() }.toTypedArray()

                            val baseCommand = commandList[0].trim()
                            var codeIndex = 1
                            var ussdSlot = slot

                            if (Other.getActiveCard(context) > 1) {
                                when (baseCommand) {
                                    "/sendussd" -> {
                                        // Check if SIM card number is specified
                                        if (messageInfo.size >= 3) {
                                            when (messageInfo[1]) {
                                                "1" -> {
                                                    ussdSlot = 0
                                                    codeIndex = 2
                                                }
                                                "2" -> {
                                                    ussdSlot = 1
                                                    codeIndex = 2
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (messageInfo.size > codeIndex) {
                                val ussdCode = messageInfo[codeIndex]
                                USSD.sendUssd(context, ussdCode, Other.getSubId(context, ussdSlot))
                                return
                            }
                        } else {
                            Log.i(logTag, "send_ussd: No permission.")
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
                    Log.i(logTag, "Detected message contains blacklist keywords")
                    requestBody.disableNotification = true
                }
            }
        }

        val values =
            mapOf("SIM" to dualSim, "From" to messageAddress, "Content" to textContentHTML, "Time" to messageTime)
        val rawValues =
            mapOf("SIM" to dualSim, "From" to messageAddress, "Content" to textContent, "Time" to messageTime)
        requestBody.text = Template.render(context, "TPL_received_sms", values)
        val requestBodyText = Template.render(context, "TPL_received_sms", rawValues)
        CcSendJob.startJob(
            context,
            CcType.SMS,
            context.getString(R.string.receive_sms_title),
            requestBodyText,
            verificationCode
        )

        TelegramApi.sendMessage(
            context = context,
            requestBody = requestBody,
            fallbackSubId = subId
        ) { result ->
            if (Other.isPhoneNumber(messageAddress)) {
                Other.addMessageList(Other.getMessageId(result), messageAddress, slot)
            } else {
                Log.w(logTag, "[$messageAddress] Not a regular phone number.")
            }
        }
    }

}


