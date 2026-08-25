package com.qwe7002.telegram_sms.static_class

import com.google.gson.Gson
import com.qwe7002.telegram_sms.data_structure.HAR
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarImportTest {

    private val gson = Gson()

    private fun parse(json: String): HAR? = gson.fromJson(json, HAR::class.java)

    @Test
    fun acceptsEveryStructurallyRequiredField() {
        val archive = parse(
            """
            {"log":{"entries":[{"request":{
              "method":"GET","url":"https://example.test/send",
              "cookies":[],"headers":[],"queryString":[]
            }}]}}
            """.trimIndent()
        )

        assertNull(HarImport.validate(archive))
        assertTrue(HarImport.isUsable(archive))
    }

    @Test
    fun acceptsMissingCollectionsThatTheSenderTreatsAsEmpty() {
        val archive = parse(
            """{"log":{"entries":[{"request":{"method":"GET","url":"https://example.test/"}}]}}"""
        )

        assertNull(HarImport.validate(archive))
        assertTrue(HarImport.isUsable(archive))
    }

    @Test
    fun acceptsAnArchiveWithNothingToSend() {
        val archive = parse("""{"log":{"entries":[]}}""")

        assertNull(HarImport.validate(archive))
        assertTrue(HarImport.isUsable(archive))
    }

    @Test
    fun rejectsNullAndDocumentsThatAreNotHarArchives() {
        val notAnArchive = parse("""{"unrelated":true}""")

        assertNotNull(HarImport.validate(null))
        assertFalse(HarImport.isUsable(null))
        assertNotNull(notAnArchive)
        assertNotNull(HarImport.validate(notAnArchive))
        assertFalse(HarImport.isUsable(notAnArchive))
    }

    @Test
    fun rejectsMissingEntryList() {
        val archive = parse("""{"log":{}}""")

        assertNotNull(HarImport.validate(archive))
        assertFalse(HarImport.isUsable(archive))
    }

    @Test
    fun rejectsNullEntryAndIdentifiesItsIndex() {
        val archive = parse(
            """
            {"log":{"entries":[
              {"request":{"method":"GET","url":"https://first.example/"}},
              null,
              {"request":{"method":"GET","url":"https://third.example/"}}
            ]}}
            """.trimIndent()
        )

        val reason = HarImport.validate(archive)
        assertNotNull(reason)
        assertTrue(
            "reason should identify entry 1: $reason",
            Regex("""\b1\b""").containsMatchIn(reason!!)
        )
        assertFalse(HarImport.isUsable(archive))
    }

    @Test
    fun rejectsMissingRequestAndIdentifiesItsIndex() {
        val archive = parse(
            """
            {"log":{"entries":[
              {"request":{"method":"GET","url":"https://first.example/"}},
              {},
              {"request":{"method":"GET","url":"https://third.example/"}}
            ]}}
            """.trimIndent()
        )

        val reason = HarImport.validate(archive)
        assertNotNull(reason)
        assertTrue(
            "reason should identify entry 1: $reason",
            Regex("""\b1\b""").containsMatchIn(reason!!)
        )
        assertFalse(HarImport.isUsable(archive))
    }

    @Test
    fun rejectsMissingOrBlankMethod() {
        val missing = parse(
            """{"log":{"entries":[{"request":{"url":"https://example.test/"}}]}}"""
        )
        val blank = parse(
            """{"log":{"entries":[{"request":{"method":" \t ","url":"https://example.test/"}}]}}"""
        )

        assertNotNull(HarImport.validate(missing))
        assertFalse(HarImport.isUsable(missing))
        assertNotNull(HarImport.validate(blank))
        assertFalse(HarImport.isUsable(blank))
    }

    @Test
    fun rejectsMissingOrBlankUrl() {
        val missing = parse(
            """{"log":{"entries":[{"request":{"method":"GET"}}]}}"""
        )
        val blank = parse(
            """{"log":{"entries":[{"request":{"method":"GET","url":" \t "}}]}}"""
        )

        assertNotNull(HarImport.validate(missing))
        assertFalse(HarImport.isUsable(missing))
        assertNotNull(HarImport.validate(blank))
        assertFalse(HarImport.isUsable(blank))
    }

    @Test
    fun oneBadEntryMakesTheWholeArchiveUnusableAndIsIndexed() {
        val archive = parse(
            """
            {"log":{"entries":[
              {"request":{"method":"GET","url":"https://first.example/"}},
              {"request":{"method":"POST","url":"https://second.example/"}},
              {"request":{"method":"GET","url":""}}
            ]}}
            """.trimIndent()
        )

        val reason = HarImport.validate(archive)
        assertNotNull(reason)
        assertTrue(
            "reason should identify entry 2: $reason",
            Regex("""\b2\b""").containsMatchIn(reason!!)
        )
        assertFalse(HarImport.isUsable(archive))
    }

    @Test
    fun validationRejectsAnEntryThatCcRequestCannotBuild() {
        val archive = parse(
            """{"log":{"entries":[{"request":{"method":"DELETE","url":"https://example.test/"}}]}}"""
        )!!

        assertNull(CcRequest.build(archive.log.entries[0], emptyMap(), emptyMap()))
        assertNotNull(
            "validate must reject an archive containing an entry the sender refuses",
            HarImport.validate(archive)
        )
        assertFalse(HarImport.isUsable(archive))
    }
}

