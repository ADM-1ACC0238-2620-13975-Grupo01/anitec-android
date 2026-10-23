package com.anitec.platform.iam

import com.anitec.platform.core.network.NetworkModule
import com.anitec.platform.iam.infrastructure.remote.AuthApi
import com.anitec.platform.iam.infrastructure.remote.SignUpRequestDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Checks the exact JSON the app sends to POST authentication/sign-up. */
class SignUpRequestTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AuthApi
    private val json = NetworkModule.provideJson()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApi::class.java)
        server.enqueue(MockResponse.Builder().code(200).body("""{"message":"ok"}""").build())
    }

    @After
    fun tearDown() = runCatching { server.close() }.let { }

    private fun sentBody(): JsonObject = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject

    @Test
    fun `the e-mail is sent when provided`() = runTest {
        api.signUp(SignUpRequestDto("demo", "secret", "Demo User", "Rancher", "ana@example.com"))

        val body = sentBody()
        assertEquals("ana@example.com", body.getValue("email").jsonPrimitive.content)
        assertEquals("Rancher", body.getValue("role").jsonPrimitive.content)
    }

    @Test
    fun `the e-mail key is omitted when there is none so older servers are unaffected`() = runTest {
        api.signUp(SignUpRequestDto("demo", "secret", "Demo User", "Veterinarian", null))

        assertFalse(sentBody().containsKey("email"))
    }
}

/** Replies seen from the real backend must not break the registration. */
class SignUpResponseTest {

    private fun apiReplying(body: String): Pair<MockWebServer, AuthApi> {
        val server = MockWebServer().also { it.start() }
        server.enqueue(MockResponse.Builder().code(200).body(body).build())
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(NetworkModule.provideJson().asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApi::class.java)
        return server to api
    }

    private val request = SignUpRequestDto("demo", "secret", "Demo User", "Rancher", null)

    @Test
    fun `a message that is an object is accepted`() = runTest {
        // Exactly what the backend returns while its localization resources are not found.
        val (server, api) = apiReplying(
            """{"message":{"name":"UserCreatedSuccessfully","value":"UserCreatedSuccessfully","resourceNotFound":true,"searchedLocation":"x"}}""",
        )
        api.signUp(request)
        server.close()
    }

    @Test
    fun `a message that is a plain string is accepted`() = runTest {
        val (server, api) = apiReplying("""{"message":"User created successfully."}""")
        api.signUp(request)
        server.close()
    }

    @Test
    fun `an empty reply is accepted`() = runTest {
        val (server, api) = apiReplying("{}")
        api.signUp(request)
        server.close()
    }
}
