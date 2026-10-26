package com.forsakenblank.atlas.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.migration.Migration
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.forsakenblank.atlas.data.AtlasDatabase
import com.forsakenblank.atlas.data.starterSql
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toOkioPath
import java.io.File

// the pc version brings its own sqlite so it behaves the same on windows and every linux
fun buildAtlasDatabase(dataDir: File): AtlasDatabase =
    Room.databaseBuilder<AtlasDatabase>(name = File(dataDir, "atlas.db").absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*AtlasDatabase.MIGRATIONS)
        .addCallback(StarterContent)
        .build()

fun settingsDataStore(dataDir: File): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { File(dataDir, "settings.preferences_pb").toOkioPath() })

suspend fun <R> AtlasDatabase.inTransaction(block: suspend () -> R): R =
    useWriterConnection { it.immediateTransaction { block() } }

fun sqlMigration(from: Int, to: Int, statements: List<String>): Migration = object : Migration(from, to) {
    override fun migrate(connection: SQLiteConnection) = statements.forEach(connection::execSQL)
}

private object StarterContent : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) = starterSql().forEach(connection::execSQL)
}
