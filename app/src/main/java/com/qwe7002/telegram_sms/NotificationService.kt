package com.qwe7002.telegram_sms

import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.qwe7002.telegram_sms.data_structure.telegram.RequestMessage
import com.qwe7002.telegram_sms.static_class.Other
import com.qwe7002.telegram_sms.static_class.TelegramApi
import com.qwe7002.telegram_sms.static_class.Template
import com.qwe7002.telegram_sms.value.CcType
import com.tencent.mmkv.MMKV
import com.google.gson.Gson
import com.qwe7002.telegram_sms.MMKV.NOTIFY_ID
import com.qwe7002.telegram_sms.store.NotificationEditStore
import com.qwe7002.telegram_sms.value.TAG

class NotificationService : NotificationListenerService() {
    lateinit var preferences: MMKV
    private val logTag = "${TAG}.NotificationService"

    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(applicationContext)
        preferences = MMKV.defaultMMKV()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        Log.d(logTag, "onNotificationPosted: $packageName")

        if (!preferences.getBoolean("initialized", false)) {
            Log.i(logTag, "Uninitialized, Notification receiver is deactivated.")
            return
        }
        val notifyMMKV = MMKV.mmkvWithID(NOTIFY_ID)
        val notifyListStr = notifyMMKV.getString("listen_list", "[]")
        val listenList: List<String> =
            Gson().fromJson(notifyListStr, Array<String>::class.java).toList()

        if (!listenList.contains(packageName)) {
            Log.i(logTag, "[$packageName] Not in the list of listening packages.")
            return
        }

