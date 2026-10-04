package com.anitec.platform.core.network

import com.anitec.platform.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module that wires the shared HTTP stack for every API interface.
 *
 * Provides a lenient [Json], a singleton [OkHttpClient] (auth + optional debug logging,
 * long timeouts for the Render free tier), and a [Retrofit] bound to [BuildConfig.API_BASE_URL].
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Shared kotlinx.serialization config: ignore unknown keys and coerce defaults. */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * HTTP client used by Retrofit.
     * Always installs [AuthInterceptor]; in debug builds also logs method/URL/status (BASIC),
     * never headers or bodies, so tokens and passwords stay out of logcat.
     * Timeouts are 60s because the hosted backend can take about a minute to wake up.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC logs method, URL and status only: no headers or bodies, so no tokens or passwords.
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        // The hosted backend (Render free tier) can take about a minute to wake up.
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Retrofit root for `/api/v1/` endpoints; converters use the shared [Json]. */
    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
