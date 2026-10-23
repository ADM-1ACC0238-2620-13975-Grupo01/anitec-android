package com.anitec.platform.analytics

import com.anitec.platform.analytics.domain.AnalyticsCalculator
import com.anitec.platform.analytics.domain.RecordKinds
import com.anitec.platform.core.designsystem.component.ChartPoint
import com.anitec.platform.core.designsystem.component.spokenSummary
import com.anitec.platform.livestock.domain.Animal
import com.anitec.platform.livestock.domain.Herd
import com.anitec.platform.sanitary.domain.HealthEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun herd(id: Int, name: String = "Herd $id") = Herd(id, name, "Loc", "Owner", 10, null, "Mixto")

private fun animal(id: Int, herdId: Int, status: String) =
    Animal(id, "T$id", "A$id", "Bovino", "B", "Hembra", null, 1.0, status, herdId, null, null, null, null)

private fun event(id: Int, animalId: Int, type: String, next: String? = null) =
    HealthEvent(id, animalId, type, "2026-09-01", "d", "Dr", "", "", "", "", next)

class AnalyticsCalculatorTest {
    private val herds = listOf(herd(1), herd(2))
    private val animals = listOf(
        animal(1, 1, "Saludable"), animal(2, 1, "Observacion"), animal(3, 2, "En tratamiento"),
        animal(4, 2, "Vendido"), animal(5, 99, "Saludable"),
    )

    @Test
    fun `animals are counted by health status and sold ones need no attention`() {
        val snapshot = AnalyticsCalculator.compute(herds, animals, emptyList())

        assertEquals(4, snapshot.animalCount)
        assertEquals(1, snapshot.healthyCount)
        assertEquals(1, snapshot.observationCount)
        assertEquals(1, snapshot.treatmentCount)
        assertEquals(2, snapshot.attentionCount)
    }

    @Test
    fun `animals of a herd the user cannot see are left out`() {
        assertEquals(4, AnalyticsCalculator.compute(herds, animals, emptyList()).animalCount)
        assertEquals(2, AnalyticsCalculator.compute(listOf(herd(1)), animals, emptyList()).animalCount)
    }

    @Test
    fun `records are grouped by kind with unknown ones under other`() {
        val events = listOf(
            event(1, 1, "Vacuna"), event(2, 1, "vacuna"), event(3, 2, "Revision", next = "2026-10-20"),
            event(4, 3, "Cirugia"), event(5, 99, "Vacuna"),
        )
        val snapshot = AnalyticsCalculator.compute(herds, animals, events)

        assertEquals(4, snapshot.recordCount)
        assertEquals(1, snapshot.followUpCount)
        val byKind = snapshot.recordsByKind.toMap()
        assertEquals(2, byKind["Vacuna"])
        assertEquals(1, byKind["Revision"])
        assertEquals(1, byKind[RecordKinds.OTHER])
        assertEquals(0, byKind["Incidencia"])
        assertEquals(RecordKinds.all, snapshot.recordsByKind.map { it.first })
    }

    @Test
    fun `records are counted per herd including herds without records`() {
        val events = listOf(event(1, 1, "Vacuna"), event(2, 2, "Vacuna"), event(3, 3, "Vacuna"))
        val byHerd = AnalyticsCalculator.compute(herds + herd(3), animals, events).recordsByHerd

        assertEquals(listOf(2, 1, 0), byHerd.map { it.recordCount })
    }

    @Test
    fun `an empty cache gives a zeroed snapshot`() {
        val snapshot = AnalyticsCalculator.compute(emptyList(), emptyList(), emptyList())

        assertFalse(snapshot.hasAnimals)
        assertFalse(snapshot.hasRecords)
        assertTrue(snapshot.recordsByKind.all { it.second == 0 })
    }

    @Test
    fun `charts are summarized in words for screen readers`() {
        assertEquals("Healthy 5, Sick 2", listOf(ChartPoint("Healthy", 5), ChartPoint("Sick", 2)).spokenSummary())
    }
}
