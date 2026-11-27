package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LevelProgressEntity::class,
        ActiveSessionEntity::class,
        DailyChallengeEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE active_session ADD COLUMN remainingUndos INTEGER NOT NULL DEFAULT 5")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_challenges` (
                        `dateKey` TEXT NOT NULL,
                        `tierOrdinal` INTEGER NOT NULL,
                        `colorCount` INTEGER NOT NULL,
                        `themeOrdinal` INTEGER NOT NULL,
                        `initialTubesJson` TEXT NOT NULL,
                        `currentTubesJson` TEXT NOT NULL,
                        `undoStackJson` TEXT NOT NULL,
                        `remainingUndos` INTEGER NOT NULL,
                        `extraTubeUsed` INTEGER NOT NULL,
                        `moveCount` INTEGER NOT NULL,
                        `isCompleted` INTEGER NOT NULL,
                        `bestMoves` INTEGER,
                        `completedAtMs` INTEGER,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`dateKey`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE active_session ADD COLUMN initialTubesJson TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chromaflow_offline.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
