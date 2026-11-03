package com.anitec.platform.iam.infrastructure

import com.anitec.platform.iam.domain.AuthRepository
import com.anitec.platform.iam.infrastructure.remote.AuthApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds [AuthRepository] to [AuthRepositoryImpl]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class IamBindingsModule {
    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}

/** Creates the Retrofit client of the sign-in and sign-up endpoints. */
@Module
@InstallIn(SingletonComponent::class)
object IamApiModule {
    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
}
