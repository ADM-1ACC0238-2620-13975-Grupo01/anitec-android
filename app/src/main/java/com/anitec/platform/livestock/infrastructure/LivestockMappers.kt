package com.anitec.platform.livestock.infrastructure

import com.anitec.platform.livestock.domain.Animal
import com.anitec.platform.livestock.domain.AnimalBatchDraft
import com.anitec.platform.livestock.domain.AnimalDraft
import com.anitec.platform.livestock.domain.Corral
import com.anitec.platform.livestock.domain.CorralDraft
import com.anitec.platform.livestock.domain.Herd
import com.anitec.platform.livestock.domain.HerdDraft
import com.anitec.platform.livestock.infrastructure.local.AnimalEntity
import com.anitec.platform.livestock.infrastructure.local.CorralEntity
import com.anitec.platform.livestock.infrastructure.local.HerdEntity
import com.anitec.platform.livestock.infrastructure.remote.AnimalBatchDto
import com.anitec.platform.livestock.infrastructure.remote.AnimalDto
import com.anitec.platform.livestock.infrastructure.remote.CorralDto
import com.anitec.platform.livestock.infrastructure.remote.HerdDto

// Remote -> domain
fun HerdDto.toDomain() = Herd(id, name, location, owner, ownerId, veterinarianId, mainType)
fun CorralDto.toDomain() = Corral(id, name, herdId)
fun AnimalDto.toDomain() = Animal(id, tag, name, species, breed, gender, birthDate, weight, status, herdId, corralId, source, ageRange, imageUrl)

// Domain -> local
fun Herd.toEntity() = HerdEntity(id, name, location, owner, ownerId, veterinarianId, mainType)
fun Corral.toEntity() = CorralEntity(id, name, herdId)
fun Animal.toEntity() = AnimalEntity(id, tag, name, species, breed, gender, birthDate, weight, status, herdId, corralId, source, ageRange, imageUrl)

// Local -> domain
fun HerdEntity.toDomain() = Herd(id, name, location, owner, ownerId, veterinarianId, mainType)
fun CorralEntity.toDomain() = Corral(id, name, herdId)
fun AnimalEntity.toDomain() = Animal(id, tag, name, species, breed, gender, birthDate, weight, status, herdId, corralId, source, ageRange, imageUrl)

// Drafts -> remote (the id is assigned by the server, or taken from the path on update)
fun HerdDraft.toDto() = HerdDto(name = name, location = location, owner = owner, ownerId = ownerId, veterinarianId = veterinarianId, mainType = mainType)
fun CorralDraft.toDto() = CorralDto(name = name, herdId = herdId)
fun AnimalDraft.toDto() = AnimalDto(
    tag = tag, name = name, species = species, breed = breed, gender = gender, birthDate = birthDate,
    weight = weight, status = status, herdId = herdId, corralId = corralId, source = source,
    ageRange = ageRange, imageUrl = imageUrl,
)
fun AnimalBatchDraft.toDto() = AnimalBatchDto(
    species = species, breed = breed, gender = gender, birthDate = birthDate, weight = weight,
    status = status, herdId = herdId, corralId = corralId, quantity = quantity, source = source,
    ageRange = ageRange, imageUrl = imageUrl,
)
