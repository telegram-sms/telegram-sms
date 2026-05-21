package com.qwe7002.telegram_sms.static_class

import android.Manifest
import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import com.qwe7002.telegram_sms.static_class.Other.getActiveCard
import com.qwe7002.telegram_sms.value.TAG
import com.tencent.mmkv.MMKV
import java.util.Locale

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
            // READ_PHONE_NUMBERS is a runtime-revocable dangerous permission and
            // can disappear after an upgrade (issue #82). Degrade to name-only
            // instead of bubbling the SecurityException up to the receiver,
            // which would otherwise collapse {{SIM}} to an empty string.
            val phoneNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    subscriptionManager.getPhoneNumber(subId1)
                } catch (e: SecurityException) {
                    Log.w(logTag, "getPhoneNumber denied; rendering SIM name only: ${e.message}")
                    null
                }
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

    /**
     * Strict check used for *authorization* (e.g. deciding whether an incoming SMS comes from the
     * trusted controller). Both numbers are normalized to E.164 using the device's current country
     * and compared for exact equality. This tolerates formatting differences (spacing, dashes,
     * national vs. international prefix) while rejecting the spoofing vectors a substring match or
     * `PhoneNumberUtils.compare()` loose (last-7-digit) match would let through.
     *
     * If either value cannot be normalized to E.164 (e.g. an alphanumeric sender ID, or a number
     * for which no country can be inferred) it falls back to a strict, case-sensitive string
     * equality — never a partial/loose match.
     */
    @JvmStatic
    fun isSameTrustedNumber(context: Context, incoming: String, trusted: String): Boolean {
        val region = deviceRegion(context)
        val normalizedIncoming = region?.let { PhoneNumberUtils.formatNumberToE164(incoming, it) }
        val normalizedTrusted = region?.let { PhoneNumberUtils.formatNumberToE164(trusted, it) }
        return if (normalizedIncoming != null && normalizedTrusted != null) {
            normalizedIncoming == normalizedTrusted
        } else {
            incoming == trusted
        }
    }

    private fun deviceRegion(context: Context): String? {
        return try {
            val tm = context.getSystemService(TELEPHONY_SERVICE) as? TelephonyManager
            (tm?.simCountryIso?.takeIf { it.isNotBlank() } ?: tm?.networkCountryIso)
                ?.takeIf { it.isNotBlank() }
                ?.uppercase(Locale.ROOT)
        } catch (e: Exception) {
            Log.w(logTag, "Failed to resolve device region for phone normalization: ${e.message}", e)
            null
        }
    }

}

