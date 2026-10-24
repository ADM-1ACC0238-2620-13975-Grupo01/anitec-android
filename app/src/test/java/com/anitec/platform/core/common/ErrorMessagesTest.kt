package com.anitec.platform.core.common

import com.anitec.platform.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {

    @Test
    fun `validation failures have their own message instead of the unknown one`() {
        assertEquals(R.string.error_validation, AppError.Validation().messageRes())
        assertEquals(R.string.error_validation, AppError.Validation(listOf("Name is required.")).messageRes())
        assertEquals(R.string.error_unknown, AppError.Unknown("boom").messageRes())
    }

    @Test
    fun `validation and conflict failures expose the server text as hints`() {
        assertEquals(listOf("Name is required."), AppError.Validation(listOf("Name is required.")).serverHints())
        assertEquals(listOf("Tag exists."), AppError.Conflict(listOf("Tag exists.")).serverHints())
    }

    @Test
    fun `other failures have no hints`() {
        assertTrue(AppError.Network.serverHints().isEmpty())
        assertTrue(AppError.Server(500).serverHints().isEmpty())
        assertTrue(AppError.Unknown("boom").serverHints().isEmpty())
    }
}
