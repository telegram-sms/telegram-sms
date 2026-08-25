package com.qwe7002.telegram_sms.data_structure

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deserialization contract for the HTTP Archive blob that drives Carbon Copy.
 *
 * A [CcSendService] stores a browser-exported HAR verbatim in the `carbon_copy` MMKV
 * namespace, and `CcSendJob.sendRequest` replays `log.entries[].request` field by field.
 * Everything asserted here is something that job dereferences directly, so a shape
 * change shows up as a failing test instead of a swallowed delivery.
 */
class HarTest {

    private val gson = Gson()

    private val jsonPostHar = """
        {
          "log": {
            "version": "1.2",
            "entries": [
              {
                "request": {
                  "method": "POST",
                  "url": "https://api.day.app/push?group=sms",
                  "httpVersion": "HTTP/2",
                  "cookies": [{ "name": "session", "value": "abc" }],
                  "headers": [
                    { "name": "Content-Type", "value": "application/json; charset=utf-8" },
                    { "name": "Authorization", "value": "Bearer t0ken" }
                  ],
                  "queryString": [{ "name": "group", "value": "sms" }],
                  "headersSize": 214,
                  "bodySize": 57,
                  "postData": {
                    "mimeType": "application/json; charset=utf-8",
                    "text": "{\"title\":\"{{Title}}\",\"body\":\"{{Message}}\"}"
                  }
                }
              }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun parsesABrowserExportedJsonPostEntry() {
        val har = gson.fromJson(jsonPostHar, HAR::class.java)

        assertEquals("1.2", har.log.version)
        assertEquals(1, har.log.entries.size)

        val request = har.log.entries[0].request
        assertEquals("POST", request.method)
        assertEquals("https://api.day.app/push?group=sms", request.url)
        assertEquals("HTTP/2", request.httpVersion)
        assertEquals(214L, request.headersSize)
        assertEquals(57L, request.bodySize)

        assertEquals(listOf("session"), request.cookies.map { it.name })
        assertEquals("abc", request.cookies[0].value)
        assertEquals(listOf("Content-Type", "Authorization"), request.headers.map { it.name })
        assertEquals("Bearer t0ken", request.headers[1].value)
    }

    @Test
    fun keepsTheQueryInBothUrlAndQueryString() {
        // A browser export carries the query twice. CcSendJob relies on that: it parses
        // the URL, then only appends queryString entries whose name is not already there.
        val request = gson.fromJson(jsonPostHar, HAR::class.java).log.entries[0].request
        assertTrue(request.url.contains("group=sms"))
        assertEquals(listOf("group"), request.queryString.map { it.name })
        assertEquals("sms", request.queryString[0].value)
    }

    @Test
    fun keepsTheCharsetOnTheMimeTypeAndTheTemplateInTheBody() {
        // CcSendJob matches on type/subtype only, precisely because the charset is here.
        val postData = gson.fromJson(jsonPostHar, HAR::class.java).log.entries[0].request.postData
        assertNotNull(postData)
        assertEquals("application/json; charset=utf-8", postData!!.mimeType)
        assertEquals("""{"title":"{{Title}}","body":"{{Message}}"}""", postData.text)
        assertNull(postData.params) // absent for a raw JSON body
    }

    @Test
    fun parsesAFormUrlencodedEntryWithParamsAndNoText() {
        val har = gson.fromJson(
            """
            {"log":{"version":"1.2","entries":[{"request":{
              "method":"POST","url":"https://push.example/api","httpVersion":"HTTP/1.1",
              "cookies":[],"headers":[],"queryString":[],"headersSize":-1,"bodySize":-1,
              "postData":{"mimeType":"application/x-www-form-urlencoded",
                "params":[{"name":"text","value":"{{Title}}"},{"name":"desp","value":"{{Message}}"}]}
            }}]}}
            """.trimIndent(),
            HAR::class.java
        )

        val postData = har.log.entries[0].request.postData!!
        assertEquals("application/x-www-form-urlencoded", postData.mimeType)
        assertEquals(listOf("text", "desp"), postData.params!!.map { it.name })
        assertEquals("{{Message}}", postData.params!![1].value)
        assertNull(postData.text) // absent for a params-style body
    }

    @Test
    fun getEntryWithoutPostDataYieldsNullBody() {
        // The GET path: CcSendJob substitutes a default body only when postData is null.
        val har = gson.fromJson(
            """
            {"log":{"version":"1.2","entries":[{"request":{
              "method":"GET","url":"https://push.example/send?msg={{Message}}",
              "httpVersion":"HTTP/1.1","cookies":[],"headers":[],"queryString":[],
              "headersSize":-1,"bodySize":0}}]}}
            """.trimIndent(),
            HAR::class.java
        )
        assertNull(har.log.entries[0].request.postData)
    }

    @Test
    fun anEmptyEntryListSurvives() {
        // CcSendJob checks for this explicitly and logs "HAR is empty" instead of crashing.
        val har = gson.fromJson("""{"log":{"version":"1.2","entries":[]}}""", HAR::class.java)
        assertTrue(har.log.entries.isEmpty())
    }

    @Test
    fun carbonCopyServiceWrapsTheHarWithItsEnabledFlag() {
        val service = gson.fromJson(
            """{"name":"bark","enabled":false,"har":$jsonPostHar}""",
            CcSendService::class.java
        )
        assertEquals("bark", service.name)
        assertEquals(false, service.enabled)
        assertEquals("POST", service.har.log.entries[0].request.method)

        // Round trip: this object is re-serialized back into MMKV on every edit.
        val reparsed = gson.fromJson(gson.toJson(service), CcSendService::class.java)
        assertEquals(service.har.log.entries[0].request.url, reparsed.har.log.entries[0].request.url)
    }

    /**
     * These are Kotlin data classes with no no-arg constructor, so Gson instantiates them
     * through Unsafe and simply leaves absent fields null - the non-null declarations are
     * not enforced at runtime. This test pins that, because it is the reason two other
     * things exist and must keep existing:
     *
     *  - `HarImport.validate` rejects such a document at the import boundary, since
     *    "gson.fromJson did not throw" says nothing about whether the HAR is usable;
     *  - `CcRequest.build` calls `orEmpty()` on the three list fields, which looks
     *    redundant against their non-null types and is not. Before that, a trimmed HAR
     *    reaching the send job NPE'd on `request.queryString.forEach`.
     *
     * If Gson is ever configured with an adapter that enforces Kotlin nullability, this is
     * the test that should be rewritten to assert the rejection instead.
     */
    @Test
    fun missingNonNullArraysDeserializeAsNull_knownTrap() {
        val har = gson.fromJson(
            """
            {"log":{"version":"1.2","entries":[{"request":{
              "method":"GET","url":"https://push.example/send","httpVersion":"HTTP/1.1",
              "headersSize":-1,"bodySize":0}}]}}
            """.trimIndent(),
            HAR::class.java
        )

        val request = har.log.entries[0].request
        val headers: List<Header>? = request.headers
        val cookies: List<Cookie>? = request.cookies
        val queryString: List<Header>? = request.queryString
        assertNull(headers)
        assertNull(cookies)
        assertNull(queryString)
    }
}
