package com.lambda.opusagenda.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local principal de la aplicacion.
 *
 * Reune las entidades persistidas por Room y expone el DAO usado por la
 * arquitectura para operar con tareas.
 */
@Database(
    entities = [TaskEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /** DAO unico para leer y modificar tareas. */
    abstract fun taskDao(): TaskDao

    companion object {
        /** Cache singleton de la base para todo el proceso de la app. */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderAt INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatAmount INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatUnit TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN isCategory INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN parentId INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN expanded INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE tasks SET sortOrder = createdAt")
            }
        }

        /**
         * Devuelve una instancia unica inicializada de forma thread-safe.
         *
         * Se usa `applicationContext` para no retener accidentalmente una
         * Activity dentro del ciclo de vida de Room.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "opus_agenda.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .build()
                    .also { database ->
                        INSTANCE = database
                    }
            }
        }
    }
}
