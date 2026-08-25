package com.qwe7002.telegram_sms.static_class

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.qwe7002.telegram_sms.data_structure.Entry
import com.qwe7002.telegram_sms.data_structure.Request as HarRequest
import com.qwe7002.telegram_sms.value.TAG
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Turns one captured HAR entry plus a placeholder mapper into the OkHttp [Request] that
 * Carbon Copy actually sends.
 *
 * This is deliberately separate from `CcSendJob`: the job owns scheduling, MMKV and the
 * network call, none of which run off-device, while everything here is pure and therefore
 * unit-testable. The delivery behaviour that used to be invisible to tests — query
 * de-duplication, MIME matching, cookie reconstruction, transport-header filtering — lives
 * in [build].
 */
object CcRequest {
    private const val logTag = "${TAG}.CcRequest"

    private val gson = Gson()

    private const val FORM_URLENCODED_SUBTYPE = "application/x-www-form-urlencoded"
    private const val JSON_SUBTYPE = "application/json"
    private const val PLAINTEXT_SUBTYPE = "text/plain"

    /**
     * The HTTP methods a captured entry may use. Public because [HarImport] rejects an
     * archive at import time for the same reasons [build] would refuse it at send time,
     * and the two must not drift apart.
     */
    @JvmField
    val SUPPORTED_METHODS = setOf("GET", "POST", "PUT")

    // Headers that must not be replayed verbatim from a captured HAR: Cookie is rebuilt from
    // request.cookies, and the rest are connection/content metadata that OkHttp or the
    // RequestBody recomputes — replaying a stale captured value duplicates or corrupts the request.
    //
    // Compared against header.name.lowercase(), so every entry has to be lower case.
    // "Authorization" was added capitalised and therefore never matched anything.
    private val SKIP_HEADERS = setOf(
        "cookie", "content-type", "content-length", "content-encoding", "host",
        "connection", "transfer-encoding", "keep-alive", "accept-encoding", "authorization"
    )

    /**
     * Builds the request for [entry], or returns null when the entry cannot produce one
     * (unusable URL, a body whose MIME type is not supported, or an unsupported method).
     *
     * @param mapper placeholder values used verbatim — for bodies, headers and cookies, and
     *   for query parameters, which OkHttp percent-encodes itself.
     * @param encodeMapper placeholder values pre-encoded for substitution directly into the
     *   URL string, where nothing else will escape them.
     */
    @JvmStatic
    fun build(
        entry: Entry,
        mapper: Map<String, String>,
        encodeMapper: Map<String, String>
    ): Request? {
        val request = entry.request

        val httpUrl = CcSend.render(request.url, encodeMapper).toHttpUrlOrNull() ?: run {
            Log.e(logTag, "Invalid URL: ${request.url}")
            return null
        }

        // addQueryParameter percent-encodes values itself, so feed it the raw mapper
        // (encodeMapper is only for {{placeholders}} substituted directly into the URL string).
        // Browser-exported HAR carries the query in BOTH request.url and request.queryString, so only
        // append params the parsed URL doesn't already have — otherwise we get ?k=v&k=v duplicates.
        // orEmpty() on the three list fields is not redundant despite their non-null types:
        // the HAR data classes have no no-arg constructor, so Gson builds them through Unsafe
        // and leaves any absent array null. A hand-trimmed HAR therefore reaches here with
        // nulls the type system says cannot exist, and used to NPE on the line below.
        val existingParams = httpUrl.queryParameterNames
        val httpUrlBuilder = httpUrl.newBuilder().apply {
            request.queryString.orEmpty().forEach { query ->
                if (query.name !in existingParams) {
                    addQueryParameter(query.name, CcSend.render(query.value, mapper))
                }
            }
        }

        val body = buildBody(request, mapper) ?: run {
            if (request.postData != null || request.method !in SUPPORTED_METHODS) {
                return null
            }
            defaultBody(request.method)
        }

        // OkHttp enforces its own method/body rules — a GET carrying a body, or a POST
        // without one, throws rather than returning an error. A hand-written HAR can hit
        // that, and an exception here would abort every remaining delivery in the batch.
        val requestBuilder = try {
            Request.Builder()
                .url(httpUrlBuilder.build())
                .method(request.method, body)
        } catch (e: IllegalArgumentException) {
            Log.e(logTag, "Entry cannot be sent as ${request.method}: ${e.message}")
            return null
        }

        // Add cookies (render placeholders so {{Code}} etc. work in cookie values too)
        val cookies = request.cookies.orEmpty()
        if (cookies.isNotEmpty()) {
            val cookieHeader = cookies.joinToString("; ") {
                "${it.name}=${CcSend.render(it.value, mapper)}"
            }
            addHeaderSafely(requestBuilder, "Cookie", cookieHeader)
        }

        // Add headers, skipping Cookie (rebuilt above) and transport/content headers OkHttp owns,
        // so a stale captured Cookie/Content-Length/Host isn't duplicated onto the request.
        request.headers.orEmpty().forEach { header ->
            if (header.name.lowercase() in SKIP_HEADERS) return@forEach
            addHeaderSafely(requestBuilder, header.name, CcSend.render(header.value, mapper))
        }

        return requestBuilder.build()
    }

    // OkHttp rejects non-ASCII header names/values with IllegalArgumentException; skip the
    // offending header instead of letting it abort the whole delivery.
    private fun addHeaderSafely(builder: Request.Builder, name: String, value: String) {
        try {
            builder.addHeader(name, value)
        } catch (e: IllegalArgumentException) {
            Log.w(logTag, "Skipping invalid header '$name': ${e.message}")
        }
    }

    @JvmStatic
    fun buildBody(request: HarRequest, mapper: Map<String, String>): RequestBody? {
        val postData = request.postData ?: return null
        val mimeType = postData.mimeType.toMediaTypeOrNull() ?: run {
            Log.w(logTag, "MIME type is null or invalid: ${postData.mimeType}")
            return null
        }
        // Match on type/subtype only — a captured HAR often carries a charset
        // (e.g. "application/json; charset=utf-8") which must not break the match.
        return when ("${mimeType.type}/${mimeType.subtype}") {
            FORM_URLENCODED_SUBTYPE -> {
                FormBody.Builder().apply {
                    postData.params?.forEach { param ->
                        add(param.name, CcSend.render(param.value, mapper))
                    }
                }.build()
            }

            JSON_SUBTYPE -> {
                val value = CcSend.renderForJson(postData.text ?: "", mapper)
                if (value.isNotEmpty()) {
                    try {
                        val jsonElement = JsonParser.parseString(value)
                        gson.toJson(jsonElement).toRequestBody(mimeType)
                    } catch (e: Exception) {
                        Log.e(logTag, "Failed to parse JSON: ${e.message}")
                        "{}".toRequestBody(mimeType)
                    }
                } else {
                    "{}".toRequestBody(mimeType)
                }
            }

            PLAINTEXT_SUBTYPE -> {
                CcSend.render(postData.text ?: "",mapper).toRequestBody(mimeType)
            }

            else -> {
                Log.w(logTag, "Unsupported MIME type: ${postData.mimeType}")
                null
            }
        }
    }

    @JvmStatic
    fun defaultBody(method: String): RequestBody? {
        return when (method) {
            "GET" -> null
            "POST", "PUT" -> FormBody.Builder().build()
            else -> {
                Log.w(logTag, "Unsupported request method: $method")
                null
            }
        }
    }
}
