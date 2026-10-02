package com.anitec.platform.core.network

import com.anitec.platform.core.session.SessionStore
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale
import javax.inject.Inject

/**
 * Adds the Bearer token and the UI language to every request, and ends the session when the
 * server rejects the token. (The web client has no 401 handling; the app needs it because
 * tokens last 7 days and cannot be refreshed.)
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
