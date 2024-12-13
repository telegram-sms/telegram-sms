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
import com.google.gson.JsonParser
import com.qwe7002.telegram_sms.R
import com.qwe7002.telegram_sms.data_structure.SMSRequestInfo
import io.paperdb.Paper
import java.util.Locale

object Other {
    fun getNineKeyMapConvert(input: String): String {
        val nineKeyMap = mapOf(
            'A' to 2, 'B' to 2, 'C' to 2,
            'D' to 3, 'E' to 3, 'F' to 3,
            'G' to 4, 'H' to 4, 'I' to 4,
            'J' to 5, 'K' to 5, 'L' to 5,
            'M' to 6, 'N' to 6, 'O' to 6,
            'P' to 7, 'Q' to 7, 'R' to 7, 'S' to 7,
            'T' to 8, 'U' to 8, 'V' to 8,
            'W' to 9, 'X' to 9, 'Y' to 9, 'Z' to 9
        )
        val result = StringBuilder()
        val phoneNumberCharArray = input.uppercase(Locale.getDefault()).toCharArray()
        for (c in phoneNumberCharArray) {
            result.append(nineKeyMap[c] ?: c)
        }
        return result.toString()
    }

    fun parseStringToLong(content: String): Long {
        var result: Long = 0
        try {
            result = content.toLong()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        return result
    }

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

    fun getDualSimCardDisplay(context: Context, slot: Int, showName: Boolean): String {
        var dualSim = ""
        if (slot == -1) {
            return dualSim
        }
        if (getActiveCard(context) >= 2) {
            var result = ""
            if (showName) {
                result = "(" + getSimDisplayName(context, slot) + ")"
            }
            dualSim = "SIM" + (slot + 1) + result
        }
        return dualSim
    }

    fun isPhoneNumber(str: String): Boolean {
        var i = str.length
        while (--i >= 0) {
            val c = str[i]
            if (c == '+') {
                Log.d("is_phone_number", "is_phone_number: found +.")
                continue
            }
            if (!Character.isDigit(c)) {
                return false
            }
        }
        return true
    }


    @JvmStatic
    fun getMessageId(result: String): Long {
        return JsonParser.parseString(result).asJsonObject["result"].asJsonObject["message_id"].asLong
    }

    @JvmStatic
    fun getNotificationObj(context: Context, notificationName: String): Notification {
        val notification: Notification.Builder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationName, notificationName,
                NotificationManager.IMPORTANCE_MIN
            )
            val manager =
                checkNotNull(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            manager.createNotificationChannel(channel)
            notification = Notification.Builder(context, notificationName)
        } else { //Notification generation method after O
            @Suppress("DEPRECATION")
            notification = Notification.Builder(context).setPriority(Notification.PRIORITY_MIN)
        }
        notification.setAutoCancel(false)
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .setTicker(context.getString(R.string.app_name))
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(notificationName + context.getString(R.string.service_is_running))
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
            return subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot).subscriptionId
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
            Log.d("get_active_card", "No permission.")
            return -1
        }
        val subscriptionManager =
            checkNotNull(context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager)
        return subscriptionManager.activeSubscriptionInfoCount
    }


    fun getSimDisplayName(context: Context, slot: Int): String {
        val TAG = "get_sim_display_name"
        var result = "Unknown"
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "No permission.")
            return result
        }
        val subscriptionManager =
            checkNotNull(context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager)
        var info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)
        if (info == null) {
            Log.d(TAG, "The active card is in the second card slot.")
            if (getActiveCard(context) == 1 && slot == 0) {
                info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(1)
            }
            if (info == null) {
                return result
            }
            return result
        }
        result = info.displayName.toString()
        if (info.displayName.toString().contains("CARD") || info.displayName.toString()
                .contains("SUB")
        ) {
            result = info.carrierName.toString()
        }
        return result
    }


    fun addMessageList(messageId: Long, phone: String?, slot: Int) {
        val item = SMSRequestInfo()
        item.phone = phone!!
        item.card = slot
        Paper.book().write(messageId.toString(), item)
        Log.d("add_message_list", "add_message_list: $messageId")
    }
}
