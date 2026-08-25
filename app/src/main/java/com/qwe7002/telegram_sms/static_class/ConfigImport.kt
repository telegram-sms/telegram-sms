package com.qwe7002.telegram_sms.static_class

import com.qwe7002.telegram_sms.data_structure.CcSendService
import com.qwe7002.telegram_sms.data_structure.ScannerJson

/**
 * Structural validation for configuration imported from outside the app — a scanned QR
 * code, or a bundle fetched from the config-transfer service.
 *
 * Same hazard as [HarImport], one level up: these are Kotlin data classes with no no-arg
 * constructor, so Gson builds them through `Unsafe` and leaves absent fields null despite
 * their non-null types. A truncated or unrelated payload therefore deserializes without
 * complaint and only fails at the point of use — `MainActivity` reads
 * `jsonConfig.apiAddress.isNotEmpty()` straight off the parsed object, and a null there is
 * an immediate crash rather than a message to the user.
 *
 * Everything the exporter writes is required here. `TransferConfigActivity.getConfigJson`
 * fills every string from a preference with a default, so a config of ours never has a
 * null one; a payload that does is not a config of ours, whatever else it may be.
 */
object ConfigImport {

    /** Returns null when [config] is usable, or a short English reason why it is not. */
    @JvmStatic
    fun validate(config: ScannerJson?): String? {
        if (config == null) return "not a JSON object"

        val required = mapOf(
            "bot_token" to config.botToken,
            "api_address" to config.apiAddress,
            "chat_id" to config.chatId,
            "trusted_phone_number" to config.trustedPhoneNumber,
            "topic_id" to config.topicID
        )
        for ((key, value) in required) {
            @Suppress("SENSELESS_COMPARISON")
            if (value == null) return "missing \"$key\""
        }
        if (config.botToken.isBlank() || config.chatId.isBlank()) {
            // The other strings may legitimately be empty; without these two there is
            // nothing to import.
            return "\"bot_token\" and \"chat_id\" must not be empty"
        }

        // cc_service is optional, but a present one is replayed by CcSendJob and has to be
        // as sound as anything added through the editor.
        config.ccService?.forEachIndexed { index, service ->
            validate(service)?.let { return "cc_service[$index]: $it" }
        }
        return null
    }

    /** Returns null when [service] is usable, or a short English reason why it is not. */
    @JvmStatic
    fun validate(service: CcSendService?): String? {
        if (service == null) return "not a JSON object"
        @Suppress("SENSELESS_COMPARISON")
        if (service.name == null) return "missing \"name\""
        @Suppress("SENSELESS_COMPARISON")
        if (service.har == null) return "missing \"har\""
        return HarImport.validate(service.har)
    }

    /** Convenience for call sites that only need a yes/no. */
    @JvmStatic
    fun isUsable(config: ScannerJson?): Boolean = validate(config) == null

    /** Convenience for call sites that only need a yes/no. */
    @JvmStatic
    fun isUsable(service: CcSendService?): Boolean = validate(service) == null
}
