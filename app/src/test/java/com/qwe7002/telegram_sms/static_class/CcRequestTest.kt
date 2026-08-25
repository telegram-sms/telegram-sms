package com.qwe7002.telegram_sms.static_class

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.qwe7002.telegram_sms.data_structure.Entry
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the request Carbon Copy actually puts on the wire.
 *
 * Everything here used to live inside `CcSendJob` as private instance methods, where no
 * JVM test could reach it: a regression in query de-duplication, MIME matching, cookie
 * reconstruction or transport-header filtering would have left the whole suite green while
 * every delivery went out malformed.
 *
 * `CcSendJob` still owns scheduling, MMKV and the network call; those stay untested here.
 */
class CcRequestTest {

    private val gson = Gson()

    private val mapper = mapOf("Title" to "SMS", "Message" to "hello world", "Code" to "1234")

    /** The same values pre-encoded, as `CcSendJob.createMapper(encoded = true)` produces. */
    private val encodeMapper =
        mapOf("Title" to "SMS", "Message" to "hello%20world", "Code" to "1234")

    private fun entry(requestJson: String): Entry =
        gson.fromJson("""{"request":$requestJson}""", Entry::class.java)

    private fun bodyText(request: Request): String {
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun getEntry(url: String, queryString: String = "[]") = entry(
        """
        {"method":"GET","url":"$url","httpVersion":"HTTP/1.1",
         "cookies":[],"headers":[],"queryString":$queryString,
         "headersSize":-1,"bodySize":0}
        """.trimIndent()
    )

    // --- URL and query string -------------------------------------------------

    @Test
    fun substitutesEncodedPlaceholdersDirectlyIntoTheUrl() {
        // Nothing escapes a value spliced into the URL string, which is why the caller
        // passes the pre-encoded mapper for this and the raw one for everything else.
        val request = CcRequest.build(
            getEntry("https://push.example/send/{{Message}}"), mapper, encodeMapper
        )!!
        assertEquals("https://push.example/send/hello%20world", request.url.toString())
    }

    @Test
    fun appendsQueryStringEntriesThatTheUrlDoesNotAlreadyCarry() {
        val request = CcRequest.build(
            getEntry("https://push.example/send", """[{"name":"text","value":"{{Message}}"}]"""),
            mapper, encodeMapper
        )!!
        assertEquals(listOf("hello world"), request.url.queryParameterValues("text"))
    }

    @Test
    fun doesNotDuplicateAQueryKeyAlreadyPresentInTheUrl() {
        // A browser-exported HAR carries the query in BOTH request.url and request.queryString.
        // Appending blindly would send ?group=sms&group=sms.
        val request = CcRequest.build(
            getEntry(
                "https://push.example/send?group=sms",
                """[{"name":"group","value":"sms"},{"name":"text","value":"{{Message}}"}]"""
            ),
            mapper, encodeMapper
        )!!
        assertEquals(listOf("sms"), request.url.queryParameterValues("group"))
        assertEquals(listOf("hello world"), request.url.queryParameterValues("text"))
    }

    @Test
    fun letsOkHttpPercentEncodeAppendedQueryValues() {
        // addQueryParameter escapes on its own, so the *raw* mapper is correct here.
        // Feeding it the pre-encoded one would double-encode into hello%2520world.
        val request = CcRequest.build(
            getEntry("https://push.example/send", """[{"name":"t","value":"{{Message}}"}]"""),
            mapper, encodeMapper
        )!!
        assertTrue(request.url.toString().endsWith("?t=hello%20world"))
        assertEquals("hello world", request.url.queryParameter("t"))
    }

    @Test
    fun returnsNullWhenTheUrlCannotBeParsed() {
        assertNull(CcRequest.build(getEntry("not a url"), mapper, encodeMapper))
        assertNull(CcRequest.build(getEntry("ftp://push.example/send"), mapper, encodeMapper))
    }

    // --- bodies ---------------------------------------------------------------

    @Test
    fun jsonBodyIsRenderedEscapedAndNormalized() {
        val request = CcRequest.build(
            entry(
                """
                {"method":"POST","url":"https://push.example/push","httpVersion":"HTTP/2",
                 "cookies":[],"headers":[],"queryString":[],"headersSize":-1,"bodySize":0,
                 "postData":{"mimeType":"application/json",
                   "text":"{\"title\":\"{{Title}}\",\"body\":\"{{Message}}\"}"}}
                """.trimIndent()
            ),
            mapper, encodeMapper
        )!!

        val body = JsonParser.parseString(bodyText(request)).asJsonObject
        assertEquals("SMS", body["title"].asString)
        assertEquals("hello world", body["body"].asString)

        // The captured mimeType is carried onto the body; toRequestBody appends the charset.
        val contentType = request.body!!.contentType()!!
        assertEquals("application/json", "${contentType.type}/${contentType.subtype}")
    }

    @Test
    fun jsonBodyMatchesOnTypeAndSubtypeIgnoringCharset() {
        // A captured HAR almost always carries "; charset=utf-8"; matching the whole string
        // would fall through to the unsupported-MIME branch and drop the body.
        val request = CcRequest.build(
            entry(
                """
                {"method":"POST","url":"https://push.example/push","httpVersion":"HTTP/2",
                 "cookies":[],"headers":[],"queryString":[],"headersSize":-1,"bodySize":0,
                 "postData":{"mimeType":"application/json; charset=utf-8",
                   "text":"{\"body\":\"{{Message}}\"}"}}
                """.trimIndent()
            ),
            mapper, encodeMapper
        )!!
        assertEquals(
            "hello world",
            JsonParser.parseString(bodyText(request)).asJsonObject["body"].asString
        )
    }

    @Test
    fun aValueContainingQuotesDoesNotBreakTheJsonBody() {
        // The whole point of CcSend.renderForJson: an SMS body full of quotes and newlines
        // has to survive into a valid JSON document.
        val nasty = mapOf("Title" to "t", "Message" to "he said \"hi\"\nbye\\", "Code" to "1")
        val request = CcRequest.build(
            entry(
                """
                {"method":"POST","url":"https://push.example/push","httpVersion":"HTTP/2",
                 "cookies":[],"headers":[],"queryString":[],"headersSize":-1,"bodySize":0,
                 "postData":{"mimeType":"application/json","text":"{\"b\":\"{{Message}}\"}"}}
                """.trimIndent()
            ),
            nasty, nasty
        )!!
        assertEquals(
            "he said \"hi\"\nbye\\",
            JsonParser.parseString(bodyText(request)).asJsonObject["b"].asString
        )
    }

    @Test
    fun unparsableJsonBodyFallsBackToAnEmptyObject() {
        // Documented fallback: rather than aborting the delivery, an unbalanced template
        // sends "{}". That silently drops the payload, so it is worth seeing in a test.
        val request = CcRequest.build(
            entry(
                """
                {"method":"POST","url":"https://push.example/push","httpVersion":"HTTP/2",
                 "cookies":[],"headers":[],"queryString":[],"headersSize":-1,"bodySize":0,
                 "postData":{"mimeType":"application/json","text":"{\"b\":"}}
                """.trimIndent()
            ),
            mapper, encodeMapper
        )!!
        assertEquals("{}", bodyText(request))
    }

    @Test
    fun emptyJsonTextBecomesAnEmptyObject() {
        val request = CcRequest.build(
            entry(
                """
                {"method":"POST","url":"https://push.example/push","httpVersion":"HTTP/2",
                 "cookies":[],"headers":[],"queryString":[],"headersSize":-1,"bodySize":0,
                 "postData":{"mimeType":"application/json","text":""}}
                """.trimIndent()
            ),
            mapper, encodeMapper
        )!!
        assertEquals("{}", bodyText(request))
    }

    @Test
    fun formUrlencodedBodyRendersEachParam() {
        val request = CcRequest.build(
            entry(
                """
                {"method":"POST","url":"https://push.example/push","httpVersion":"HTTP/1.1",
                 "cookies":[],"headers":[],"queryString":[],"headersSize":-1,"bodySize":0,
                 "postData":{"mimeType":"application/x-www-form-urlencoded",
                   "params":[{"name":"text","value":"{{Title}}"},
                             {"name":"desp","value":"{{Message}}"}]}}
                """.trimIndent()
            ),
            mapper, encodeMapper
        )!!
        // Form encoding, not URL encoding: a space is "+" here, which is what a
        // form-urlencoded receiver expects. This is the literal wire body.
        assertEquals("text=SMS&desp=hello+world", bodyText(request))

        val contentType = request.body!!.contentType()!!
        assertEquals(
            "application/x-www-form-urlencoded",
            "${contentType.type}/${contentType.subtype}"
        )
    }

    @Test
    fun plainTextBodyIsRenderedWithoutJsonEscaping() {
        // ntfy takes the message as a bare text/plain body, so the value must arrive
        // verbatim - running it through the JSON escaper would deliver literal \n and \".
        val values = mapOf("Title" to "t", "Message" to "line1\nline2 \"quoted\"", "Code" to "1")
        val request = CcRequest.build(
            entry(
                """
                {"method":"POST","url":"https://ntfy.example/topic","httpVersion":"HTTP/1.1",
                 "cookies":[],"headers":[],"queryString":[],"headersSize":-1,"bodySize":0,
                 "postData":{"mimeType":"text/plain","text":"{{Message}}"}}
                """.trimIndent()
            ),
            values, values
        )!!
        assertEquals("line1\nline2 \"quoted\"", bodyText(request))

        val contentType = request.body!!.contentType()!!
        assertEquals("text/plain", "${contentType.type}/${contentType.subtype}")
    }

    @Test
    fun unsupportedMimeTypeWithABodyIsRejectedRatherThanSentEmpty() {
        // postData is present but unusable: sending the request without it would deliver a
        // meaningless empty POST, so the entry is dropped instead.
        assertNull(
            CcRequest.build(
                entry(
                    """
                    {"method":"POST","url":"https://push.example/push","httpVersion":"HTTP/1.1",
                     "cookies":[],"headers":[],"queryString":[],"headersSize":-1,"bodySize":0,
                     "postData":{"mimeType":"text/xml","text":"<a/>"}}
                    """.trimIndent()
                ),
                mapper, encodeMapper
            )
        )
    }

    @Test
    fun postWithoutPostDataGetsAnEmptyFormBody() {
        // OkHttp refuses a POST with a null body, so a bodyless POST entry needs a stand-in.
        val request = CcRequest.build(
            entry(
                """
                {"method":"POST","url":"https://push.example/push","httpVersion":"HTTP/1.1",
                 "cookies":[],"headers":[],"queryString":[],"headersSize":-1,"bodySize":0}
                """.trimIndent()
            ),
            mapper, encodeMapper
        )!!
        assertEquals("POST", request.method)
        assertEquals(0L, request.body!!.contentLength())
    }

    @Test
    fun getHasNoBody() {
        val request = CcRequest.build(getEntry("https://push.example/send"), mapper, encodeMapper)!!
        assertEquals("GET", request.method)
        assertNull(request.body)
    }

    @Test
    fun unsupportedMethodIsRejected() {
        assertNull(
            CcRequest.build(
                entry(
                    """
                    {"method":"DELETE","url":"https://push.example/x","httpVersion":"HTTP/1.1",
                     "cookies":[],"headers":[],"queryString":[],"headersSize":-1,"bodySize":0}
                    """.trimIndent()
                ),
                mapper, encodeMapper
            )
        )
    }

    // --- headers and cookies --------------------------------------------------

    @Test
    fun rebuildsTheCookieHeaderFromTheCookieList() {
        val request = CcRequest.build(
            entry(
                """
                {"method":"GET","url":"https://push.example/send","httpVersion":"HTTP/1.1",
                 "cookies":[{"name":"session","value":"abc"},{"name":"code","value":"{{Code}}"}],
                 "headers":[],"queryString":[],"headersSize":-1,"bodySize":0}
                """.trimIndent()
            ),
            mapper, encodeMapper
        )!!
        assertEquals("session=abc; code=1234", request.header("Cookie"))
    }

    @Test
    fun aCapturedCookieHeaderIsNotReplayedAlongsideTheRebuiltOne() {
        val request = CcRequest.build(
            entry(
                """
                {"method":"GET","url":"https://push.example/send","httpVersion":"HTTP/1.1",
                 "cookies":[{"name":"session","value":"fresh"}],
                 "headers":[{"name":"Cookie","value":"session=stale"}],
                 "queryString":[],"headersSize":-1,"bodySize":0}
                """.trimIndent()
            ),
            mapper, encodeMapper
        )!!
        assertEquals(listOf("session=fresh"), request.headers.values("Cookie"))
    }

    @Test
    fun dropsTransportAndContentHeadersOkHttpOwns() {
        val request = CcRequest.build(
            entry(
                """
                {"method":"GET","url":"https://push.example/send","httpVersion":"HTTP/1.1",
                 "cookies":[],"queryString":[],"headersSize":-1,"bodySize":0,
                 "headers":[{"name":"Host","value":"stale.example"},
                            {"name":"Content-Length","value":"999"},
                            {"name":"Accept-Encoding","value":"br"},
                            {"name":"Connection","value":"close"},
                            {"name":"X-Api-Key","value":"k3y"}]}
                """.trimIndent()
            ),
            mapper, encodeMapper
        )!!
        assertNull(request.header("Host"))
        assertNull(request.header("Content-Length"))
        assertNull(request.header("Accept-Encoding"))
        assertNull(request.header("Connection"))
        assertEquals("k3y", request.header("X-Api-Key")) // anything else is replayed
    }

    /**
     * Authorization is in SKIP_HEADERS, so a captured token is dropped rather than
     * replayed. Note this is a behaviour change: the entry was added capitalised and the
     * lookup lowercases, so until that was corrected the header went out untouched.
     * A provider that authenticates this way now needs its token in the URL, the body,
     * or a differently-named header.
     */
    @Test
    fun dropsACapturedAuthorizationHeader() {
        val request = CcRequest.build(
            entry(
                """
                {"method":"GET","url":"https://push.example/send","httpVersion":"HTTP/1.1",
                 "cookies":[],"queryString":[],"headersSize":-1,"bodySize":0,
                 "headers":[{"name":"Authorization","value":"Bearer t0ken"},
                            {"name":"authorization","value":"Bearer lower"}]}
                """.trimIndent()
            ),
            mapper, encodeMapper
        )!!
        assertNull(request.header("Authorization"))
    }

    @Test
    fun headerSkippingIsCaseInsensitive() {
        // A HAR captured from HTTP/2 spells them lowercase; from HTTP/1.1, capitalized.
        val request = CcRequest.build(
            entry(
                """
                {"method":"GET","url":"https://push.example/send","httpVersion":"HTTP/2",
                 "cookies":[],"queryString":[],"headersSize":-1,"bodySize":0,
                 "headers":[{"name":"content-type","value":"text/plain"},
                            {"name":"HOST","value":"stale.example"}]}
                """.trimIndent()
            ),
            mapper, encodeMapper
        )!!
        assertNull(request.header("content-type"))
        assertNull(request.header("host"))
    }

    @Test
    fun rendersPlaceholdersInsideHeaderValues() {
        val request = CcRequest.build(
            entry(
                """
                {"method":"GET","url":"https://push.example/send","httpVersion":"HTTP/1.1",
                 "cookies":[],"queryString":[],"headersSize":-1,"bodySize":0,
                 "headers":[{"name":"X-Code","value":"{{Code}}"}]}
                """.trimIndent()
            ),
            mapper, encodeMapper
        )!!
        assertEquals("1234", request.header("X-Code"))
    }

    @Test
    fun anInvalidHeaderIsSkippedInsteadOfAbortingTheDelivery() {
        // OkHttp rejects non-ASCII header values with IllegalArgumentException. One bad
        // header must not cost the whole notification.
        val values = mapOf("Title" to "t", "Message" to "中文", "Code" to "1")
        val request = CcRequest.build(
            entry(
                """
                {"method":"GET","url":"https://push.example/send","httpVersion":"HTTP/1.1",
                 "cookies":[],"queryString":[],"headersSize":-1,"bodySize":0,
                 "headers":[{"name":"X-Bad","value":"{{Message}}"},
                            {"name":"X-Good","value":"ok"}]}
                """.trimIndent()
            ),
            values, values
        )!!
        assertNull(request.header("X-Bad"))
        assertEquals("ok", request.header("X-Good"))
    }

    // --- malformed HAR --------------------------------------------------------

    @Test
    fun aHarMissingItsArraysStillProducesARequest() {
        // Gson builds the HAR data classes through Unsafe, so a hand-trimmed capture arrives
        // with null where the non-null type says List. This used to NPE on queryString.
        val request = CcRequest.build(
            entry(
                """
                {"method":"GET","url":"https://push.example/send","httpVersion":"HTTP/1.1",
                 "headersSize":-1,"bodySize":0}
                """.trimIndent()
            ),
            mapper, encodeMapper
        )!!
        assertEquals("https://push.example/send", request.url.toString())
        assertNull(request.header("Cookie"))
        assertFalse(request.headers.names().contains("X-Anything"))
    }
}
