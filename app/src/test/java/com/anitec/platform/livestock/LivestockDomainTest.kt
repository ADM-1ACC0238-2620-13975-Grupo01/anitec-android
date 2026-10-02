package com.anitec.platform.livestock

import com.anitec.platform.core.network.resolveMediaUrl
import com.anitec.platform.core.session.UserRole
import com.anitec.platform.livestock.domain.Animal
import com.anitec.platform.livestock.domain.AnimalPlacementPolicy
import com.anitec.platform.livestock.domain.AnimalStatus
import com.anitec.platform.livestock.domain.AnimalView
import com.anitec.platform.livestock.domain.Corral
import com.anitec.platform.livestock.domain.Herd
import com.anitec.platform.livestock.domain.LivestockScope
import com.anitec.platform.livestock.domain.filterBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun herd(id: Int, ownerId: Int, vetId: Int? = null, name: String = "Farm $id") =
    Herd(id, name, "Cajamarca", "Owner $ownerId", ownerId, vetId, "Mixto")

internal fun corral(id: Int, herdId: Int, name: String = "Corral $id") = Corral(id, name, herdId)

internal fun animal(
    id: Int,
    herdId: Int,
    corralId: Int? = null,
    tag: String = "TAG-$id",
    name: String = "Animal $id",
    species: String = "Bovino",
    status: String = "Saludable",
) = Animal(id, tag, name, species, "Brown Swiss", "Hembra", null, 400.0, status, herdId, corralId, null, null, null)

class AnimalStatusTest {
    @Test
    fun `web values map to their status`() {
        assertEquals(AnimalStatus.Healthy, AnimalStatus.fromApi("Saludable"))
        assertEquals(AnimalStatus.Observation, AnimalStatus.fromApi("Observacion"))
        assertEquals(AnimalStatus.InTreatment, AnimalStatus.fromApi("En tratamiento"))
        assertEquals(AnimalStatus.Sold, AnimalStatus.fromApi("Vendido"))
    }

    @Test
    fun `English seed values map to the same statuses`() {
        assertEquals(AnimalStatus.Healthy, AnimalStatus.fromApi("Healthy"))
        assertEquals(AnimalStatus.Healthy, AnimalStatus.fromApi("sano"))
        assertEquals(AnimalStatus.Sold, AnimalStatus.fromApi("SOLD"))
    }

    @Test
    fun `unknown and blank values are Unknown`() {
        assertEquals(AnimalStatus.Unknown, AnimalStatus.fromApi("Quarantine"))
        assertEquals(AnimalStatus.Unknown, AnimalStatus.fromApi(""))
        assertEquals(AnimalStatus.Unknown, AnimalStatus.fromApi(null))
    }

    @Test
    fun `only observation and treatment need attention`() {
        assertTrue(AnimalStatus.Observation.needsAttention)
        assertTrue(AnimalStatus.InTreatment.needsAttention)
        assertFalse(AnimalStatus.Healthy.needsAttention)
        assertFalse(AnimalStatus.Sold.needsAttention)
    }

    @Test
    fun `an animal keeps its raw status text`() {
        val unusual = animal(1, 1, status = "Quarantine")
        assertEquals("Quarantine", unusual.status)
        assertEquals(AnimalStatus.Unknown, unusual.healthStatus)
    }
}

class LivestockScopeTest {
    private val herds = listOf(
        herd(1, ownerId = 10),
        herd(2, ownerId = 11, vetId = 50),
        herd(3, ownerId = 12),
    )

    @Test
    fun `a rancher sees only the herds they own`() {
        val visible = LivestockScope.visibleHerds(UserRole.Rancher, 10, emptySet(), herds)
        assertEquals(listOf(1), visible.map { it.id })
    }

    @Test
    fun `a veterinarian sees linked clients herds and herds that name them`() {
        val visible = LivestockScope.visibleHerds(UserRole.Veterinarian, 50, setOf(12), herds)
        assertEquals(setOf(2, 3), visible.map { it.id }.toSet())
    }

    @Test
    fun `a veterinarian without clients sees nothing`() {
        assertTrue(LivestockScope.visibleHerds(UserRole.Veterinarian, 99, emptySet(), herds).isEmpty())
    }

