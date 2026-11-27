package com.anitec.platform.sync

// Test coverage marker: sync conflict scenarios (US-032).

import com.anitec.platform.R
import com.anitec.platform.activities.domain.ActivityDraft
import com.anitec.platform.activities.infrastructure.ActivitiesRepositoryImpl
import com.anitec.platform.activities.infrastructure.local.ActivitiesDao
import com.anitec.platform.activities.infrastructure.remote.ActivitiesApi
import com.anitec.platform.activities.infrastructure.remote.FarmActivityDto
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.common.FieldDifference
import com.anitec.platform.core.common.differencesBetween
import com.anitec.platform.core.common.explainingConflict
import com.anitec.platform.core.common.asConflict
import com.anitec.platform.core.common.ifMatchOf
import com.anitec.platform.core.common.messageRes
import com.anitec.platform.core.network.parseOutOfDate
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.financial.domain.FinancialRecordDraft
import com.anitec.platform.financial.infrastructure.FinancialRepositoryImpl
import com.anitec.platform.financial.infrastructure.local.FinancialDao
import com.anitec.platform.financial.infrastructure.remote.FinancialApi
import com.anitec.platform.financial.infrastructure.remote.FinancialRecordDto
import com.anitec.platform.livestock.domain.AnimalDraft
import com.anitec.platform.livestock.infrastructure.LivestockRepositoryImpl
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import com.anitec.platform.livestock.infrastructure.remote.AnimalDto
import com.anitec.platform.livestock.infrastructure.remote.LivestockApi
import com.anitec.platform.sanitary.domain.HealthEventDraft
import com.anitec.platform.sanitary.infrastructure.SanitaryRepositoryImpl
import com.anitec.platform.sanitary.infrastructure.local.SanitaryDao
import com.anitec.platform.sanitary.infrastructure.remote.HealthEventDto
import com.anitec.platform.sanitary.infrastructure.remote.SanitaryApi
import com.anitec.platform.veterinary.infrastructure.local.VeterinaryDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the API answers to an edit based on an old version: 412 with the version it holds and its copy. */
private fun staleError(currentVersion: Int, current: String) = retrofit2.HttpException(
    retrofit2.Response.error<Any>(
        412,
        okhttp3.ResponseBody.create(
            null,
            """{"message":"The record was changed after the version you based your change on.","currentVersion":$currentVersion,"current":$current}""",
        ),
    ),
)

class ConflictHelpersTest {

    @Test
    fun `the If-Match value is the quoted version, or nothing when the version is unknown`() {
        assertEquals("\"3\"", ifMatchOf(3))
        assertNull(ifMatchOf(null))
    }

    @Test
    fun `only the labelled fields that differ are listed, blank and missing counting as the same`() {
        val mine = buildJsonObject {
            put("id", 1); put("version", 1); put("title", "Vaccination"); put("priority", "Alta"); put("status", "")
            put("type", "Sanitario"); put("nextDueDate", JsonNull)
        }
        val server = buildJsonObject {
            put("id", 1); put("version", 2); put("title", "Vaccination"); put("priority", "Baja"); put("type", "Sanitario")
        }

        val differences = differencesBetween(mine, server)

        assertEquals(listOf(FieldDifference(R.string.field_priority, "Alta", "Baja")), differences)
    }

    @Test
    fun `a field that is empty on one side and filled on the other is a difference`() {
        val mine = buildJsonObject { put("description", "") }
        val server = buildJsonObject { put("description", "Cojera") }

        assertEquals(listOf(FieldDifference(R.string.field_description, "—", "Cojera")), differencesBetween(mine, server))
    }

    @Test
    fun `fields the user does not edit never appear`() {
        val mine = buildJsonObject { put("animalId", 1); put("imageUrl", "a.jpg"); put("ownerId", 2) }
        val server = buildJsonObject { put("animalId", 9); put("imageUrl", "b.jpg"); put("ownerId", 3) }

        assertTrue(differencesBetween(mine, server).isEmpty())
    }

    @Test
    fun `the 412 body gives the current version and the server copy`() {
        val error = parseOutOfDate("""{"message":"x","currentVersion":4,"current":{"id":7,"title":"T"}}""")

        assertEquals(4, error.currentVersion)
        assertEquals("""{"id":7,"title":"T"}""", error.serverCopy)
    }

    @Test
    fun `a 412 without a readable body still says it is out of date`() {
        assertEquals(AppError.OutOfDate(0, null), parseOutOfDate(null))
        assertEquals(AppError.OutOfDate(0, null), parseOutOfDate("not json"))
    }

    @Test
    fun `an out of date failure becomes a conflict with its message, and nothing else does`() {
        val error = AppError.OutOfDate(5, "{}", listOf(FieldDifference(R.string.field_title, "a", "b")))

        assertEquals(5, error.asConflict()?.serverVersion)
        assertEquals(1, error.asConflict()?.differences?.size)
        assertEquals(R.string.error_out_of_date, error.messageRes())
        assertNull(AppError.Network.asConflict())
        assertNull(AppError.Conflict().asConflict())
    }

