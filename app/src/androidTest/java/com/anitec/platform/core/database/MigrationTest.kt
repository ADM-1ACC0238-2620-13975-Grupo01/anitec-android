package com.anitec.platform.core.database

// Test coverage marker: Room migration scenarios.

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The queue of changes made offline holds data that is not on the server yet, so updating the app must keep it.
 * These tests open the database as version 7 shipped it, run the migration and read what is left.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate7To8_keepsTheQueuedChangesAndLeavesTheirKeyBlank() {
        helper.createDatabase(DB, 7).apply {
            execSQL(
                "INSERT INTO pending_operations (seq, kind, localId, payload, createdAt, attempts, failed, lastError) " +
                    "VALUES (1, 'create_animal', -1, '{\"tag\":\"BOV-1\"}', 1000, 2, 0, NULL)",
            )
            execSQL(
                "INSERT INTO pending_operations (seq, kind, localId, payload, createdAt, attempts, failed, lastError) " +
                    "VALUES (2, 'create_activity', -2, '{\"title\":\"A\"}', 2000, 0, 1, 'rejected')",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB, 8, true, MIGRATION_7_8)

        migrated.query("SELECT seq, kind, localId, payload, attempts, failed, lastError, idempotencyKey FROM pending_operations ORDER BY seq").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals("create_animal", cursor.getString(1))
            assertEquals(-1, cursor.getInt(2))
            assertEquals("{\"tag\":\"BOV-1\"}", cursor.getString(3))
            assertEquals(2, cursor.getInt(4))
            assertEquals("", cursor.getString(7))
            cursor.moveToNext()
            assertEquals("create_activity", cursor.getString(1))
            assertEquals(1, cursor.getInt(5))
            assertEquals("rejected", cursor.getString(6))
            assertEquals("", cursor.getString(7))
        }
    }

    @Test
    fun migrate7To8_acceptsNewChangesWithoutAKeyColumnValue() {
        helper.createDatabase(DB, 7).close()

        val migrated = helper.runMigrationsAndValidate(DB, 8, true, MIGRATION_7_8)
        migrated.execSQL(
            "INSERT INTO pending_operations (kind, localId, payload, createdAt, attempts, failed) VALUES ('create_animal', -1, '{}', 1, 0, 0)",
        )

        migrated.query("SELECT idempotencyKey FROM pending_operations").use { cursor ->
            cursor.moveToFirst()
            assertEquals("", cursor.getString(0))
        }
    }

    @Test
    fun migrate8To9_keepsTheCachedRecordsAtVersionOne() {
        helper.createDatabase(DB, 8).apply {
            execSQL(
                "INSERT INTO health_events (id, animalId, type, date, description, veterinarian, diagnosis, treatment, prescription, followUp, nextDueDate) " +
                    "VALUES (5, 4, 'Vacuna', '2026-10-02', 'd', 'Dra. Ana', '', '', '', '', NULL)",
            )
            execSQL(
                "INSERT INTO financial_records (id, ownerId, type, category, amount, date, description) VALUES (3, 10, 'Ingreso', 'Venta', 5.0, '2026-10-01', 'x')",
            )
            execSQL(
                "INSERT INTO pending_operations (seq, kind, localId, payload, createdAt, attempts, failed, lastError, idempotencyKey) " +
                    "VALUES (1, 'create_animal', -1, '{}', 1000, 0, 0, NULL, 'key-1')",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(DB, 9, true, MIGRATION_8_9)

        migrated.query("SELECT version FROM health_events WHERE id = 5").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT version FROM financial_records WHERE id = 3").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        // The queue of changes made offline, with its keys, is untouched.
        migrated.query("SELECT idempotencyKey FROM pending_operations").use { cursor ->
            cursor.moveToFirst()
            assertEquals("key-1", cursor.getString(0))
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
