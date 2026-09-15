package org.openaustria.googletv.hermes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesSettingsTest {

    @Test
    fun `http and https endpoints are valid`() {
        assertEquals(EndpointValidation.VALID, HermesSettings.validateEndpoint("http://192.168.1.10:8642"))
        assertEquals(EndpointValidation.VALID, HermesSettings.validateEndpoint(" https://hermes.example.org/ "))
        assertEquals(EndpointValidation.VALID, HermesSettings.validateEndpoint("HTTP://hermes.local:8642/v1"))
    }

    @Test
    fun `blank endpoint is empty`() {
        assertEquals(EndpointValidation.EMPTY, HermesSettings.validateEndpoint("   "))
    }

    @Test
    fun `endpoints without http scheme or host are invalid`() {
        assertEquals(EndpointValidation.INVALID, HermesSettings.validateEndpoint("192.168.1.10:8642"))
        assertEquals(EndpointValidation.INVALID, HermesSettings.validateEndpoint("ftp://hermes.local"))
        assertEquals(EndpointValidation.INVALID, HermesSettings.validateEndpoint("http://"))
        assertEquals(EndpointValidation.INVALID, HermesSettings.validateEndpoint("http://hermes local"))
    }

    @Test
    fun `configured only with valid endpoint, token is optional`() {
        assertTrue(HermesSettings(endpoint = "http://hermes.local:8642").isConfigured)
        assertFalse(HermesSettings(endpoint = "", token = "geheim").isConfigured)
    }

    @Test
    fun `chat completions url accepts base, v1 and full url`() {
        val expected = "http://hermes.local:8642/v1/chat/completions"
        assertEquals(expected, HermesSettings.chatCompletionsUrl("http://hermes.local:8642"))
        assertEquals(expected, HermesSettings.chatCompletionsUrl("http://hermes.local:8642/"))
        assertEquals(expected, HermesSettings.chatCompletionsUrl("http://hermes.local:8642/v1/"))
        assertEquals(expected, HermesSettings.chatCompletionsUrl(" http://hermes.local:8642/v1/chat/completions "))
    }
}