    @Test
    fun `explaining a conflict keeps the server copy and lists what differs`() = runTest {
        val mine = FinancialRecordDto(id = 3, ownerId = 10, type = "Ingreso", amount = 50.0, date = "2026-10-01", description = "Mine", version = 1)
        val server = FinancialRecordDto(id = 3, ownerId = 10, type = "Ingreso", amount = 80.0, date = "2026-10-01", description = "Mine", version = 2)
        var kept: FinancialRecordDto? = null
        val failure: AppResult<Unit> = AppResult.Failure(AppError.OutOfDate(2, """{"id":3,"ownerId":10,"type":"Ingreso","amount":80.0,"date":"2026-10-01","description":"Mine","version":2}"""))

        val result = failure.explainingConflict(FinancialRecordDto.serializer(), mine) { kept = it }

        assertEquals(server, kept)
        val error = (result as AppResult.Failure).error as AppError.OutOfDate
        assertEquals(listOf(FieldDifference(R.string.field_amount, "50.0", "80.0")), error.differences)
        assertEquals(2, error.currentVersion)
    }

    @Test
    fun `other results pass through and a copy that cannot be read is not cached`() = runTest {
        var kept = false
        val dto = FinancialRecordDto(id = 3, ownerId = 10)

        val ok = AppResult.Success(1).explainingConflict(FinancialRecordDto.serializer(), dto) { kept = true }
        val network: AppResult<Int> = AppResult.Failure(AppError.Network)
        val sameNetwork = network.explainingConflict(FinancialRecordDto.serializer(), dto) { kept = true }
        val garbled: AppResult<Int> = AppResult.Failure(AppError.OutOfDate(2, "not json"))
        val sameGarbled = garbled.explainingConflict(FinancialRecordDto.serializer(), dto) { kept = true }

        assertEquals(AppResult.Success(1), ok)
        assertEquals(network, sameNetwork)
        assertEquals(garbled, sameGarbled)
        assertEquals(false, kept)
    }
}

class EditsAgainstTheServerVersionTest {
    private val outbox = mockk<com.anitec.platform.core.outbox.OutboxQueue>(relaxed = true)

    private val sessionStore = mockk<SessionStore>().also {
        every { it.state } returns MutableStateFlow<SessionState>(SessionState.SignedIn(UserSession(10, "u", "U", UserRole.Rancher, "t")))
    }

    // ---------- Health events ----------

    private val sanitaryApi = mockk<SanitaryApi>()
    private val sanitaryDao = mockk<SanitaryDao>(relaxed = true)
    private val sanitary = SanitaryRepositoryImpl(sanitaryApi, sanitaryDao, mockk<LivestockDao>(), outbox)

    private fun healthDraft(description: String, baseVersion: Int?) = HealthEventDraft(
        animalId = 4, type = "Vacuna", date = "2026-10-02", description = description, veterinarian = "Dra. Ana",
        diagnosis = "", treatment = "", prescription = "", followUp = "", nextDueDate = null, baseVersion = baseVersion,
    )

    @Test
    fun `an edit is sent against the version it started from and the new version is cached`() = runTest {
        coEvery { sanitaryApi.updateEvent(5, any(), "\"2\"") } returns HealthEventDto(id = 5, animalId = 4, type = "Vacuna", date = "2026-10-02", description = "Mine", version = 3)

        val result = sanitary.update(5, healthDraft("Mine", baseVersion = 2)) as AppResult.Success

        assertEquals(3, result.value.version)
        coVerify { sanitaryDao.upsert(match { it.id == 5 && it.version == 3 }) }
    }

    @Test
    fun `an edit without a known version sends no If-Match`() = runTest {
        coEvery { sanitaryApi.updateEvent(5, any(), null) } returns HealthEventDto(id = 5, animalId = 4, type = "Vacuna", date = "2026-10-02", version = 2)

        sanitary.update(5, healthDraft("Mine", baseVersion = null))

        coVerify { sanitaryApi.updateEvent(5, any(), null) }
    }

    @Test
    fun `a refused edit keeps the server copy in the cache and reports what differs`() = runTest {
        coEvery { sanitaryApi.updateEvent(5, any(), "\"1\"") } throws staleError(
            2, """{"id":5,"animalId":4,"type":"Vacuna","date":"2026-10-02","description":"From the other device","veterinarian":"Dra. Ana","diagnosis":"","treatment":"","prescription":"","followUp":"","nextDueDate":null,"version":2}""",
        )

        val result = sanitary.update(5, healthDraft("From this device", baseVersion = 1))

        val error = (result as AppResult.Failure).error as AppError.OutOfDate
        assertEquals(2, error.currentVersion)
        assertEquals(listOf(FieldDifference(R.string.field_description, "From this device", "From the other device")), error.differences)
        coVerify { sanitaryDao.upsert(match { it.id == 5 && it.version == 2 && it.description == "From the other device" }) }
    }

