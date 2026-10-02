package com.anitec.platform.activities.infrastructure

import com.anitec.platform.activities.domain.ActivitiesRepository
import com.anitec.platform.activities.infrastructure.remote.ActivitiesApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ActivitiesBindingsModule {
    @Binds
    abstract fun bindActivitiesRepository(impl: ActivitiesRepositoryImpl): ActivitiesRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ActivitiesApiModule {
    @Provides
    @Singleton
    fun provideActivitiesApi(retrofit: Retrofit): ActivitiesApi = retrofit.create(ActivitiesApi::class.java)
}
