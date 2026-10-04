package com.anitec.platform.core.network

import com.anitec.platform.core.session.SessionStore
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale
import javax.inject.Inject

/**
 * OkHttp interceptor that attaches credentials and locale to outbound API calls.
 *
 * - Always sets `Accept-Language` from the device locale so the backend can localize messages.
 * - When a session token exists, sets `Authorization: Bearer <token>`.
 * - If the server answers HTTP 401 on a request that carried a token, calls
 *   [SessionStore.onUnauthorized] so the app signs the user out.
 *
 * The web client has no 401 handling; the app needs it because tokens last 7 days and
 * cannot be refreshed. A 401 without a prior token (e.g. failed sign-in) is left alone.
 */
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionStore.currentToken()
        val request = chain.request().newBuilder()
            .header("Accept-Language", Locale.getDefault().language)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        val response = chain.proceed(request)
        if (response.code == 401 && token != null) sessionStore.onUnauthorized()
        return response
    }
}
