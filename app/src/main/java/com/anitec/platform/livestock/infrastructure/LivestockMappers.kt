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

/**
 * Pure mapping helpers between livestock layers:
 * remote DTOs ↔ domain models ↔ Room entities, plus drafts → write DTOs.
 *
 * Field names stay aligned with the API; no business rules live here.
 */

/** Remote API DTO → domain model (read path after sync). */
fun HerdDto.toDomain() = Herd(id, name, location, owner, ownerId, veterinarianId, mainType)

/** Remote API DTO → domain model (read path after sync). */
fun CorralDto.toDomain() = Corral(id, name, herdId)

/** Remote API DTO → domain model (read path after sync). */
fun AnimalDto.toDomain() = Animal(id, tag, name, species, breed, gender, birthDate, weight, status, herdId, corralId, source, ageRange, imageUrl)

/** Domain model → Room entity (cache write). */
fun Herd.toEntity() = HerdEntity(id, name, location, owner, ownerId, veterinarianId, mainType)

/** Domain model → Room entity (cache write). */
fun Corral.toEntity() = CorralEntity(id, name, herdId)

/** Domain model → Room entity (cache write). */
fun Animal.toEntity() = AnimalEntity(id, tag, name, species, breed, gender, birthDate, weight, status, herdId, corralId, source, ageRange, imageUrl)

/** Room entity → domain model (cache read). */
fun HerdEntity.toDomain() = Herd(id, name, location, owner, ownerId, veterinarianId, mainType)

/** Room entity → domain model (cache read). */
fun CorralEntity.toDomain() = Corral(id, name, herdId)

/** Room entity → domain model (cache read). */
fun AnimalEntity.toDomain() = Animal(id, tag, name, species, breed, gender, birthDate, weight, status, herdId, corralId, source, ageRange, imageUrl)

/**
 * Create/update draft → remote write DTO.
 * The id is assigned by the server on create, or taken from the path on update.
 */
fun HerdDraft.toDto() = HerdDto(name = name, location = location, owner = owner, ownerId = ownerId, veterinarianId = veterinarianId, mainType = mainType)

/** Create/update draft → remote write DTO. */
fun CorralDraft.toDto() = CorralDto(name = name, herdId = herdId)

/** Create/update draft → remote write DTO. */
fun AnimalDraft.toDto() = AnimalDto(
    tag = tag, name = name, species = species, breed = breed, gender = gender, birthDate = birthDate,
    weight = weight, status = status, herdId = herdId, corralId = corralId, source = source,
    ageRange = ageRange, imageUrl = imageUrl,
)

/** Batch-register draft → remote write DTO (`quantity` animals with shared fields). */
fun AnimalBatchDraft.toDto() = AnimalBatchDto(
    species = species, breed = breed, gender = gender, birthDate = birthDate, weight = weight,
    status = status, herdId = herdId, corralId = corralId, quantity = quantity, source = source,
    ageRange = ageRange, imageUrl = imageUrl,
)
