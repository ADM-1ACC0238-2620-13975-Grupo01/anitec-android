package com.anitec.platform.livestock.infrastructure

import com.anitec.platform.livestock.domain.AnimalImageUploader
import com.anitec.platform.livestock.domain.LivestockRepository
import com.anitec.platform.livestock.infrastructure.remote.LivestockApi
import com.anitec.platform.veterinary.infrastructure.remote.VeterinaryApi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/** Binds the livestock repository and the animal image uploader implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LivestockBindingsModule {
    @Binds
    abstract fun bindLivestockRepository(impl: LivestockRepositoryImpl): LivestockRepository

    @Binds
    abstract fun bindAnimalImageUploader(impl: AnimalImageUploaderImpl): AnimalImageUploader
}

/** Provides the Retrofit clients of the livestock and veterinary APIs. */
@Module
@InstallIn(SingletonComponent::class)
object LivestockApiModule {
    @Provides
    @Singleton
    fun provideLivestockApi(retrofit: Retrofit): LivestockApi = retrofit.create(LivestockApi::class.java)

    @Provides
    @Singleton
    fun provideVeterinaryApi(retrofit: Retrofit): VeterinaryApi = retrofit.create(VeterinaryApi::class.java)
}
