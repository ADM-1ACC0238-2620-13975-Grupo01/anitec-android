package com.anitec.platform.sanitary.infrastructure

import com.anitec.platform.sanitary.domain.SanitaryRepository
import com.anitec.platform.sanitary.infrastructure.remote.SanitaryApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SanitaryBindingsModule {
    @Binds
    abstract fun bindSanitaryRepository(impl: SanitaryRepositoryImpl): SanitaryRepository
}

@Module
@InstallIn(SingletonComponent::class)
object SanitaryApiModule {
    @Provides
    @Singleton
    fun provideSanitaryApi(retrofit: Retrofit): SanitaryApi = retrofit.create(SanitaryApi::class.java)
}
