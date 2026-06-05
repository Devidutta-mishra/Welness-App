package com.example.yourswelnes.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.yourswelnes.data.local.room.dao.AppMonitoringDao
import com.example.yourswelnes.data.local.room.dao.LocationDao
import com.example.yourswelnes.data.local.room.dao.NotificationDao
import com.example.yourswelnes.data.local.room.entity.AppMonitoringEntity
import com.example.yourswelnes.data.local.room.entity.LocationEntity
import com.example.yourswelnes.data.local.room.entity.NotificationEntity

@Database(
    entities = [LocationEntity::class, AppMonitoringEntity::class, NotificationEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun appMonitoringDao(): AppMonitoringDao
    abstract fun notificationDao(): NotificationDao
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

// Non-destructive: adds the index that speeds up the pending-locations query. The index name
// must match Room's generated name (index_<table>_<col>_<col>_<col>) or schema validation fails.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_locations_user_id_uploaded_created_at` " +
                "ON `locations` (`user_id`, `uploaded`, `created_at`)"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `notifications` (
                `id` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `message` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `is_read` INTEGER NOT NULL DEFAULT 0,
                `is_displayed` INTEGER NOT NULL DEFAULT 0,
                `created_at` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )"""
        )
    }
}
