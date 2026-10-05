package com.anitec.platform.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anitec.platform.livestock.infrastructure.local.AnimalEntity
import com.anitec.platform.livestock.infrastructure.local.CorralEntity
import com.anitec.platform.livestock.infrastructure.local.HerdEntity
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import com.anitec.platform.sanitary.infrastructure.local.HealthEventEntity
import com.anitec.platform.sanitary.infrastructure.local.SanitaryDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real SQL of the cache on a device with an in-memory database. */

@RunWith(AndroidJUnit4::class)
class RoomDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var livestock: LivestockDao
    private lateinit var sanitary: SanitaryDao

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        livestock = database.livestockDao()
        sanitary = database.sanitaryDao()
    }

    @After
    fun close() = database.close()

    private fun herd(id: Int, name: String = "Farm $id") = HerdEntity(id, name, "Cajamarca", "Owner", 10, null, "Mixto")
    private fun corral(id: Int, herdId: Int, name: String = "Corral $id") = CorralEntity(id, name, herdId)
    private fun animal(id: Int, herdId: Int = 1, corralId: Int? = null, name: String = "Animal $id") = AnimalEntity(
        id, "T$id", name, "Bovino", "Brown Swiss", "Hembra", null, 400.0, "Saludable", herdId, corralId, null, null, null,
    )
    private fun event(id: Int, animalId: Int, date: String) =
        HealthEventEntity(id, animalId, "Vacuna", date, "d", "Dr. Ana", "", "", "", "", null)

    @Test
    fun replaceAll_swapsTheWholeCache() = runTest {
        livestock.replaceAll(listOf(herd(1)), listOf(corral(1, 1)), listOf(animal(1), animal(2)))
        livestock.replaceAll(listOf(herd(2)), listOf(corral(2, 2)), listOf(animal(3, herdId = 2)))

        assertEquals(listOf(2), livestock.observeHerds().first().map { it.id })
        assertEquals(listOf(2), livestock.observeCorrals().first().map { it.id })
        assertEquals(listOf(3), livestock.observeAnimals().first().map { it.id })
    }

    @Test
    fun animals_areOrderedByNameIgnoringCase() = runTest {
        livestock.upsertAnimals(listOf(animal(1, name = "zeta"), animal(2, name = "Alfa"), animal(3, name = "beta")))

        assertEquals(listOf("Alfa", "beta", "zeta"), livestock.observeAnimals().first().map { it.name })
    }

    @Test
    fun upsert_updatesAnExistingRowInPlace() = runTest {
        livestock.upsertAnimal(animal(1, name = "Luna"))
        livestock.upsertAnimal(animal(1, name = "Luna II"))

        val animals = livestock.observeAnimals().first()
        assertEquals(1, animals.size)
        assertEquals("Luna II", animals.single().name)
    }

    @Test
    fun deletingACorral_detachesItsAnimalsOnly() = runTest {
        livestock.replaceAll(
            listOf(herd(1)),
            listOf(corral(1, 1), corral(2, 1)),
            listOf(animal(1, corralId = 1), animal(2, corralId = 2)),
        )

        livestock.deleteCorral(1)
        livestock.detachAnimalsFromCorral(1)

        val animals = livestock.observeAnimals().first().associateBy { it.id }
        assertNull(animals.getValue(1).corralId)
        assertEquals(2, animals.getValue(2).corralId)
        assertEquals(listOf(2), livestock.observeCorrals().first().map { it.id })
    }

    @Test
    fun bulkDelete_removesOnlyTheGivenIds() = runTest {
        livestock.upsertAnimals((1..5).map { animal(it) })

        livestock.deleteAnimals(listOf(2, 4))

        assertEquals(listOf(1, 3, 5), livestock.animalIds().sorted())
    }

    
    @Test
    fun healthEvents_areNewestFirstAndScopedByReplaceAll() = runTest {
        sanitary.replaceAll(listOf(event(1, 1, "2026-01-10"), event(2, 1, "2026-03-05"), event(3, 2, "2026-02-01")))
        assertEquals(listOf(2, 3, 1), sanitary.observeEvents().first().map { it.id })

        sanitary.replaceAll(listOf(event(9, 1, "2026-04-01")))
        assertEquals(listOf(9), sanitary.observeEvents().first().map { it.id })
    }

    
    @Test
    fun clearAllTables_removesEveryUsersData() = runTest {
        livestock.replaceAll(listOf(herd(1)), listOf(corral(1, 1)), listOf(animal(1)))
        sanitary.upsert(event(1, 1, "2026-01-10"))

        RoomUserDataCleaner(database).clear()

        assertTrue(livestock.observeHerds().first().isEmpty())
        assertTrue(livestock.observeCorrals().first().isEmpty())
        assertTrue(livestock.observeAnimals().first().isEmpty())
        assertTrue(sanitary.observeEvents().first().isEmpty())
    }
}

