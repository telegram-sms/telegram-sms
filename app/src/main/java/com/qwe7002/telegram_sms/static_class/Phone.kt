package com.qwe7002.telegram_sms.static_class

import android.Manifest
import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.qwe7002.telegram_sms.static_class.Other.getActiveCard

object Phone {

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    @JvmStatic
    fun getSimDisplayName(context: Context, slot: Int): String {
        val TAG = "get_sim_display_name"
        val telephonyManager =
            context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val subscriptionManager =
            checkNotNull(context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager)
        var info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)
        if (info == null) {
            Log.d(TAG, "The active card is in the second card slot.")
            if (getActiveCard(context) == 1 && slot == 0) {
                info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(1)
            }
            if (info == null) {
                Log.d(TAG, "The active card is not found.")
                return "Unknown"
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val subId1 = info.subscriptionId
            val tm = telephonyManager.createForSubscriptionId(subId1)
            if (tm.simOperatorName == info.displayName) {
                tm.simOperatorName + " (" + info.number + ")"
            } else {
                //todo alias
                info.displayName.toString() + " (" + info.number + ")"
            }
        } else {
            info.carrierName.toString()
        }
    }

}

