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
        val nineKeyMap: HashMap<Char, Int> = object : HashMap<Char, Int>() {
            init {
                put('A', 2)
                put('B', 2)
                put('C', 2)
                put('D', 3)
                put('E', 3)
                put('F', 3)
                put('G', 4)
                put('H', 4)
                put('I', 4)
                put('J', 5)
                put('K', 5)
                put('L', 5)
                put('M', 6)
                put('N', 6)
                put('O', 6)
                put('P', 7)
                put('Q', 7)
                put('R', 7)
                put('S', 7)
                put('T', 8)
                put('U', 8)
                put('V', 8)
                put('W', 9)
                put('X', 9)
                put('Y', 9)
                put('Z', 9)
            }
        }
        val result = StringBuilder()
        val phoneNumberCharArray = input.uppercase(Locale.getDefault()).toCharArray()
        for (c in phoneNumberCharArray) {
            if (Character.isUpperCase(c)) {
                result.append(nineKeyMap[c])
            } else {
                result.append(c)
            }
        }
        return result.toString()
    }

    @JvmStatic
    fun parseStringToLong(content: String): Long {
        var result: Long = 0
        try {
            result = content.toLong()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }
        return result
    }

    @JvmStatic
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

    @JvmStatic
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
            dualSim = "SIM" + (slot + 1) + result + " "
        }
        return dualSim
    }

    @JvmStatic
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
        val result_obj = JsonParser.parseString(result).asJsonObject["result"].asJsonObject
        return result_obj["message_id"].asLong
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