    @Test
    fun `keeping my change is the same edit sent again against the version the server reported`() = runTest {
        coEvery { sanitaryApi.updateEvent(5, any(), "\"1\"") } throws staleError(
            2, """{"id":5,"animalId":4,"type":"Vacuna","date":"2026-10-02","description":"Other","version":2}""",
        )
        coEvery { sanitaryApi.updateEvent(5, any(), "\"2\"") } returns HealthEventDto(id = 5, animalId = 4, type = "Vacuna", date = "2026-10-02", description = "Mine", version = 3)

        val refused = sanitary.update(5, healthDraft("Mine", baseVersion = 1))
        val serverVersion = ((refused as AppResult.Failure).error as AppError.OutOfDate).currentVersion
        val resolved = sanitary.update(5, healthDraft("Mine", baseVersion = serverVersion))

        assertEquals("Mine", (resolved as AppResult.Success).value.description)
        assertEquals(3, resolved.value.version)
    }

    // ---------- Financial records ----------

    private val financialApi = mockk<FinancialApi>()
    private val financialDao = mockk<FinancialDao>(relaxed = true)
    private val financial = FinancialRepositoryImpl(financialApi, financialDao, sessionStore, outbox)

    @Test
    fun `a refused financial edit keeps the server copy and lists the changed amount`() = runTest {
        coEvery { financialApi.update(3, any(), "\"1\"") } throws staleError(
            2, """{"id":3,"ownerId":10,"type":"Ingreso","category":"Venta","amount":80.0,"date":"2026-10-01","description":"Milk","version":2}""",
        )

        val result = financial.update(3, FinancialRecordDraft(10, "Ingreso", "Venta", 50.0, "2026-10-01", "Milk", baseVersion = 1))

        val error = (result as AppResult.Failure).error as AppError.OutOfDate
        assertEquals(listOf(FieldDifference(R.string.field_amount, "50.0", "80.0")), error.differences)
        coVerify { financialDao.upsert(match { it.id == 3 && it.amount == 80.0 && it.version == 2 }) }
    }

    // ---------- Activities ----------

    private val activitiesApi = mockk<ActivitiesApi>()
    private val activitiesDao = mockk<ActivitiesDao>(relaxed = true)

    private fun activities() = ActivitiesRepositoryImpl(activitiesApi, activitiesDao, mockk<VeterinaryDao>(), sessionStore, outbox)

    @Test
    fun `a refused activity edit keeps the server copy and lists the changed priority`() = runTest {
        coEvery { activitiesApi.update(9, any(), "\"1\"") } throws staleError(
            2, """{"id":9,"ownerId":10,"veterinarianId":null,"title":"Dip","type":"Sanitario","date":"2026-10-05","priority":"Baja","status":"Pendiente","version":2}""",
        )

        val result = activities().update(9, ActivityDraft(10, null, "Dip", "Sanitario", "2026-10-05", "Alta", "Pendiente", baseVersion = 1))

        val error = (result as AppResult.Failure).error as AppError.OutOfDate
        assertEquals(listOf(FieldDifference(R.string.field_priority, "Alta", "Baja")), error.differences)
        coVerify { activitiesDao.upsert(match { it.id == 9 && it.priority == "Baja" && it.version == 2 }) }
    }

    // ---------- Animals ----------

    private val livestockApi = mockk<LivestockApi>()
    private val livestockDao = mockk<LivestockDao>(relaxed = true)

    @Test
    fun `a refused animal edit keeps the server copy and lists the changed weight`() = runTest {
        coEvery { livestockApi.updateAnimal(8, any(), "\"1\"") } throws staleError(
            2, """{"id":8,"tag":"BOV-1","name":"Lola","species":"Bovino","breed":"B","gender":"Hembra","birthDate":null,"weight":450.0,"status":"Saludable","herdId":1,"corralId":null,"version":2}""",
        )
        val repository = LivestockRepositoryImpl(livestockApi, mockk(), livestockDao, sessionStore, outbox)
        val draft = AnimalDraft("BOV-1", "Lola", "Bovino", "B", "Hembra", null, 420.0, "Saludable", 1, null, null, null, null, baseVersion = 1)

        val result = repository.updateAnimal(8, draft)

        val error = (result as AppResult.Failure).error as AppError.OutOfDate
        assertEquals(listOf(FieldDifference(R.string.field_weight, "420.0", "450.0")), error.differences)
        coVerify { livestockDao.upsertAnimal(match { it.id == 8 && it.weight == 450.0 && it.version == 2 }) }
        assertNotNull(error.asConflict())
    }

    @Test
    fun `an edit that fails for another reason is not turned into a conflict`() = runTest {
        coEvery { sanitaryApi.updateEvent(5, any(), any()) } throws java.io.IOException("offline")

        val result = sanitary.update(5, healthDraft("Mine", baseVersion = 1))

        assertEquals(AppResult.Failure(AppError.Network), result)
        coVerify(exactly = 0) { sanitaryDao.upsert(any()) }
    }
}
