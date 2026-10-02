package com.anitec.platform.livestock.domain

import com.anitec.platform.core.session.UserRole

/**
 * Which herds a user may see. The API returns every herd, so the client decides (as the web app does):
 * a rancher sees the herds they own; a veterinarian sees the herds of their linked clients plus herds
 * that name them as veterinarian.
 */
object LivestockScope {
    fun visibleHerds(role: UserRole, userId: Int, clientRancherIds: Set<Int>, herds: List<Herd>): List<Herd> =
        when (role) {
            UserRole.Rancher -> herds.filter { it.ownerId == userId }
            UserRole.Veterinarian -> herds.filter { it.ownerId in clientRancherIds || it.veterinarianId == userId }
        }

    fun visibleCorrals(corrals: List<Corral>, herds: List<Herd>): List<Corral> {
        val herdIds = herds.map { it.id }.toSet()
        return corrals.filter { it.herdId in herdIds }
    }

    fun visibleAnimals(animals: List<Animal>, herds: List<Herd>): List<Animal> {
        val herdIds = herds.map { it.id }.toSet()
        return animals.filter { it.herdId in herdIds }
    }
}

/** Placement rules the backend does not enforce yet: a corral must exist and belong to the animal's herd. */
object AnimalPlacementPolicy {
    const val MIN_BATCH = 1
    const val MAX_BATCH = 500

    fun isValidPlacement(herdId: Int, corralId: Int?, corrals: List<Corral>): Boolean {
        if (corralId == null) return false
        return corrals.any { it.id == corralId && it.herdId == herdId }
    }

    fun isValidBatchSize(quantity: Int): Boolean = quantity in MIN_BATCH..MAX_BATCH
}
