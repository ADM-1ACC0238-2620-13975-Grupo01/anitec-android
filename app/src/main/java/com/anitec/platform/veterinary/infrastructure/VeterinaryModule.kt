package com.anitec.platform.veterinary.infrastructure

import com.anitec.platform.veterinary.domain.VeterinaryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds [VeterinaryRepository] to [VeterinaryRepositoryImpl]. Its API client is provided by the livestock
 * network setup, so only the binding lives here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VeterinaryBindingsModule {
    @Binds
    abstract fun bindVeterinaryRepository(impl: VeterinaryRepositoryImpl): VeterinaryRepository
}
