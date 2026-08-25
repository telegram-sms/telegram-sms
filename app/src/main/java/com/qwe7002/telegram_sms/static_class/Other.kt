package com.qwe7002.telegram_sms.static_class

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.qwe7002.telegram_sms.MMKV.CHAT_INFO_ID
import com.qwe7002.telegram_sms.R
import com.qwe7002.telegram_sms.data_structure.SMSRequestInfo
import com.qwe7002.telegram_sms.value.TAG
import com.tencent.mmkv.MMKV
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Other {
    private const val logTag = "${TAG}.Other"

    @JvmStatic
    fun formatTimestamp(timestampMillis: Long): String {
        // Locale.US — keep digits ASCII regardless of device locale (e.g. fa-IR / ar-* would otherwise
        // emit non-Western numerals into the Telegram message).
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .format(Date(timestampMillis))
    }

    private val NINE_KEY_MAP = mapOf(
        'A' to 2, 'B' to 2, 'C' to 2,
        'D' to 3, 'E' to 3, 'F' to 3,
        'G' to 4, 'H' to 4, 'I' to 4,
        'J' to 5, 'K' to 5, 'L' to 5,
        'M' to 6, 'N' to 6, 'O' to 6,
        'P' to 7, 'Q' to 7, 'R' to 7, 'S' to 7,
        'T' to 8, 'U' to 8, 'V' to 8,
        'W' to 9, 'X' to 9, 'Y' to 9, 'Z' to 9
    )

    fun getNineKeyMapConvert(input: String): String {
        // Locale.ROOT — Turkish locale would otherwise uppercase 'i' to 'İ' (U+0130),
        // which isn't in NINE_KEY_MAP and would pass through unmapped.
        val result = StringBuilder(input.length)
        val phoneNumberCharArray = input.uppercase(Locale.ROOT).toCharArray()
        for (c in phoneNumberCharArray) {
            result.append(NINE_KEY_MAP[c] ?: c)
        }
        return result.toString()
    }

    fun parseStringToLong(value: String?): Long {
        if (value.isNullOrBlank()) {
            return 0L
        }
        return try {
            value.toLong()
        } catch (_: NumberFormatException) {
            0L
        }
    }

    /**
     * Converts the stored `message_thread_id` preference into the Integer the Bot API
     * expects. The preference is a free-text field, so it can hold anything; blank (no
     * topic configured) and unparsable values alike become null, which omits the field
     * from the payload instead of sending something Telegram would have to reject.
     *
     * Distinct from [parseStringToLong], which folds those cases into 0 — and 0 would be
     * a *value* here, not an absence.
     */
    @JvmStatic
    fun parseMessageThreadId(value: String?): Long? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.toLongOrNull()

    fun getSendPhoneNumber(phoneNumber: String): String {
        var phone = phoneNumber
        phone = getNineKeyMapConvert(phone)
        val result = StringBuilder()
        for (element in phone) {
            if (element == '+' || Character.isDigit(element)) {
                result.append(element)
            }
        }
        return result.toString()
    }


    fun isPhoneNumber(str: String): Boolean {
        if (str.isEmpty()) return false
        var hasDigit = false
        for ((i, c) in str.withIndex()) {
            when {
                c == '+' -> if (i != 0) return false
                Character.isDigit(c) -> hasDigit = true
                else -> return false
            }
        }
        return hasDigit
    }


    @JvmStatic
    fun getMessageId(result: String): Long {
        return try {
            val root = JsonParser.parseString(result).asJsonObject
            val resultObj = root["result"]?.asJsonObject ?: run {
                Log.w(logTag, "getMessageId: no 'result' object in $result")
                return 0L
            }
            resultObj["message_id"]?.asLong ?: 0L
        } catch (e: Exception) {
            // Telegram error responses (ok:false), HTML/proxy bodies, or other
            // non-Telegram payloads land here. Returning 0 keeps callers alive;
            // they already treat 0 as "no message id" via MMKV defaults.
            Log.w(logTag, "getMessageId: failed to parse response: $result", e)
            0L
        }
    }

    @JvmStatic
    fun getNotificationObj(context: Context, notificationName: String): Notification {
        val notification: Notification.Builder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                checkNotNull(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            var channel = manager.getNotificationChannel(notificationName)
            if (channel == null) {
                channel = NotificationChannel(
                    notificationName, notificationName,
                    NotificationManager.IMPORTANCE_MIN
                )
                manager.createNotificationChannel(channel)
            }
            notification = Notification.Builder(context, notificationName)
        } else {
            @Suppress("DEPRECATION")
            notification = Notification.Builder(context).setPriority(Notification.PRIORITY_MIN)
        }
        notification.setAutoCancel(false)
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .setTicker(context.getString(R.string.app_name))
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(
                String.format(
                    "%s%s",
                    notificationName,
                    context.getString(R.string.service_is_running)
                )
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notification.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return notification.build()
    }

    @JvmStatic
    fun getSubId(context: Context, slot: Int): Int {
        val activeCard = getActiveCard(context)
        if (activeCard >= 2) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return -1
            }
            val subscriptionManager =
                checkNotNull(context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager)
            val subscriptionInfo =
                subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)
            return subscriptionInfo?.subscriptionId ?: -1
        }
        return -1
    }

    @JvmStatic
    fun getActiveCard(context: Context): Int {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(logTag, "No permission.")
            return -1
        }
        val subscriptionManager =
            checkNotNull(context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager)
        return subscriptionManager.activeSubscriptionInfoCount
    }


    fun addMessageList(messageId: Long, phone: String?, slot: Int) {
        val item = SMSRequestInfo()
        item.phone = phone!!
        item.card = slot
        val gson = Gson()
        val itemString = gson.toJson(item)
        val chatInfoMMKV = MMKV.mmkvWithID(CHAT_INFO_ID)
        chatInfoMMKV.putString(messageId.toString(), itemString)
        Log.d(logTag, "addMessageList: $messageId")
    }
}