        // Skip group summary notifications — their child notifications are forwarded individually,
        // so forwarding the summary as well produces a duplicate.
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            Log.d(logTag, "[$packageName] Skipping group summary notification: ${sbn.key}")
            return
        }

        val extras = sbn.notification.extras ?: Bundle()
        var appName = "unknown"
        Log.d(logTag, "onNotificationPosted: $appNameList")
        if (appNameList.containsKey(packageName)) {
            appName = appNameList[packageName].toString()
        } else {
            val pm = applicationContext.packageManager
            try {
                val applicationInfo = pm.getApplicationInfo(sbn.packageName, 0)
                appName = pm.getApplicationLabel(applicationInfo) as String
                appNameList[packageName] = appName
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(logTag, "onNotificationPosted: ", e)
            }
        }

        // Some apps put a SpannableString (CharSequence) into EXTRA_TITLE/EXTRA_TEXT.
        // Bundle.getString() throws ClassCastException in that case and returns the default,
        // so always read as CharSequence and toString().
        val fallback = getString(R.string.unable_to_obtain_information)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: fallback
        val content = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: fallback

        val contentHash = contentHashOf(title, content)
        val now = System.currentTimeMillis()

        // Decide what to do for this key. `confirmedHash` is the hash of the content we believe is
        // currently shown in the Telegram message (null until the first send succeeds); it is only
        // advanced on a successful send/edit, so a failed request never makes us wrongly skip a
        // later repost.
        //   - request already in flight        → stash latest payload, replay when it completes
        //   - content already shown            → drop (Android re-posted without a payload change)
        //   - content changed + have a message → edit it in place
        //   - new key, or no usable message id → send a fresh message
        val action: SendAction = synchronized(recentNotifications) {
            evictExpired(now)
            maybeSweepPersisted(now)
            // Fall back to the on-disk mapping when the in-memory entry is gone (process recycled,
            // or the notification was removed) so a follow-up update still edits the existing message.
            val previous = recentNotifications[sbn.key]
                ?: loadPersisted(sbn.key, now)?.also { recentNotifications[sbn.key] = it }
            when {
                previous == null -> {
                    recentNotifications[sbn.key] = NotificationState(
                        timestamp = now,
                        inFlight = true,
                        inFlightHash = contentHash
                    )
                    SendAction.Send
                }
                previous.inFlight -> {
                    // A send/edit is in flight. Issuing another request now would create a second
                    // Telegram message we can never collapse back together, so just record the
                    // latest payload — the in-flight call's onSuccess replays it via editMessageText.
                    previous.timestamp = now
                    if (contentHash != previous.inFlightHash) {
                        previous.pendingTitle = title
                        previous.pendingContent = content
                        previous.pendingAppName = appName
                    }
                    SendAction.Skip
                }
                previous.confirmedHash == contentHash -> {
                    // Already showing this exact content; just keep the entry (and its TTL) alive.
                    previous.timestamp = now
                    val id = previous.telegramMessageId ?: 0L
                    if (id > 0L) NotificationEditStore.save(sbn.key, id, contentHash, now)
                    SendAction.Skip
                }
                (previous.telegramMessageId ?: 0L) > 0L -> {
                    previous.timestamp = now
                    previous.inFlight = true
                    previous.inFlightHash = contentHash
                    SendAction.Edit(previous.telegramMessageId!!)
                }
                else -> {
                    // Have an entry but no usable message id (a prior send failed): start fresh.
                    previous.timestamp = now
                    previous.inFlight = true
                    previous.inFlightHash = contentHash
                    SendAction.Send
                }
            }
        }

        when (action) {
            SendAction.Skip -> {
                Log.d(logTag, "[$packageName] Skipping duplicate notification: ${sbn.key}")
                return
            }
            SendAction.Send -> dispatchSend(sbn.key, appName, title, content, isEdit = false, messageId = 0, triggerCarbonCopy = true)
            is SendAction.Edit -> dispatchSend(sbn.key, appName, title, content, isEdit = true, messageId = action.messageId, triggerCarbonCopy = false)
        }
    }

    // Intentionally NOT overriding onNotificationRemoved to drop our tracking state.
    //
    // Apps routinely remove-then-repost a notification to "update" it (and the service process
    // itself is recycled aggressively). If we deleted state on removal, the very next post for the
    // same key would no longer find the tracked message id and would start a brand-new Telegram
    // message instead of editing the existing one — which is exactly the duplicate-message bug
    // this class exists to prevent. The mapping is bounded instead by STATE_TTL_MS (in-memory
    // eviction in evictExpired, on-disk sweep in maybeSweepPersisted), well under Telegram's 48h
    // editMessageText window.

    private fun dispatchSend(
        sbnKey: String,
        appName: String,
        title: String,
        content: String,
        isEdit: Boolean,
        messageId: Long,
        triggerCarbonCopy: Boolean
    ) {
        val rendered = Template.render(
            applicationContext,
            "TPL_notification",
            mapOf("APP" to appName, "Title" to title, "Description" to content)
        )

        if (triggerCarbonCopy) {
            // Carbon-copy delivery (webhook / external services) fires only for the original send.
            // Edits can't be replayed against arbitrary CC backends, and a retry after a failed send
            // must not re-fire CC (it already fired on the first attempt), so refresh-style updates
            // and retries stay Telegram-only.
            CcSendJob.startJob(
                applicationContext,
                CcType.NOTIFICATION,
                applicationContext.getString(R.string.Notification_Listener_title),
                rendered
            )
        }

        val requestBody = RequestMessage()
        requestBody.text = rendered
        if (isEdit) {
            requestBody.messageId = messageId
        }

        TelegramApi.sendMessage(
            context = applicationContext,
            requestBody = requestBody,
            method = if (isEdit) "editMessageText" else "sendMessage",
            fallbackSubId = -1,
            // Edits are best-effort: a stale message id (deleted, > 48h, content unchanged on the
            // Telegram side) returns a 400. Don't enqueue these into the resend loop — a repeated
            // edit attempt has the same problem, and a sendMessage replay would double-post.
            enableResend = !isEdit,
            onSuccess = { responseBody -> handleSendResponse(sbnKey, isEdit, responseBody) },
            onFailure = { handleSendFailure(sbnKey) }
        )
    }

    private fun handleSendFailure(sbnKey: String) {
        // Release the inFlight latch so future updates aren't blocked, and leave confirmedHash
        // untouched (we did NOT change what Telegram shows). For a failed sendMessage we have no
        // message id, so the next update retries as a fresh send. For a failed editMessageText we
        // keep the existing message id and the next update edits it again — the failure may have
        // been transient. Persistent edit failure (e.g. message deleted) is acceptable: edits keep
        // failing until TTL eviction clears the state, after which the next post sends fresh.
        val pendingReplay: PendingReplay? = synchronized(recentNotifications) {
            val state = recentNotifications[sbnKey] ?: return@synchronized null
            state.inFlight = false
            buildPendingReplay(state)
        }
        replayPending(sbnKey, pendingReplay)
    }

    private fun handleSendResponse(sbnKey: String, wasEdit: Boolean, responseBody: String) {
        val pendingReplay: PendingReplay? = synchronized(recentNotifications) {
            val state = recentNotifications[sbnKey] ?: return@synchronized null

            // Decide whether the content we just sent is now actually shown in Telegram.
            val confirmed: Boolean = if (!wasEdit) {
                // getMessageId returns 0L (never null) for error / non-Telegram bodies — e.g. a
                // captive-portal HTML page served with HTTP 200. A value <= 0 means no real message
                // was created, so don't record an id and don't confirm the content: the next post
                // of the same content should retry rather than be skipped.
                val newId = Other.getMessageId(responseBody)
                if (newId > 0L) {
                    state.telegramMessageId = newId
                    true
                } else {
                    Log.w(logTag, "No usable message_id in response for $sbnKey: $responseBody")
                    false
                }
            } else {
                // editMessageText returned 200 (applied, or a harmless "message is not modified").
                true
            }

            if (confirmed) {
                state.confirmedHash = state.inFlightHash
            }
            state.inFlight = false

            // Persist key -> message_id so an update arriving after this process is recycled still
            // edits the existing message instead of posting a new one.
            val id = state.telegramMessageId ?: 0L
            if (confirmed && id > 0L) {
                NotificationEditStore.save(sbnKey, id, state.inFlightHash, state.timestamp)
            }

            buildPendingReplay(state)
        }
        replayPending(sbnKey, pendingReplay)
    }

    private fun replayPending(sbnKey: String, pendingReplay: PendingReplay?) {
        if (pendingReplay != null) {
            // Edit the existing message when we have one; otherwise (the original send failed before
            // we learned a message id) retry as a fresh send. Either way CC has already fired, so
            // don't trigger it again.
            dispatchSend(
                sbnKey,
                pendingReplay.appName,
                pendingReplay.title,
                pendingReplay.content,
                isEdit = pendingReplay.messageId > 0L,
                messageId = pendingReplay.messageId,
                triggerCarbonCopy = false
            )
        }
    }

    // Promote any stashed pending payload into an in-flight request. Caller must hold the
    // recentNotifications lock. Returns null (and clears the pending fields) when there is nothing
    // to replay. A returned messageId of 0 means "no message yet" — the caller retries as a fresh
    // send rather than dropping the update.
    private fun buildPendingReplay(state: NotificationState): PendingReplay? {
        val pendingTitle = state.pendingTitle
        val pendingContent = state.pendingContent
        val pendingAppName = state.pendingAppName
        state.pendingTitle = null
        state.pendingContent = null
        state.pendingAppName = null
        if (pendingTitle != null && pendingContent != null && pendingAppName != null) {
            state.inFlight = true
            state.inFlightHash = contentHashOf(pendingTitle, pendingContent)
            return PendingReplay(state.telegramMessageId ?: 0L, pendingAppName, pendingTitle, pendingContent)
        }
        return null
    }

    private fun evictExpired(now: Long) {
        val iterator = recentNotifications.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.timestamp > STATE_TTL_MS && !entry.value.inFlight) {
                iterator.remove()
            }
        }
    }

    // Reconstruct in-memory tracking state from the persisted mapping (see NotificationEditStore)
    // when the process-global map has lost it (service restart / removed notification).
    private fun loadPersisted(key: String, now: Long): NotificationState? {
        val record = NotificationEditStore.load(key, now, STATE_TTL_MS) ?: return null
        return NotificationState(
            confirmedHash = record.contentHash,
            timestamp = record.timestamp,
            telegramMessageId = record.messageId,
            inFlight = false,
            inFlightHash = record.contentHash
        )
    }

    // Drop expired on-disk entries. Throttled to SWEEP_INTERVAL_MS, and run on a background thread:
    // notification callbacks fire on the main thread, so scanning every persisted key here would
    // risk an ANR. The store touches only the notification_edit namespace, so it needs no lock.
    private fun maybeSweepPersisted(now: Long) {
        if (now - lastSweep < SWEEP_INTERVAL_MS) return
        lastSweep = now
        Thread { NotificationEditStore.sweepExpired(now, STATE_TTL_MS) }.start()
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    private sealed class SendAction {
        data object Skip : SendAction()
        data object Send : SendAction()
        data class Edit(val messageId: Long) : SendAction()
    }

    private data class NotificationState(
        // Hash of the content currently shown in Telegram; null until the first send succeeds.
        var confirmedHash: Int? = null,
        var timestamp: Long,
        var telegramMessageId: Long? = null,
        var inFlight: Boolean = false,
        // Hash of the content of the in-flight request; promoted to confirmedHash on success.
        var inFlightHash: Int = 0,
        var pendingTitle: String? = null,
        var pendingContent: String? = null,
        var pendingAppName: String? = null
    )

    private data class PendingReplay(
        val messageId: Long,
        val appName: String,
        val title: String,
        val content: String
    )

    companion object {
        var appNameList: MutableMap<String, String?> = HashMap()

        // Telegram allows editMessageText up to 48 hours after the original send. Cap state retention
        // a bit under that so we never attempt an edit Telegram is guaranteed to reject.
        private const val STATE_TTL_MS = 36L * 60 * 60 * 1000

        // How often the on-disk sweep is allowed to run (it scans every persisted key).
        private const val SWEEP_INTERVAL_MS = 10L * 60 * 1000

        @Volatile
        private var lastSweep = 0L

        private val recentNotifications: MutableMap<String, NotificationState> = HashMap()

        private fun contentHashOf(title: String, content: String): Int = "$title$content".hashCode()
    }
}
