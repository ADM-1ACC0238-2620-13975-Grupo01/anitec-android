package com.anitec.platform.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.anitec.platform.core.common.UserDataCleaner
import com.anitec.platform.livestock.infrastructure.local.AnimalEntity
import com.anitec.platform.livestock.infrastructure.local.CorralEntity
import com.anitec.platform.livestock.infrastructure.local.HerdEntity
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import com.anitec.platform.sanitary.infrastructure.local.HealthEventEntity
import com.anitec.platform.sanitary.infrastructure.local.SanitaryDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room database that caches the signed-in user's livestock and sanitary records.
 *
 * Contents are always rebuildable from the API after sync; schema is exported under
 * `app/schemas` for review. Version bumps currently wipe the DB via destructive migration.
 */
@Database(
    entities = [HerdEntity::class, CorralEntity::class, AnimalEntity::class, HealthEventEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun livestockDao(): LivestockDao
    abstract fun sanitaryDao(): SanitaryDao
}

/**
 * [UserDataCleaner] backed by Room: clears every table on the IO dispatcher
 * when the session ends so the next user starts with an empty cache.
 */
class RoomUserDataCleaner @Inject constructor(
    private val database: AppDatabase,
) : UserDataCleaner {
    override suspend fun clear() = withContext(Dispatchers.IO) { database.clearAllTables() }
}

/** Binds [RoomUserDataCleaner] as the app-wide [UserDataCleaner]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindingsModule {
    @Binds
    abstract fun bindUserDataCleaner(impl: RoomUserDataCleaner): UserDataCleaner
}

/**
 * Provides the singleton [AppDatabase] (`anitec.db`) and its DAOs.
 * Destructive migration is intentional: the cache is disposable and refilled from the API.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "anitec.db")
            // Cache only for now: a schema change just rebuilds it from the API.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideLivestockDao(database: AppDatabase): LivestockDao = database.livestockDao()

    @Provides
    fun provideSanitaryDao(database: AppDatabase): SanitaryDao = database.sanitaryDao()
}
