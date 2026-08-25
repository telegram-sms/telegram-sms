package com.qwe7002.telegram_sms.static_class

import com.google.gson.Gson
import com.qwe7002.telegram_sms.data_structure.CcSendService
import com.qwe7002.telegram_sms.data_structure.ScannerJson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigImportTest {

    private val gson = Gson()

    private fun parseConfig(json: String): ScannerJson? =
        gson.fromJson(json, ScannerJson::class.java)

    private fun parseService(json: String): CcSendService? =
        gson.fromJson(json, CcSendService::class.java)

    private fun assertUsable(config: ScannerJson?) {
        assertNull(ConfigImport.validate(config))
        assertTrue(ConfigImport.isUsable(config))
    }

    private fun assertUnusable(config: ScannerJson?) {
        assertNotNull(ConfigImport.validate(config))
        assertFalse(ConfigImport.isUsable(config))
    }

    private fun assertUsable(service: CcSendService?) {
        assertNull(ConfigImport.validate(service))
        assertTrue(ConfigImport.isUsable(service))
    }

    private fun assertUnusable(service: CcSendService?) {
        assertNotNull(ConfigImport.validate(service))
        assertFalse(ConfigImport.isUsable(service))
    }

    @Test
    fun acceptsConfigWithAllExportedFields() {
        val config = parseConfig(
            """
            {
              "bot_token":"123:token",
              "api_address":"https://api.telegram.org",
              "chat_id":"-100123",
              "trusted_phone_number":"+15551234567",
              "battery_monitoring_switch":true,
              "charger_status":false,
              "chat_command":true,
              "fallback_sms":false,
              "privacy_mode":true,
              "verification_code":false,
              "call_notify":true,
              "topic_id":"42",
              "cc_service":null,
              "hide_phone_number":false,
              "doh_switch":true
            }
            """.trimIndent()
        )

        assertUsable(config)
    }

    @Test
    fun acceptsLegitimatelyEmptyOptionalStringsAndMissingOptionalServiceList() {
        val config = parseConfig(
            """
            {
              "bot_token":"123:token",
              "api_address":"",
              "chat_id":"-100123",
              "trusted_phone_number":"",
              "topic_id":""
            }
            """.trimIndent()
        )

        assertUsable(config)
    }

    @Test
    fun rejectsNullAndUnrelatedDocumentsAsConfigs() {
        val unrelated = parseConfig("""{"unrelated":true}""")

        assertUnusable(null as ScannerJson?)
        assertNotNull("Gson should construct an object despite absent non-null fields", unrelated)
        assertUnusable(unrelated)
    }

    @Test
    fun rejectsEveryMissingStringWrittenByTheExporter() {
        val fields = listOf(
            "bot_token",
            "api_address",
            "chat_id",
            "trusted_phone_number",
            "topic_id"
        )

        for (missing in fields) {
            val members = fields
                .filterNot { it == missing }
                .joinToString(",") { "\"$it\":\"value\"" }
            val config = parseConfig("{$members}")

            assertNotNull("Gson should construct a config missing $missing", config)
            assertNotNull("missing $missing must be rejected", ConfigImport.validate(config))
            assertFalse("missing $missing must be unusable", ConfigImport.isUsable(config))
        }
    }

    @Test
    fun rejectsEmptyOrBlankBotCredentialAndDestination() {
        val invalidPairs = listOf(
            "" to "-100123",
            " \t " to "-100123",
            "123:token" to "",
            "123:token" to " \t "
        )

        for ((botToken, chatId) in invalidPairs) {
            val config = parseConfig(
                """
                {
                  "bot_token":"$botToken",
                  "api_address":"",
                  "chat_id":"$chatId",
                  "trusted_phone_number":"",
                  "topic_id":""
                }
                """.trimIndent()
            )

            assertUnusable(config)
        }
    }

    @Test
    fun acceptsConfigContainingUsableCarbonCopyService() {
        val config = parseConfig(
            """
            {
              "bot_token":"123:token",
              "api_address":"",
              "chat_id":"-100123",
              "trusted_phone_number":"",
              "topic_id":"",
              "cc_service":[{
                "name":"webhook",
                "enabled":true,
                "har":{"log":{"entries":[{"request":{
                  "method":"GET","url":"https://example.test/hook"
                }}]}}
              }]
            }
            """.trimIndent()
        )

        assertUsable(config)
    }

    @Test
    fun rejectsBrokenNestedHarAndIdentifiesServiceIndex() {
        val config = parseConfig(
            """
            {
              "bot_token":"123:token",
              "api_address":"",
              "chat_id":"-100123",
              "trusted_phone_number":"",
              "topic_id":"",
              "cc_service":[
                {
                  "name":"sound",
                  "enabled":true,
                  "har":{"log":{"entries":[]}}
                },
                {
                  "name":"broken",
                  "enabled":false,
                  "har":{"log":{"entries":[{"request":{"url":"https://example.test/"}}]}}
                }
              ]
            }
            """.trimIndent()
        )

        val reason = ConfigImport.validate(config)
        assertNotNull(reason)
        assertTrue("reason should identify cc_service[1]: $reason", reason!!.contains("[1]"))
        assertFalse(ConfigImport.isUsable(config))
    }

    @Test
    fun rejectsNullNestedServiceAndIdentifiesItsIndex() {
        val config = parseConfig(
            """
            {
              "bot_token":"123:token",
              "api_address":"",
              "chat_id":"-100123",
              "trusted_phone_number":"",
              "topic_id":"",
              "cc_service":[
                {"name":"sound","enabled":true,"har":{"log":{"entries":[]}}},
                null
              ]
            }
            """.trimIndent()
        )

        val reason = ConfigImport.validate(config)
        assertNotNull(reason)
        assertTrue("reason should identify cc_service[1]: $reason", reason!!.contains("[1]"))
        assertFalse(ConfigImport.isUsable(config))
    }

    @Test
    fun acceptsUsableServiceIncludingEmptyName() {
        val service = parseService(
            """{"name":"","enabled":true,"har":{"log":{"entries":[]}}}"""
        )

        assertUsable(service)
    }

    @Test
    fun rejectsNullAndUnrelatedDocumentsAsServices() {
        val unrelated = parseService("""{"unrelated":true}""")

        assertUnusable(null as CcSendService?)
        assertNotNull("Gson should construct an object despite absent non-null fields", unrelated)
        assertUnusable(unrelated)
    }

    @Test
    fun rejectsServiceMissingNameOrHar() {
        val missingName = parseService(
            """{"enabled":true,"har":{"log":{"entries":[]}}}"""
        )
        val missingHar = parseService("""{"name":"webhook","enabled":true}""")

        assertUnusable(missingName)
        assertUnusable(missingHar)
    }

    @Test
    fun serviceValidationDelegatesStructuralHarValidation() {
        val service = parseService(
            """
            {
              "name":"webhook",
              "enabled":true,
              "har":{"log":{"entries":[{"request":{
                "method":"GET"
              }}]}}
            }
            """.trimIndent()
        )

        assertUnusable(service)
    }
}
