package com.anitec.platform.ui

import android.content.Context
import androidx.room.Room
import com.anitec.platform.activities.infrastructure.local.ActivitiesDao
import com.anitec.platform.core.database.AppDatabase
import com.anitec.platform.core.database.DatabaseModule
import com.anitec.platform.core.network.AuthInterceptor
import com.anitec.platform.core.network.NetworkModule
import com.anitec.platform.core.outbox.OutboxDao
import com.anitec.platform.devices.infrastructure.local.DevicesDao
import com.anitec.platform.financial.infrastructure.local.FinancialDao
import com.anitec.platform.integration.FakeBackend
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import com.anitec.platform.sanitary.infrastructure.local.SanitaryDao
import com.anitec.platform.veterinary.infrastructure.local.VeterinaryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.serialization.json.Json
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

/** The fake server every UI test talks to; each test installs its own [FakeBackend] as the dispatcher. */
object UiTestServer {
    val server: MockWebServer by lazy { MockWebServer().apply { start() } }
}

/** The app talks to the fake server instead of the hosted API; everything else in the network setup is the real one. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NetworkModule::class])
object TestNetworkModule {
    @Provides @Singleton fun provideJson(): Json = NetworkModule.provideJson()

    @Provides @Singleton fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(authInterceptor).build()

    @Provides @Singleton fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(UiTestServer.server.url("/api/v1/"))
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}

/** An empty database in memory for each run, so no test sees what another cached. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides @Singleton fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @Provides fun provideLivestockDao(database: AppDatabase): LivestockDao = database.livestockDao()
    @Provides fun provideSanitaryDao(database: AppDatabase): SanitaryDao = database.sanitaryDao()
    @Provides fun provideVeterinaryDao(database: AppDatabase): VeterinaryDao = database.veterinaryDao()
    @Provides fun provideActivitiesDao(database: AppDatabase): ActivitiesDao = database.activitiesDao()
    @Provides fun provideFinancialDao(database: AppDatabase): FinancialDao = database.financialDao()
    @Provides fun provideDevicesDao(database: AppDatabase): DevicesDao = database.devicesDao()
    @Provides fun provideOutboxDao(database: AppDatabase): OutboxDao = database.outboxDao()
}
