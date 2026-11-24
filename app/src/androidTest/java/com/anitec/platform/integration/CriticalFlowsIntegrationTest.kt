package com.anitec.platform.integration

// Test coverage marker: integration scenarios of the critical flows (TS-014).

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anitec.platform.activities.infrastructure.remote.ActivitiesApi
import com.anitec.platform.consent.infrastructure.ConsentRepositoryImpl
import com.anitec.platform.consent.infrastructure.remote.ConsentApi
import com.anitec.platform.consent.domain.LinkStatus
import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import com.anitec.platform.core.database.AppDatabase
import com.anitec.platform.core.network.NetworkModule
import com.anitec.platform.core.outbox.OutboxQueue
import com.anitec.platform.core.outbox.OutboxScheduler
import com.anitec.platform.core.session.SessionState
import com.anitec.platform.core.session.SessionStore
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.core.session.UserSession
import com.anitec.platform.financial.domain.FinancialRecordDraft
import com.anitec.platform.financial.infrastructure.FinancialRepositoryImpl
import com.anitec.platform.financial.infrastructure.remote.FinancialApi
import com.anitec.platform.livestock.infrastructure.remote.LivestockApi
import com.anitec.platform.sanitary.infrastructure.remote.SanitaryApi
import com.anitec.platform.sync.OutboxProcessor
import com.anitec.platform.sync.SyncOutcome
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The repositories, the outbox and the cache working together over the real Room and the real Retrofit, against a fake
 * server that follows the rules of the API. They cover what the unit tests with doubles cannot: that what is queued,
 * stored and sent is the same thing, that a retry reaches the server with the same key, and that errors leave the cache
 * as it was.
 */
@RunWith(AndroidJUnit4::class)
class CriticalFlowsIntegrationTest {

    private val backend = FakeBackend()
    private val server = MockWebServer()
    private val loseNextAnswer = AtomicBoolean(false)

    private lateinit var database: AppDatabase
    private lateinit var retrofit: Retrofit
    private lateinit var repository: FinancialRepositoryImpl
    private lateinit var processor: OutboxProcessor
    private lateinit var queue: OutboxQueue

