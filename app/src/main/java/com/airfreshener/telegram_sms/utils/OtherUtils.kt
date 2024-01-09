package com.airfreshener.telegram_sms.utils

import android.Manifest.permission.READ_PHONE_STATE
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.airfreshener.telegram_sms.R
import com.airfreshener.telegram_sms.model.SmsRequestInfo
import com.airfreshener.telegram_sms.utils.PaperUtils.getDefaultBook
import com.airfreshener.telegram_sms.utils.ServiceUtils.notificationManager
import com.airfreshener.telegram_sms.utils.ServiceUtils.subscriptionManager
import com.google.gson.JsonParser
import java.util.Locale

object OtherUtils {
    fun getNineKeyMapConvert(input: String): String {
        val nineKeyMap: Map<Char, Int> = mapOf(
            'A' to 2,
            'B' to 2,
            'C' to 2,
            'D' to 3,
            'E' to 3,
            'F' to 3,
            'G' to 4,
            'H' to 4,
            'I' to 4,
            'J' to 5,
            'K' to 5,
            'L' to 5,
            'M' to 6,
            'N' to 6,
            'O' to 6,
            'P' to 7,
            'Q' to 7,
            'R' to 7,
            'S' to 7,
            'T' to 8,
            'U' to 8,
            'V' to 8,
            'W' to 9,
            'X' to 9,
            'Y' to 9,
            'Z' to 9,
        )
        val stringBuilder = StringBuilder()
        val phoneNumberCharArray = input.uppercase(Locale.getDefault()).toCharArray()
        for (c in phoneNumberCharArray) {
            if (Character.isUpperCase(c)) {
                stringBuilder.append(nineKeyMap[c])
            } else {
                stringBuilder.append(c)
            }
        }
        return stringBuilder.toString()
    }

    fun parseStringToLong(content: String): Long = try {
        content.toLong()
    } catch (e: NumberFormatException) {
        e.printStackTrace()
        0
    }

    fun getSendPhoneNumber(phoneNumber: String): String {
        val result = StringBuilder()
        for (element in getNineKeyMapConvert(phoneNumber)) {
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
            dualSim = "SIM" + (slot + 1) + result + " "
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

    fun getMessageId(result: String?): Long {
        if (result == null) return -1
        val resultObj = JsonParser.parseString(result).asJsonObject["result"].asJsonObject
        return resultObj["message_id"].asLong
    }

    fun getNotificationObj(context: Context, notificationName: String): Notification {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationName, notificationName,
                NotificationManager.IMPORTANCE_MIN
            )
            context.notificationManager.createNotificationChannel(channel)
            Notification.Builder(context, notificationName)
        } else { // Notification generation method after O
            Notification.Builder(context).setPriority(Notification.PRIORITY_MIN)
        }
        notification.setAutoCancel(false)
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .setTicker(context.getString(R.string.app_name))
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(notificationName + context.getString(R.string.service_is_running))
        return notification.build()
    }

    fun getSubId(context: Context, slot: Int): Int {
        if (ActivityCompat.checkSelfPermission(context, READ_PHONE_STATE) != PERMISSION_GRANTED) {
            Log.d("getActiveCard", "No permission.")
            return -1
        }
        val activeCard = getActiveCard(context)
        if (activeCard > slot) {
            return context.subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)?.subscriptionId ?: -1
        }
        return -1
    }

    fun getActiveCard(context: Context): Int {
        return if (ActivityCompat.checkSelfPermission(context, READ_PHONE_STATE) != PERMISSION_GRANTED) {
            Log.d("getActiveCard", "No permission.")
            -1
        } else {
            context.subscriptionManager.activeSubscriptionInfoCount
        }
    }

    fun Context.isReadPhoneStatePermissionGranted() =
        ContextCompat.checkSelfPermission(this, READ_PHONE_STATE) == PERMISSION_GRANTED

    fun Activity.requestReadPhoneStatePermission(requestCode: Int) =
        ActivityCompat.requestPermissions(this, arrayOf(READ_PHONE_STATE), requestCode)

    fun getSimDisplayName(context: Context, slot: Int): String {
        val TAG = "get_sim_display_name"
        var result = "Unknown"
        if (ActivityCompat.checkSelfPermission(context, READ_PHONE_STATE) != PERMISSION_GRANTED) {
            Log.d(TAG, "No permission.")
            return result
        }
        val subscriptionManager = context.subscriptionManager
        var info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)
        if (info == null) {
            Log.d(TAG, "The active card is in the second card slot.")
            if (getActiveCard(context) == 1 && slot == 0) {
                info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(1)
            }
            return if (info == null) {
                result
            } else result
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
        val item = SmsRequestInfo()
        item.phone = phone
        item.card = slot
        getDefaultBook().write(messageId.toString(), item)
        Log.d("add_message_list", "add_message_list: $messageId")
    }

}
