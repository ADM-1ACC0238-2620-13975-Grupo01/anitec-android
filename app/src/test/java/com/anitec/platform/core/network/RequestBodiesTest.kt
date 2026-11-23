package com.anitec.platform.core.network

// Test coverage marker: request body scenarios.

import com.anitec.platform.financial.infrastructure.remote.FinancialRecordDto
import com.anitec.platform.sanitary.infrastructure.remote.HealthEventDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestBodiesTest {
    private val json = NetworkModule.provideJson()

    @Test
    fun `fields left empty in a form still travel, so the server does not read them as missing`() {
        val body = json.encodeToString(HealthEventDto.serializer(), HealthEventDto(animalId = 4, type = "Vacuna", date = "2026-11-24"))

        val sent = Json.parseToJsonElement(body).jsonObject
        listOf("description", "veterinarian", "diagnosis", "treatment", "prescription", "followUp").forEach { field ->
            assertTrue("$field is missing from $body", field in sent)
            assertEquals("", sent.getValue(field).jsonPrimitive.content)
        }
    }

    @Test
    fun `an optional field that is null is still left out`() {
        val body = json.encodeToString(HealthEventDto.serializer(), HealthEventDto(animalId = 4, type = "Vacuna", date = "2026-11-24"))

        assertFalse("nextDueDate" in Json.parseToJsonElement(body).jsonObject)
    }

    @Test
    fun `a financial record with an empty description sends it empty`() {
        val body = json.encodeToString(FinancialRecordDto.serializer(), FinancialRecordDto(ownerId = 10, type = "Ingreso", amount = 5.0, date = "2026-11-24"))

        assertEquals("", Json.parseToJsonElement(body).jsonObject.getValue("description").jsonPrimitive.content)
    }
}
