package com.example.yourswelnes.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.yourswelnes.data.local.room.dao.AppMonitoringDao
import com.example.yourswelnes.data.local.room.dao.LocationDao
import com.example.yourswelnes.data.local.room.entity.AppMonitoringEntity
import com.example.yourswelnes.data.local.room.entity.LocationEntity

@Database(
    entities = [LocationEntity::class, AppMonitoringEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun appMonitoringDao(): AppMonitoringDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `app_monitoring` (
                `appId` INTEGER NOT NULL,
                `appName` TEXT NOT NULL,
                `downloadLink` TEXT NOT NULL,
                `packageName` TEXT,
                `isInstalled` INTEGER NOT NULL,
                PRIMARY KEY(`appId`)
            )"""
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `locations` ADD COLUMN `user_id` TEXT NOT NULL DEFAULT ''")
    }
}
