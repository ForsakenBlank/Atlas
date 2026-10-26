package com.forsakenblank.atlas.platform

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL
import com.forsakenblank.atlas.data.AtlasDatabase
import com.forsakenblank.atlas.data.starterSql

// android keeps its database where every older version left it, so nothing is lost on update
fun buildAtlasDatabase(context: Context): AtlasDatabase =
    Room.databaseBuilder(context, AtlasDatabase::class.java, "atlas.db")
        .addMigrations(*AtlasDatabase.MIGRATIONS)
        .addCallback(StarterContent)
        .build()

// same file the old preferencesDataStore delegate used, so settings carry over
fun settingsDataStore(context: Context): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("settings") })

suspend fun <R> AtlasDatabase.inTransaction(block: suspend () -> R): R = withTransaction(block)

// android runs migrations on the old support database, the bundled driver path is there for tests
fun sqlMigration(from: Int, to: Int, statements: List<String>): Migration = object : Migration(from, to) {
    override fun migrate(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)
    override fun migrate(connection: SQLiteConnection) = statements.forEach(connection::execSQL)
}

// a fresh install only gets an inbox, the starter pack picker adds everything else
private object StarterContent : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) = starterSql().forEach(db::execSQL)
}
