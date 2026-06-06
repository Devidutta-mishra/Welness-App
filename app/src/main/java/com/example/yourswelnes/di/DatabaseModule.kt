package com.example.yourswelnes.di

import android.content.Context
import androidx.room.Room
import com.example.yourswelnes.core.database.AppDatabase
import com.example.yourswelnes.core.database.MIGRATION_1_2
import com.example.yourswelnes.core.database.MIGRATION_2_3
import com.example.yourswelnes.core.database.MIGRATION_3_4
import com.example.yourswelnes.core.database.MIGRATION_4_5
import com.example.yourswelnes.core.database.MIGRATION_5_6
import com.example.yourswelnes.core.database.dao.AppMonitoringDao
import com.example.yourswelnes.core.database.dao.LocationDao
import com.example.yourswelnes.core.database.dao.NotificationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "yourswelnes.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()

    @Provides
    @Singleton
    fun provideLocationDao(db: AppDatabase): LocationDao = db.locationDao()

    @Provides
    @Singleton
    fun provideAppMonitoringDao(db: AppDatabase): AppMonitoringDao = db.appMonitoringDao()

    @Provides
    @Singleton
    fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()
}
