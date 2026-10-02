package com.anitec.platform.core.network

import com.anitec.platform.core.common.AppError
import com.anitec.platform.core.common.AppResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET

@Serializable
private data class PingDto(val value: String)

private interface PingApi {
    @GET("ping")
    suspend fun ping(): PingDto
}

class SafeApiCallTest {

    private lateinit var server: MockWebServer
    private lateinit var api: PingApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PingApi::class.java)
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
    }

    private fun respond(code: Int, body: String = "") {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private suspend fun errorOf(): AppError {
        val result = safeApiCall { api.ping() }
        assertTrue("expected a failure but got $result", result is AppResult.Failure)
        return (result as AppResult.Failure).error
    }

    @Test
    fun `success returns the decoded body`() = runTest {
        respond(200, """{"value":"pong"}""")
        val result = safeApiCall { api.ping() }
        assertEquals(AppResult.Success(PingDto("pong")), result)
    }

    @Test
    fun `400 keeps the server message as a hint`() = runTest {
        respond(400, """{"message":"Corral not found."}""")
        assertEquals(AppError.Validation(listOf("Corral not found.")), errorOf())
    }

    @Test
    fun `400 collects the errors array of the animals controller`() = runTest {
        respond(400, """{"message":"One or more fields are invalid.","errors":["Code is required.","Name is required."]}""")
        assertEquals(
            AppError.Validation(listOf("One or more fields are invalid.", "Code is required.", "Name is required.")),
            errorOf(),
        )
    }

    @Test
    fun `400 collects the ASP NET validation dictionary`() = runTest {
        respond(400, """{"title":"One or more validation errors occurred.","errors":{"Username":["required"],"Password":["too short"]}}""")
        assertEquals(AppError.Validation(listOf("required", "too short")), errorOf())
    }

    @Test
    fun `ProblemDetails detail is kept`() = runTest {
        respond(409, """{"type":"x","title":"UsernameAlreadyTaken","status":409,"detail":"UsernameAlreadyTaken"}""")
        assertEquals(AppError.Conflict(listOf("UsernameAlreadyTaken")), errorOf())
    }

    @Test
    fun `401 maps to Unauthorized`() = runTest {
        respond(401, """{"title":"Unauthorized","status":401,"detail":"Missing or invalid token"}""")
        assertEquals(AppError.Unauthorized, errorOf())
    }

    @Test
    fun `403 maps to Forbidden`() = runTest {
        respond(403)
        assertEquals(AppError.Forbidden, errorOf())
    }

    @Test
    fun `404 with an empty body maps to NotFound`() = runTest {
        respond(404)
        assertEquals(AppError.NotFound, errorOf())
    }

    @Test
    fun `500 maps to Server`() = runTest {
        respond(500, """{"title":"Internal Server Error","status":500,"detail":"An error occurred."}""")
        assertEquals(AppError.Server(500), errorOf())
    }

    @Test
    fun `an unreachable server maps to Network`() = runTest {
        server.close()
        assertEquals(AppError.Network, errorOf())
    }

    @Test
    fun `malformed error bodies do not crash the parser`() {
        assertEquals(emptyList<String>(), parseErrorMessages("<html>bad gateway</html>"))
        assertEquals(emptyList<String>(), parseErrorMessages(null))
        assertEquals(emptyList<String>(), parseErrorMessages("[]"))
    }
}
