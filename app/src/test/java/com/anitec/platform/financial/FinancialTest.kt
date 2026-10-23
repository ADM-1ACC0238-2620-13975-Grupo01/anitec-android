package com.anitec.platform.financial

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.financial.domain.FinancialRecord
import com.anitec.platform.financial.domain.FinancialRecordDraft
import com.anitec.platform.financial.domain.FinancialScope
import com.anitec.platform.financial.infrastructure.FinancialRepositoryImpl
import com.anitec.platform.financial.infrastructure.local.FinancialDao
import com.anitec.platform.financial.infrastructure.local.FinancialRecordEntity
import com.anitec.platform.financial.infrastructure.remote.FinancialApi
import com.anitec.platform.financial.infrastructure.remote.FinancialRecordDto
import com.anitec.platform.financial.interfaces.ui.formatSoles
import com.anitec.platform.financial.interfaces.viewmodel.FinancialFormUiState
import com.anitec.platform.financial.interfaces.viewmodel.parseAmount
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Locale

private fun record(id: Int, type: String, amount: Double, ownerId: Int = 10) =
    FinancialRecord(id, ownerId, type, "Alimento", amount, "2026-10-01", "")

class FinancialRulesTest {

    @Test
    fun `a rancher sees only their own records`() {
        val all = listOf(record(1, "Ingreso", 10.0, ownerId = 10), record(2, "Ingreso", 20.0, ownerId = 11))
        assertEquals(listOf(1), FinancialScope.visible(all, 10).map { it.id })
    }

    @Test
    fun `the summary adds income and expenses apart and subtracts them for the balance`() {
        val summary = FinancialScope.summarize(
            listOf(record(1, "Ingreso", 1000.50), record(2, "Ingreso", 200.0), record(3, "Egreso", 350.25)),
        )
        assertEquals(1200.5, summary.income, 0.0)
        assertEquals(350.25, summary.expenses, 0.0)
        assertEquals(850.25, summary.balance, 0.0)
    }

    @Test
    fun `cents do not drift when many small amounts are added`() {
        val records = (1..10).map { record(it, "Ingreso", 0.1) }
        assertEquals(1.0, FinancialScope.summarize(records).income, 0.0)
    }

    @Test
    fun `records of an unknown type count in neither total`() {
        val summary = FinancialScope.summarize(listOf(record(1, "Otro", 50.0)))
        assertEquals(0.0, summary.income, 0.0)
        assertEquals(0.0, summary.expenses, 0.0)
    }

    @Test
    fun `an amount must be positive with at most two decimals`() {
        assertTrue(FinancialScope.isValidAmount(12.5))
        assertTrue(FinancialScope.isValidAmount(0.01))
        assertFalse(FinancialScope.isValidAmount(0.0))
        assertFalse(FinancialScope.isValidAmount(-3.0))
        assertFalse(FinancialScope.isValidAmount(1.234))
        assertFalse(FinancialScope.isValidAmount(Double.NaN))
    }

    @Test
    fun `typed amounts accept a decimal comma`() {
        assertEquals(12.5, parseAmount("12,5")!!, 0.0)
        assertEquals(1200.0, parseAmount(" 1200 ")!!, 0.0)
        assertNull(parseAmount("abc"))
        assertNull(parseAmount(""))
    }

    @Test
    fun `amounts are shown as soles with thousands separators`() {
        assertEquals("S/ 1,250.50", formatSoles(1250.5, Locale.US))
        assertEquals("S/ 0.00", formatSoles(0.0, Locale.US))
    }

    @Test
    fun `the form only shows amount errors after a save attempt`() {
        assertFalse(FinancialFormUiState(amount = "").amountInvalid)
        assertTrue(FinancialFormUiState(amount = "", showErrors = true).amountInvalid)
        assertFalse(FinancialFormUiState(amount = "15,50", showErrors = true).amountInvalid)
        assertTrue(FinancialFormUiState(amount = "15.555", showErrors = true).amountInvalid)
    }
}

class FinancialRepositoryTest {
    private val api = mockk<FinancialApi>()
    private val dao = mockk<FinancialDao>(relaxed = true)

    private fun repository(userId: Int = 10): FinancialRepositoryImpl {
        val sessionStore = mockk<SessionStore>()
        every { sessionStore.state } returns MutableStateFlow<SessionState>(SessionState.SignedIn(UserSession(userId, "u", "U", UserRole.Rancher, "t")))
        return FinancialRepositoryImpl(api, dao, sessionStore)
    }

    private fun dto(id: Int, ownerId: Int) = FinancialRecordDto(id, ownerId, "Ingreso", "Alimento", 5.0, "2026-10-01", "")

    @Test
    fun `refresh caches only the signed in rancher's records`() = runTest {
        coEvery { api.getRecords() } returns listOf(dto(1, 10), dto(2, 11))
        val cached = slot<List<FinancialRecordEntity>>()
        coEvery { dao.replaceAll(capture(cached)) } returns Unit

        assertEquals(AppResult.Success(Unit), repository().refresh())

        assertEquals(listOf(1), cached.captured.map { it.id })
    }

    @Test
    fun `a failed refresh keeps the cache`() = runTest {
        coEvery { api.getRecords() } throws IOException("offline")

        assertEquals(AppResult.Failure(AppError.Network), repository().refresh())
        coVerify(exactly = 0) { dao.replaceAll(any()) }
    }

    @Test
    fun `creating sends the owner and caches the answer`() = runTest {
        val sent = slot<FinancialRecordDto>()
        coEvery { api.create(capture(sent)) } returns dto(9, 10)

        val result = repository().create(FinancialRecordDraft(10, "Egreso", "Alimento", 5.0, "2026-10-01", "x"))

        assertTrue(result is AppResult.Success)
        assertEquals(10, sent.captured.ownerId)
        assertEquals("Egreso", sent.captured.type)
        coVerify { dao.upsert(match { it.id == 9 }) }
    }

    @Test
    fun `deleting removes the row only after the server agrees`() = runTest {
        coEvery { api.delete(4) } throws IOException("offline")
        repository().delete(4)
        coVerify(exactly = 0) { dao.delete(any()) }

        coEvery { api.delete(4) } returns Unit
        repository().delete(4)
        coVerify { dao.delete(4) }
    }
}