    @Before
    fun setUp() {
        server.dispatcher = backend
        server.start()
        val json = NetworkModule.provideJson()
        // The answer of a request can be lost after the server handled it, which is what makes a retry dangerous.
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val response = chain.proceed(chain.request())
            if (chain.request().method == "POST" && loseNextAnswer.getAndSet(false)) {
                response.close()
                throw IOException("the answer was lost")
            }
            response
        }).build()
        retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        queue = OutboxQueue(database.outboxDao(), json, object : OutboxScheduler { override fun schedule() = Unit })
        val sessionStore = mockk<SessionStore>()
        every { sessionStore.state } returns MutableStateFlow<SessionState>(SessionState.SignedIn(UserSession(10, "rancher", "Rancher", UserRole.Rancher, "token")))

        val financialApi = retrofit.create(FinancialApi::class.java)
        repository = FinancialRepositoryImpl(financialApi, database.financialDao(), sessionStore, queue)
        processor = OutboxProcessor(
            database.outboxDao(), retrofit.create(LivestockApi::class.java), database.livestockDao(), retrofit.create(SanitaryApi::class.java),
            database.sanitaryDao(), retrofit.create(ActivitiesApi::class.java), database.activitiesDao(), financialApi, database.financialDao(), json,
        )
        consent = ConsentRepositoryImpl(retrofit.create(ConsentApi::class.java), sessionStore)
    }

    private lateinit var consent: ConsentRepositoryImpl

    @After
    fun tearDown() {
        database.close()
        runCatching { server.close() }
    }

    private fun draft(description: String = "Milk", baseVersion: Int? = null) =
        FinancialRecordDraft(10, "Ingreso", "Venta de leche", 80.0, "2026-11-24", description, baseVersion)

    private fun cached() = runBlocking { database.financialDao().observeRecords().first() }

    private fun postKeys() = backend.received.filter { it.method == "POST" && it.url.encodedPath.endsWith("financial-records") }.map { it.headers["Idempotency-Key"] }

    // ---------- Create and cache ----------

    @Test
    fun anOnlineCreate_sendsItsKey_andCachesTheRecordWithTheVersionOfTheServer() = runBlocking {
        val result = repository.create(draft()) as AppResult.Success

        assertTrue(result.value.id > 0)
        assertEquals(1, result.value.version)
        assertEquals(1, backend.recordCount)
        val key = postKeys().single()
        assertTrue("the request must carry an Idempotency-Key", !key.isNullOrBlank())
        assertEquals(listOf(result.value.id), cached().map { it.id })
        assertEquals(0, database.outboxDao().count())
    }

    @Test
    fun aCreateWhoseAnswerIsLost_isQueuedWithTheSameKey_andTheServerEndsUpWithOneRecord() = runBlocking {
        loseNextAnswer.set(true)

        val result = repository.create(draft("Lost answer")) as AppResult.Success

        // The server saved it, the app did not hear back: it keeps the record locally and queues it.
        assertTrue("kept under a temporary id", result.value.id < 0)
        assertEquals(1, backend.recordCount)
        assertEquals(1, database.outboxDao().count())

        assertEquals(SyncOutcome.Done, processor.run())

        assertEquals("the retry must not create the record again", 1, backend.recordCount)
        val keys = postKeys()
        assertEquals(2, keys.size)
        assertEquals("both tries carry the same key", keys[0], keys[1])
        assertTrue(backend.received.last { it.method == "POST" }.headers["Idempotent-Replayed"] == null)
        val record = cached().single()
        assertTrue("the temporary record was replaced by the real one", record.id > 0)
        assertEquals("Lost answer", record.description)
        assertEquals(0, database.outboxDao().count())
    }

    @Test
    fun aChangeMadeWithoutConnection_waitsInTheQueueAndIsSentWhenTheServerIsBack() = runBlocking {
        server.close()
        val queued = (repository.create(draft("While offline")) as AppResult.Success).value

        assertTrue(queued.id < 0)
        assertEquals(1, database.outboxDao().count())
        assertEquals(SyncOutcome.Retry, processor.run())
        assertEquals("still waiting, not lost", 1, database.outboxDao().count())
        assertEquals(listOf("While offline"), cached().map { it.description })
    }

    // ---------- Edit conflicts ----------

    @Test
    fun anEditBasedOnAnOldVersion_keepsBothCopies_andCanBeResentAgainstTheNewVersion() = runBlocking {
        val id = (repository.create(draft("Original")) as AppResult.Success).value.id
        backend.changeElsewhere(id, "description", "From the other device")

        val refused = repository.update(id, draft("My edit", baseVersion = 1))

        val error = (refused as AppResult.Failure).error as AppError.OutOfDate
        assertEquals(2, error.currentVersion)
        assertTrue("it says what differs", error.differences.isNotEmpty())
        assertEquals("the server keeps its copy", "From the other device", backend.record(id)!!.getValue("description").jsonPrimitive.content)
        val cache = cached().single()
        assertEquals("the cache holds the server copy", "From the other device", cache.description)
        assertEquals(2, cache.version)

        val resolved = repository.update(id, draft("My edit", baseVersion = error.currentVersion)) as AppResult.Success

        assertEquals("My edit", resolved.value.description)
        assertEquals(3, resolved.value.version)
        assertEquals(3, backend.record(id)!!.getValue("version").jsonPrimitive.int)
        assertEquals("My edit", cached().single().description)
    }

    @Test
    fun anEditAgainstTheCurrentVersion_isAccepted_andRaisesTheVersion() = runBlocking {
        val id = (repository.create(draft("Original")) as AppResult.Success).value.id

        val result = repository.update(id, draft("Edited", baseVersion = 1)) as AppResult.Success

        assertEquals(2, result.value.version)
        assertEquals("\"1\"", backend.received.last { it.method == "PUT" }.headers["If-Match"])
    }

    // ---------- Errors leave the cache as it was ----------

    @Test
    fun aFailingServer_isReported_andTheCacheIsNotTouched() = runBlocking {
        repository.create(draft("Kept"))
        backend.failWith = 500

        val result = repository.refresh()

        assertTrue((result as AppResult.Failure).error is AppError.Server)
        assertEquals(listOf("Kept"), cached().map { it.description })
    }

    @Test
    fun aServerThatIsDown_isReportedAsNoConnection_andTheCacheIsNotTouched() = runBlocking {
        repository.create(draft("Kept"))
        server.close()

        val result = repository.refresh()

        assertEquals(AppResult.Failure(AppError.Network), result)
        assertEquals(listOf("Kept"), cached().map { it.description })
    }

    @Test
    fun aRefusedRequest_isNotQueuedAsIfItWereOffline() = runBlocking {
        backend.failWith = 400

        val result = repository.create(draft())

        assertTrue((result as AppResult.Failure).error is AppError.Validation)
        assertEquals(0, database.outboxDao().count())
        assertTrue(cached().isEmpty())
    }

    // ---------- Consent ----------

    @Test
    fun theRequestsOfVeterinarians_areMappedAndTheAnswersMoveThemBetweenStates() = runBlocking {
        val first = consent.links() as AppResult.Success
        assertEquals(listOf(LinkStatus.Pending, LinkStatus.Accepted), first.value.map { it.status })
        assertEquals("Vet 1", first.value.first().displayName)

        assertEquals(AppResult.Success(Unit), consent.accept(1))
        assertEquals(listOf(LinkStatus.Accepted, LinkStatus.Accepted), (consent.links() as AppResult.Success).value.map { it.status })

        assertEquals(AppResult.Success(Unit), consent.revoke(1))
        assertEquals(LinkStatus.Revoked, (consent.links() as AppResult.Success).value.first().status)
    }

    @Test
    fun answeringTwice_isAConflict_andRevokingWithoutAccessIsNotFound() = runBlocking {
        consent.accept(1)

        val again = consent.accept(1)
        val reject = consent.reject(1)
        backend.links[1] = "Rejected"
        val revoke = consent.revoke(1)

        assertTrue((again as AppResult.Failure).error is AppError.Conflict)
        assertTrue((reject as AppResult.Failure).error is AppError.Conflict)
        assertEquals(AppResult.Failure(AppError.NotFound), revoke)
        assertNotEquals(0, backend.received.size)
    }
}
