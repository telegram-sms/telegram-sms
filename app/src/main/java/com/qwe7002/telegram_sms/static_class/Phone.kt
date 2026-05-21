package com.qwe7002.telegram_sms.static_class

import android.Manifest
import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.qwe7002.telegram_sms.static_class.Other.getActiveCard
import com.qwe7002.telegram_sms.value.TAG
import com.tencent.mmkv.MMKV

object Phone {
    private const val logTag = "${TAG}.Phone"
    // Lazy so that pure helpers (e.g. formatSimDisplayName) are unit-testable
    // without requiring MMKV native init / an Android context at object load.
    val preferences by lazy { MMKV.defaultMMKV() }
    @RequiresPermission(allOf = [Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS])
    @JvmStatic
    fun getSimDisplayName(context: Context, slot: Int): String {
        val telephonyManager =
            context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val subscriptionManager =
            checkNotNull(context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager)
        var info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(slot)
        if (info == null) {
            Log.d(logTag, "The active card is in the second card slot.")
            if (getActiveCard(context) == 1 && slot == 0) {
                info = subscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(1)
            }
            if (info == null) {
                Log.d(logTag, "The active card is not found.")
                return ""
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val subId1 = info.subscriptionId
            val tm = telephonyManager.createForSubscriptionId(subId1)
            val phoneNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                subscriptionManager.getPhoneNumber(subId1)
            } else {
                @Suppress("DEPRECATION")
                info.number
            }
            formatSimDisplayName(
                operatorName = tm.simOperatorName,
                displayName = info.displayName?.toString(),
                phoneNumber = phoneNumber,
                hidePhoneNumber = preferences.getBoolean("hide_phone_number", false)
            )
        } else {
            info.carrierName.toString()
        }
    }

    @JvmStatic
    fun formatSimDisplayName(
        operatorName: String?,
        displayName: String?,
        phoneNumber: String?,
        hidePhoneNumber: Boolean
    ): String {
        val name = when {
            !displayName.isNullOrBlank() -> displayName
            !operatorName.isNullOrBlank() -> operatorName
            else -> ""
        }
        return if (hidePhoneNumber || phoneNumber.isNullOrBlank()) {
            "$name "
        } else {
            "$name ($phoneNumber)"
        }
    }

}

