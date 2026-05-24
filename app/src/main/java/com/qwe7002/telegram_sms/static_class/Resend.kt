package com.qwe7002.telegram_sms.static_class

import android.content.Context
import com.qwe7002.telegram_sms.R
import com.qwe7002.telegram_sms.ReSendJob
import com.qwe7002.telegram_sms.MMKV.RESEND_ID
import com.tencent.mmkv.MMKV
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Resend {
    // All read-modify-write access to "resend_list" must hold this lock. MMKV offers no atomic
    // collection update, so concurrent writers (receivers via addResendLoop + ReSendJob removing
    // sent messages) would otherwise clobber each other and silently drop queued messages.
    private val resendListLock = Any()

    @JvmStatic
    fun addResendLoop(context: Context, msg: String) {
        if (msg.isEmpty()) {
            return
        }
        val simpleDateFormat = SimpleDateFormat(context.getString(R.string.time_format), Locale.UK)
        val message = msg + "\n" + context.getString(R.string.time) +
                simpleDateFormat.format(Date(System.currentTimeMillis()))
        synchronized(resendListLock) {
            val mmkv = MMKV.mmkvWithID(RESEND_ID)
            val resendList = mmkv.decodeStringSet("resend_list", mutableSetOf()) ?: mutableSetOf()
            resendList.add(message)
            mmkv.encode("resend_list", resendList)
        }
        ReSendJob.startJob(context)
    }

    /** Atomically reads the current resend queue snapshot. */
    @JvmStatic
    fun getResendList(): List<String> = synchronized(resendListLock) {
        val mmkv = MMKV.mmkvWithID(RESEND_ID)
        mmkv.decodeStringSet("resend_list", setOf())?.toList() ?: emptyList()
    }

    /** Atomically removes a single message from the resend queue without clobbering concurrent adds. */
    @JvmStatic
    fun removeFromResendList(message: String) = synchronized(resendListLock) {
        val mmkv = MMKV.mmkvWithID(RESEND_ID)
        val resendList = mmkv.decodeStringSet("resend_list", mutableSetOf())?.toMutableSet()
            ?: return
        if (resendList.remove(message)) {
            mmkv.encode("resend_list", resendList)
        }
    }
}
