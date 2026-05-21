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

        val contentHash = "$title$content".hashCode()
        val now = System.currentTimeMillis()

        // Decide what to do for this key:
        //   - identical content + fresh entry → drop (Android re-posted without payload change)
        //   - changed content + existing Telegram message → edit in place
        //   - new key or expired entry → send a fresh message
        val action: SendAction = synchronized(recentNotifications) {
            evictExpired(now)
            val previous = recentNotifications[sbn.key]
            when {
                previous != null && previous.contentHash == contentHash -> {
                    previous.timestamp = now
                    SendAction.Skip
                }
                previous != null && previous.telegramMessageId != null && !previous.inFlight -> {
                    previous.contentHash = contentHash
                    previous.timestamp = now
                    previous.inFlight = true
                    SendAction.Edit(previous.telegramMessageId!!)
                }
                else -> {
                    if (previous != null && previous.inFlight) {
                        // A previous send is still in flight. Issuing another sendMessage now would
                        // create a second Telegram message that we can never collapse back together,
                        // so just record the latest payload — when the in-flight call completes its
                        // onSuccess callback, the deferred update is replayed via editMessageText.
                        previous.contentHash = contentHash
                        previous.timestamp = now
                        previous.pendingTitle = title
                        previous.pendingContent = content
                        previous.pendingAppName = appName
                        return@synchronized SendAction.Skip
                    }
                    recentNotifications[sbn.key] = NotificationState(
                        contentHash = contentHash,
                        timestamp = now,
                        inFlight = true
                    )
                    SendAction.Send
                }
            }
        }

        when (action) {
            SendAction.Skip -> {
                Log.d(logTag, "[$packageName] Skipping duplicate notification: ${sbn.key}")
                return
            }
            SendAction.Send -> dispatchSend(sbn.key, appName, title, content, isEdit = false, messageId = 0)
            is SendAction.Edit -> dispatchSend(sbn.key, appName, title, content, isEdit = true, messageId = action.messageId)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Once the source notification disappears, drop our state so the next post for the same key
        // is treated as a fresh event rather than another edit of the now-stale Telegram message.
        synchronized(recentNotifications) {
            recentNotifications.remove(sbn.key)
        }
    }

    private fun dispatchSend(
        sbnKey: String,
        appName: String,
        title: String,
        content: String,
        isEdit: Boolean,
        messageId: Long
    ) {
        val rendered = Template.render(
            applicationContext,
            "TPL_notification",
            mapOf("APP" to appName, "Title" to title, "Description" to content)
        )

        if (!isEdit) {
            // Carbon-copy delivery (webhook / external services) only fires for the first send.
            // Edit operations cannot be replayed against arbitrary CC backends, so for refresh-style
            // notifications subsequent updates remain Telegram-only.
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
            onFailure = { handleSendFailure(sbnKey, isEdit) }
        )
    }

    private fun handleSendFailure(sbnKey: String, wasEdit: Boolean) {
        // Always release the inFlight latch so future updates aren't blocked.
        // For a failed sendMessage we have no message_id, so the next update will retry as a fresh
        // send. For a failed editMessageText we keep the existing message_id and the next update
        // will attempt to edit it again — the failure may have been transient (network blip).
        // Persistent edit failure (e.g. message deleted) is acceptable: we'll keep failing the edit
        // until onNotificationRemoved or TTL eviction clears the state, at which point the next
        // post sends fresh.
        val pendingReplay: PendingReplay? = synchronized(recentNotifications) {
            val state = recentNotifications[sbnKey] ?: return@synchronized null
            state.inFlight = false

            val messageId = state.telegramMessageId
            val pendingTitle = state.pendingTitle
            val pendingContent = state.pendingContent
            val pendingAppName = state.pendingAppName
            // We can only replay as an edit if we already have a message id from a prior successful
            // send. If this was the very first send and it failed, drop the pending payload — the
            // next OS-driven update will start fresh.
            if (messageId != null && pendingTitle != null && pendingContent != null && pendingAppName != null) {
                state.pendingTitle = null
                state.pendingContent = null
                state.pendingAppName = null
                state.inFlight = true
                PendingReplay(messageId, pendingAppName, pendingTitle, pendingContent)
            } else {
                state.pendingTitle = null
                state.pendingContent = null
                state.pendingAppName = null
                null
            }
        }

        if (pendingReplay != null) {
            dispatchSend(
                sbnKey,
                pendingReplay.appName,
                pendingReplay.title,
                pendingReplay.content,
                isEdit = true,
                messageId = pendingReplay.messageId
            )
        }
    }

    private fun handleSendResponse(sbnKey: String, wasEdit: Boolean, responseBody: String) {
        val pendingReplay: PendingReplay? = synchronized(recentNotifications) {
            val state = recentNotifications[sbnKey] ?: return@synchronized null

            if (!wasEdit) {
                val newId = try {
                    Other.getMessageId(responseBody)
                } catch (e: Exception) {
                    Log.w(logTag, "Failed to extract message_id for $sbnKey: ${e.message}")
                    null
                }
                if (newId != null) {
                    state.telegramMessageId = newId
                }
            }
            state.inFlight = false

            val pendingTitle = state.pendingTitle
            val pendingContent = state.pendingContent
            val pendingAppName = state.pendingAppName
            if (pendingTitle != null && pendingContent != null && pendingAppName != null && state.telegramMessageId != null) {
                state.pendingTitle = null
                state.pendingContent = null
                state.pendingAppName = null
                state.inFlight = true
                PendingReplay(state.telegramMessageId!!, pendingAppName, pendingTitle, pendingContent)
            } else {
                state.pendingTitle = null
                state.pendingContent = null
                state.pendingAppName = null
                null
            }
        }

        if (pendingReplay != null) {
            dispatchSend(
                sbnKey,
                pendingReplay.appName,
                pendingReplay.title,
                pendingReplay.content,
                isEdit = true,
                messageId = pendingReplay.messageId
            )
        }
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

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    private sealed class SendAction {
        data object Skip : SendAction()
        data object Send : SendAction()
        data class Edit(val messageId: Long) : SendAction()
    }

    private data class NotificationState(
        var contentHash: Int,
        var timestamp: Long,
        var telegramMessageId: Long? = null,
        var inFlight: Boolean = false,
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

        private val recentNotifications: MutableMap<String, NotificationState> = HashMap()
    }
}
