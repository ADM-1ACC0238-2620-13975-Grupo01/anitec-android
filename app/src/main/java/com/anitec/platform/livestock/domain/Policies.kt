package com.anitec.platform.livestock.domain

import com.anitec.platform.core.session.UserRole

/**
 * Client-side visibility rules for livestock data.
 *
 * The API returns every herd/corral/animal, so the client decides (as the web app does):
 * a rancher sees herds they own; a veterinarian sees herds of linked clients plus herds
 * that name them as veterinarian. Corrals and animals are then restricted to those herds.
 */
object LivestockScope {
    /** Herds the signed-in [role]/[userId] is allowed to see. */
    fun visibleHerds(role: UserRole, userId: Int, clientRancherIds: Set<Int>, herds: List<Herd>): List<Herd> =
        when (role) {
            UserRole.Rancher -> herds.filter { it.ownerId == userId }
            UserRole.Veterinarian -> herds.filter { it.ownerId in clientRancherIds || it.veterinarianId == userId }
        }

    /** Corrals that belong to one of the already-filtered [herds]. */
    fun visibleCorrals(corrals: List<Corral>, herds: List<Herd>): List<Corral> {
        val herdIds = herds.map { it.id }.toSet()
        return corrals.filter { it.herdId in herdIds }
    }

    /** Animals that belong to one of the already-filtered [herds]. */
    fun visibleAnimals(animals: List<Animal>, herds: List<Herd>): List<Animal> {
        val herdIds = herds.map { it.id }.toSet()
        return animals.filter { it.herdId in herdIds }
    }
}

/**
 * Placement rules the backend does not enforce yet.
 * A corral must exist and belong to the animal's herd; batch register size is capped.
 */
object AnimalPlacementPolicy {
    /** Inclusive lower bound for batch animal registration. */
    const val MIN_BATCH = 1

    /** Inclusive upper bound for batch animal registration. */
    const val MAX_BATCH = 500

    /** True when [corralId] refers to a corral that belongs to [herdId]. */
    fun isValidPlacement(herdId: Int, corralId: Int?, corrals: List<Corral>): Boolean {
        if (corralId == null) return false
        return corrals.any { it.id == corralId && it.herdId == herdId }
    }

    /** True when [quantity] is within [MIN_BATCH]..[MAX_BATCH]. */
    fun isValidBatchSize(quantity: Int): Boolean = quantity in MIN_BATCH..MAX_BATCH
}
