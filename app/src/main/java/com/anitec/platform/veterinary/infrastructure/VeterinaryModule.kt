package com.anitec.platform.veterinary.infrastructure

import com.anitec.platform.veterinary.domain.VeterinaryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class VeterinaryBindingsModule {
    @Binds
    abstract fun bindVeterinaryRepository(impl: VeterinaryRepositoryImpl): VeterinaryRepository
}
