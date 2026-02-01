package com.example.wakacje1.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Główna konfiguracja bazy danych Room.
 * Wersja schematu: 3.
 */
@Database(
    entities = [LocalPlanEntity::class],
    version = 3,
    exportSchema = false
)
abstract class LocalPlansDb : RoomDatabase() {

    // Punkt dostępu do operacji na tabeli (DAO).
    abstract fun plans(): LocalPlanDao

    companion object {

        /**
         * Migracja v1 -> v2.
         * Typ: Destrukcyjna (Rebuild).
         * Cel: Normalizacja danych (konwersja 0 na NULL w kolumnach dat).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Utworzenie tabeli o nowej strukturze
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_plans_new (
                        uid TEXT NOT NULL,
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        startDateMillis INTEGER,
                        endDateMillis INTEGER,
                        createdAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL,
                        payloadJson TEXT NOT NULL,
                        PRIMARY KEY(uid, id)
                    )
                    """.trimIndent()
                )

                // 2. Kopiowanie danych z logiką naprawczą (NULLIF)
                db.execSQL(
                    """
                    INSERT INTO local_plans_new(uid, id, title, startDateMillis, endDateMillis, createdAtMillis, updatedAtMillis, payloadJson)
                    SELECT uid, id, title,
                           NULLIF(startDateMillis, 0),
                           NULLIF(endDateMillis, 0),
                           createdAtMillis, updatedAtMillis, payloadJson
                    FROM local_plans
                    """.trimIndent()
                )

                // 3. Podmiana starej tabeli na nową
                db.execSQL("DROP TABLE local_plans")
                db.execSQL("ALTER TABLE local_plans_new RENAME TO local_plans")
            }
        }

        /**
         * Migracja v2 -> v3.
         * Typ: Add Index.
         * Cel: Optymalizacja zapytania sortującego po dacie edycji.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_local_plans_uid_updatedAtMillis ON local_plans(uid, updatedAtMillis)"
                )
            }
        }
    }
}