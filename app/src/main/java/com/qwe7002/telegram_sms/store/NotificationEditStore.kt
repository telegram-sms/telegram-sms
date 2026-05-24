package com.qwe7002.telegram_sms.store

import android.util.Log
import com.qwe7002.telegram_sms.MMKV.NOTIFICATION_EDIT_ID
import com.qwe7002.telegram_sms.value.TAG
import com.tencent.mmkv.MMKV

/**
 * Read/write access to the `notification_edit` MMKV namespace.
 *
 * Maps a [android.service.notification.StatusBarNotification.getKey] to the Telegram message
 * we sent for it, so an update to an already-forwarded notification edits that message instead
 * of posting a new one — and the mapping survives the NotificationListenerService process being
 * recycled (see [com.qwe7002.telegram_sms.NotificationService]).
 *
 * Records are stored as a compact `messageId:contentHash:timestamp` string so the notification
 * hot path never pays for reflection-based (de)serialization. Persistence lives here, separate
 * from the service, which keeps only the in-memory state machine.
 */
object NotificationEditStore {
    private const val logTag = "${TAG}.NotificationEditStore"
    private const val SEPARATOR = ":"

    /** One persisted notification → Telegram-message mapping. */
    data class Record(val messageId: Long, val contentHash: Int, val timestamp: Long)

    private fun mmkv(): MMKV = MMKV.mmkvWithID(NOTIFICATION_EDIT_ID)

    /**
     * Read the record for [key]. Returns null — and drops the stored entry — when it is missing,
     * malformed, carries no usable message id, or is older than [ttlMs] relative to [now].
     */
    @JvmStatic
    fun load(key: String, now: Long, ttlMs: Long): Record? {
        val mmkv = mmkv()
        val raw = mmkv.decodeString(key) ?: return null
        val parts = raw.split(SEPARATOR)
        if (parts.size != 3) {
            Log.w(logTag, "Discarding malformed edit record for $key: $raw")
            mmkv.removeValueForKey(key)
            return null
        }
        val messageId = parts[0].toLongOrNull() ?: 0L
        val contentHash = parts[1].toIntOrNull() ?: 0
        val timestamp = parts[2].toLongOrNull() ?: 0L
        if (messageId <= 0L || now - timestamp > ttlMs) {
            mmkv.removeValueForKey(key)
            return null
        }
        return Record(messageId, contentHash, timestamp)
    }

    /** Write (or overwrite) the mapping for [key]. No-op for a non-positive [messageId]. */
    @JvmStatic
    fun save(key: String, messageId: Long, contentHash: Int, timestamp: Long) {
        if (messageId <= 0L) return
        mmkv().encode(key, "$messageId$SEPARATOR$contentHash$SEPARATOR$timestamp")
    }

    /**
     * Remove every record older than [ttlMs] relative to [now]. Scans all keys in the namespace,
     * so callers should invoke this off the main thread.
     */
    @JvmStatic
    fun sweepExpired(now: Long, ttlMs: Long) {
        val mmkv = mmkv()
        val keys = mmkv.allKeys() ?: return
        for (k in keys) {
            val raw = mmkv.decodeString(k) ?: continue
            val timestamp = raw.substringAfterLast(SEPARATOR).toLongOrNull() ?: 0L
            if (now - timestamp > ttlMs) {
                mmkv.removeValueForKey(k)
            }
        }
    }
}
