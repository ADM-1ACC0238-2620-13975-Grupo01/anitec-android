package com.anitec.platform.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.anitec.platform.activities.infrastructure.local.ActivitiesDao
import com.anitec.platform.activities.infrastructure.local.ActivityEntity
import com.anitec.platform.core.common.UserDataCleaner
import com.anitec.platform.core.outbox.OutboxDao
import com.anitec.platform.core.outbox.PendingOperationEntity
import com.anitec.platform.devices.infrastructure.local.DeviceEntity
import com.anitec.platform.devices.infrastructure.local.DevicesDao
import com.anitec.platform.financial.infrastructure.local.FinancialDao
import com.anitec.platform.financial.infrastructure.local.FinancialRecordEntity
import com.anitec.platform.livestock.infrastructure.local.AnimalEntity
import com.anitec.platform.livestock.infrastructure.local.CorralEntity
import com.anitec.platform.livestock.infrastructure.local.HerdEntity
import com.anitec.platform.livestock.infrastructure.local.LivestockDao
import com.anitec.platform.sanitary.infrastructure.local.HealthEventEntity
import com.anitec.platform.sanitary.infrastructure.local.SanitaryDao
import com.anitec.platform.veterinary.infrastructure.local.ClientEntity
import com.anitec.platform.veterinary.infrastructure.local.VeterinaryDao
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

/** Local cache of the signed-in user's records. Its contents are always rebuildable from the API. */
@Database(
    entities = [
        HerdEntity::class, CorralEntity::class, AnimalEntity::class, HealthEventEntity::class, ClientEntity::class,
        ActivityEntity::class, FinancialRecordEntity::class, DeviceEntity::class,
        PendingOperationEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun livestockDao(): LivestockDao
    abstract fun sanitaryDao(): SanitaryDao
    abstract fun veterinaryDao(): VeterinaryDao
    abstract fun activitiesDao(): ActivitiesDao
    abstract fun financialDao(): FinancialDao
    abstract fun devicesDao(): DevicesDao
    abstract fun outboxDao(): OutboxDao
}

class RoomUserDataCleaner @Inject constructor(
    private val database: AppDatabase,
) : UserDataCleaner {
    override suspend fun clear() = withContext(Dispatchers.IO) { database.clearAllTables() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindingsModule {
    @Binds
    abstract fun bindUserDataCleaner(impl: RoomUserDataCleaner): UserDataCleaner
}

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

    @Provides
    fun provideVeterinaryDao(database: AppDatabase): VeterinaryDao = database.veterinaryDao()

    @Provides
    fun provideActivitiesDao(database: AppDatabase): ActivitiesDao = database.activitiesDao()

    @Provides
    fun provideFinancialDao(database: AppDatabase): FinancialDao = database.financialDao()

    @Provides
    fun provideDevicesDao(database: AppDatabase): DevicesDao = database.devicesDao()

    @Provides
    fun provideOutboxDao(database: AppDatabase): OutboxDao = database.outboxDao()
}
