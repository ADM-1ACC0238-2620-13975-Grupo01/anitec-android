package com.anitec.platform.consent.application

import com.anitec.platform.consent.domain.ConsentRepository
import com.anitec.platform.consent.domain.VeterinarianLink
import com.anitec.platform.core.common.AppResult
import javax.inject.Inject

/** Downloads the veterinarians' requests and links addressed to the signed-in rancher. */
class ListVeterinarianLinksUseCase @Inject constructor(private val repository: ConsentRepository) {
    suspend operator fun invoke(): AppResult<List<VeterinarianLink>> = repository.links()
}

/** Accepts the request with [linkId]: the veterinarian gets access to the rancher's data. */
class AcceptVeterinarianRequestUseCase @Inject constructor(private val repository: ConsentRepository) {
    suspend operator fun invoke(linkId: Int): AppResult<Unit> = repository.accept(linkId)
}

/** Rejects the request with [linkId]: no access is granted. */
class RejectVeterinarianRequestUseCase @Inject constructor(private val repository: ConsentRepository) {
    suspend operator fun invoke(linkId: Int): AppResult<Unit> = repository.reject(linkId)
}

/** Revokes the active access of the veterinarian behind [linkId]. */
class RevokeVeterinarianAccessUseCase @Inject constructor(private val repository: ConsentRepository) {
    suspend operator fun invoke(linkId: Int): AppResult<Unit> = repository.revoke(linkId)
}
