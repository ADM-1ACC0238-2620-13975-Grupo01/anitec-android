package com.anitec.platform.core.network

import com.anitec.platform.core.session.SessionStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthInterceptorTest {

    private val sessionStore = mockk<SessionStore>(relaxed = true)
    private val interceptor = AuthInterceptor(sessionStore)

    private fun chain(code: Int, sent: MutableList<Request> = mutableListOf()): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns Request.Builder().url("http://localhost/api/v1/herds").build()
        every { chain.proceed(any()) } answers {
            val request = firstArg<Request>()
            sent += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test").build()
        }
        return chain
    }

    @Test
    fun `a request carries the token of the session`() {
        every { sessionStore.currentToken() } returns "abc"
        val sent = mutableListOf<Request>()

        interceptor.intercept(chain(200, sent))

        assertEquals("Bearer abc", sent.single().header("Authorization"))
    }

    @Test
    fun `a 401 with the current token ends the session`() {
        every { sessionStore.currentToken() } returns "abc"

        interceptor.intercept(chain(401))

        verify(exactly = 1) { sessionStore.onUnauthorized() }
    }

    @Test
    fun `a late 401 from an old session does not end the new one`() {
        every { sessionStore.currentToken() } returnsMany listOf("old-session", "new-session")

        interceptor.intercept(chain(401))

        verify(exactly = 0) { sessionStore.onUnauthorized() }
    }

    @Test
    fun `a 401 without a token, like a failed sign-in, ends nothing`() {
        every { sessionStore.currentToken() } returns null
        val sent = mutableListOf<Request>()

        interceptor.intercept(chain(401, sent))

        assertEquals(null, sent.single().header("Authorization"))
        verify(exactly = 0) { sessionStore.onUnauthorized() }
    }
}
