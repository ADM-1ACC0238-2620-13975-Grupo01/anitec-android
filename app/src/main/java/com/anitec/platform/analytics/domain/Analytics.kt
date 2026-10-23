package com.anitec.platform.analytics.domain

import com.anitec.platform.livestock.domain.Animal
import com.anitec.platform.livestock.domain.AnimalStatus
import com.anitec.platform.livestock.domain.Herd
import com.anitec.platform.sanitary.domain.HealthEvent
import com.anitec.platform.sanitary.domain.HealthTypes

/** Health record kinds shown in the "records by type" chart; everything else is [OTHER]. */
object RecordKinds {
    const val OTHER = "Otros"
    val all = listOf(HealthTypes.INCIDENT, HealthTypes.VACCINE, HealthTypes.CHECKUP, HealthTypes.TREATMENT, HealthTypes.DIAGNOSIS, OTHER)

    fun of(type: String): String = all.firstOrNull { it != OTHER && it.equals(type, ignoreCase = true) } ?: OTHER
}

data class HerdActivity(val herdId: Int, val herdName: String, val recordCount: Int)

/** Everything the analytics screen shows, computed from the user's own (already scoped) cache. */
data class AnalyticsSnapshot(
    val herdCount: Int = 0,
    val animalCount: Int = 0,
    val healthyCount: Int = 0,
    val observationCount: Int = 0,
    val treatmentCount: Int = 0,
    /** Animals in observation or in treatment. */
    val attentionCount: Int = 0,
    val recordCount: Int = 0,
    val followUpCount: Int = 0,
    /** Records per kind, in the order of [RecordKinds.all]. */
    val recordsByKind: List<Pair<String, Int>> = RecordKinds.all.map { it to 0 },
    val recordsByHerd: List<HerdActivity> = emptyList(),
) {
    val hasAnimals get() = animalCount > 0
    val hasRecords get() = recordCount > 0
}

object AnalyticsCalculator {
    fun compute(herds: List<Herd>, animals: List<Animal>, events: List<HealthEvent>): AnalyticsSnapshot {
        val herdIds = herds.map { it.id }.toSet()
        val scopedAnimals = animals.filter { it.herdId in herdIds }
        val animalHerd = scopedAnimals.associate { it.id to it.herdId }
        val scopedEvents = events.filter { it.animalId in animalHerd }
        val byKind = scopedEvents.groupingBy { RecordKinds.of(it.type) }.eachCount()
        val byHerd = scopedEvents.groupingBy { animalHerd.getValue(it.animalId) }.eachCount()
        val statuses = scopedAnimals.groupingBy { it.healthStatus }.eachCount()
        return AnalyticsSnapshot(
            herdCount = herds.size,
            animalCount = scopedAnimals.size,
            healthyCount = statuses[AnimalStatus.Healthy] ?: 0,
            observationCount = statuses[AnimalStatus.Observation] ?: 0,
            treatmentCount = statuses[AnimalStatus.InTreatment] ?: 0,
            attentionCount = scopedAnimals.count { it.healthStatus.needsAttention },
            recordCount = scopedEvents.size,
            followUpCount = scopedEvents.count { it.hasFollowUp },
            recordsByKind = RecordKinds.all.map { it to (byKind[it] ?: 0) },
            recordsByHerd = herds.map { HerdActivity(it.id, it.name, byHerd[it.id] ?: 0) },
        )
    }
}
