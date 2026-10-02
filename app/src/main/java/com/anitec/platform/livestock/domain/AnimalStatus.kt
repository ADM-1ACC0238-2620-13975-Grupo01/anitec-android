package com.anitec.platform.livestock.domain

/**
 * Health/commercial status of an animal. [apiValue] is what the web client sends; [aliases] are the
 * English values found in the backend seed data, so both render correctly.
 */
enum class AnimalStatus(val apiValue: String, private val aliases: Set<String> = emptySet()) {
    Healthy("Saludable", setOf("healthy", "sano")),
    Observation("Observacion", setOf("observation")),
    InTreatment("En tratamiento", setOf("in treatment", "treatment", "sick")),
    Sold("Vendido", setOf("sold")),
    Unknown("");

    /** True for animals that need attention (not healthy and not sold). */
    val needsAttention: Boolean get() = this == Observation || this == InTreatment

    companion object {
        /** Statuses a rancher can choose from, in the order the web shows them. */
        val selectable: List<AnimalStatus> = listOf(Healthy, Observation, InTreatment, Sold)

        fun fromApi(value: String?): AnimalStatus {
            val normalized = value?.trim()?.lowercase().orEmpty()
            if (normalized.isEmpty()) return Unknown
            return entries.firstOrNull { it != Unknown && (it.apiValue.lowercase() == normalized || normalized in it.aliases) }
                ?: Unknown
        }
    }
}