    @Test
    fun `corrals and animals follow the visible herds`() {
        val visibleHerds = herds.filter { it.id == 1 }
        val corrals = listOf(corral(1, 1), corral(2, 2))
        val animals = listOf(animal(1, 1, 1), animal(2, 2, 2), animal(3, 3))

        assertEquals(listOf(1), LivestockScope.visibleCorrals(corrals, visibleHerds).map { it.id })
        assertEquals(listOf(1), LivestockScope.visibleAnimals(animals, visibleHerds).map { it.id })
    }
}

class AnimalPlacementPolicyTest {
    private val corrals = listOf(corral(1, herdId = 1), corral(2, herdId = 2))

    @Test
    fun `a corral of the same herd is valid`() {
        assertTrue(AnimalPlacementPolicy.isValidPlacement(1, 1, corrals))
    }

    @Test
    fun `a corral of another herd is rejected`() {
        assertFalse(AnimalPlacementPolicy.isValidPlacement(1, 2, corrals))
    }

    @Test
    fun `a missing or unknown corral is rejected`() {
        assertFalse(AnimalPlacementPolicy.isValidPlacement(1, null, corrals))
        assertFalse(AnimalPlacementPolicy.isValidPlacement(1, 99, corrals))
    }

    @Test
    fun `batch size is limited to 1 through 500`() {
        assertFalse(AnimalPlacementPolicy.isValidBatchSize(0))
        assertTrue(AnimalPlacementPolicy.isValidBatchSize(1))
        assertTrue(AnimalPlacementPolicy.isValidBatchSize(500))
        assertFalse(AnimalPlacementPolicy.isValidBatchSize(501))
    }
}

class AnimalSearchTest {
    private val views = listOf(
        AnimalView(animal(1, 1, 1, tag = "BOV-001", name = "Luna"), "Hato Los Alamos", "Corral ALAMOS 1"),
        AnimalView(animal(2, 1, 1, tag = "BOV-002", name = "Tornado", status = "En tratamiento"), "Hato Los Alamos", "Corral ALAMOS 1"),
        AnimalView(animal(3, 2, 2, tag = "AVE-001", name = "Gallina Roja", species = "Pollo"), "Granja El Molino", "Corral MOLINO 1"),
        AnimalView(animal(4, 2, null, tag = "AVE-002", name = "Pato Norte", species = "Pato"), "Granja El Molino", null),
    )

    @Test
    fun `empty query returns everything`() {
        assertEquals(4, views.filterBy("  ", null).size)
    }

    @Test
    fun `matches code name species status herd and corral case-insensitively`() {
        assertEquals(listOf(1), views.filterBy("luna", null).map { it.animal.id })
        assertEquals(listOf(3), views.filterBy("pollo", null).map { it.animal.id })
        assertEquals(listOf(2), views.filterBy("tratamiento", null).map { it.animal.id })
        assertEquals(listOf(3, 4), views.filterBy("molino", null).map { it.animal.id })
        assertEquals(listOf(3), views.filterBy("MOLINO 1", null).map { it.animal.id })
    }

    @Test
    fun `corral filter narrows the list before the text search`() {
        assertEquals(listOf(1, 2), views.filterBy("", 1).map { it.animal.id })
        assertEquals(listOf(2), views.filterBy("tornado", 1).map { it.animal.id })
        assertTrue(views.filterBy("tornado", 2).isEmpty())
    }

    @Test
    fun `no match returns an empty list`() {
        assertTrue(views.filterBy("zebra", null).isEmpty())
    }
}

class MediaUrlTest {
    @Test
    fun `relative paths are resolved against the server root`() {
        assertEquals("http://10.0.2.2:5191/uploads/animals/a.jpg", resolveMediaUrl("/uploads/animals/a.jpg", "http://10.0.2.2:5191"))
        assertEquals("https://x.onrender.com/uploads/a.jpg", resolveMediaUrl("uploads/a.jpg", "https://x.onrender.com/"))
    }

    @Test
    fun `absolute urls are untouched`() {
        assertEquals("https://cdn.example.com/a.png", resolveMediaUrl("https://cdn.example.com/a.png", "http://server"))
    }

    @Test
    fun `blank paths give no url`() {
        assertNull(resolveMediaUrl(null, "http://server"))
        assertNull(resolveMediaUrl("  ", "http://server"))
    }
}
