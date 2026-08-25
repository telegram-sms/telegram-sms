package com.qwe7002.telegram_sms.static_class

import com.qwe7002.telegram_sms.data_structure.HAR
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Structural validation for a user-supplied HTTP Archive.
 *
 * Gson builds the HAR data classes through `Unsafe` because they have no no-arg
 * constructor, so it never applies Kotlin's null checks: any absent field lands as null
 * even where the declared type says it cannot be. `gson.fromJson(...)` therefore succeeds
 * on `{"a":1}` and hands back a HAR whose `log` is null, which only fails later — as an
 * NPE in the Carbon Copy list or a dropped delivery.
 *
 * "It parsed" is consequently not a usable check. [validate] is.
 */
object HarImport {

    /**
     * Returns null when [har] is structurally usable, or a short English reason why it is
     * not. The reason is for logs; callers facing the user should show a localized string.
     *
     * Only the fields something actually dereferences are required. A capture with no
     * `cookies` / `headers` / `queryString` arrays is accepted, because [CcRequest] treats
     * those as empty; a HAR with zero entries is accepted too, since both the editor and
     * the send job already have a defined "nothing to send" path for it.
     *
     * Structural completeness is not sufficient on its own: an archive is usable only if
     * [CcRequest.build] can actually turn every entry into a request. Accepting one it
     * would refuse is the failure this guards against — the editor would report the
     * service as fine and every delivery would silently do nothing. The named checks below
     * exist to say *what* is wrong; the final one guarantees the two agree even when a
     * future refusal reason has no named check here yet.
     */
    @JvmStatic
    fun validate(har: HAR?): String? {
        if (har == null) return "not a JSON object"
        @Suppress("SENSELESS_COMPARISON")
        if (har.log == null) return "missing \"log\""
        @Suppress("SENSELESS_COMPARISON")
        if (har.log.entries == null) return "missing \"log.entries\""

        har.log.entries.forEachIndexed { index, entry ->
            @Suppress("SENSELESS_COMPARISON")
            if (entry == null || entry.request == null) {
                return "entry $index has no \"request\""
            }
            val request = entry.request
            @Suppress("SENSELESS_COMPARISON")
            if (request.method == null || request.method.isBlank()) {
                return "entry $index has no \"request.method\""
            }
            if (request.method !in CcRequest.SUPPORTED_METHODS) {
                return "entry $index uses unsupported method \"${request.method}\" " +
                        "(supported: ${CcRequest.SUPPORTED_METHODS.joinToString(", ")})"
            }
            @Suppress("SENSELESS_COMPARISON")
            if (request.url == null || request.url.isBlank()) {
                return "entry $index has no \"request.url\""
            }
            if (CcSend.render(request.url, emptyMap()).toHttpUrlOrNull() == null) {
                return "entry $index has an unusable \"request.url\" (needs an http/https URL)"
            }
            // Backstop: whatever else would make the sender refuse this entry - an
            // unsupported body MIME type, a method/body combination OkHttp rejects - is
            // caught here rather than surfacing as a silent no-op at delivery time.
            // Rendering with no values leaves {{placeholders}} in place, which is fine:
            // they only ever appear where a real value would also be legal.
            if (CcRequest.build(entry, emptyMap(), emptyMap()) == null) {
                return "entry $index cannot be turned into a request"
            }
        }
        return null
    }

    /** Convenience for call sites that only need a yes/no. */
    @JvmStatic
    fun isUsable(har: HAR?): Boolean = validate(har) == null
}
