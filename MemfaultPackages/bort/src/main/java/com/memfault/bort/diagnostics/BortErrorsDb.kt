package com.memfault.bort.diagnostics

import android.app.Application
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.memfault.bort.BortJson
import com.memfault.bort.diagnostics.BortErrorType.UnknownError
import com.memfault.bort.diagnostics.MapWrapper.Companion.fromJson
import com.memfault.bort.diagnostics.MapWrapper.Companion.toJson
import com.memfault.bort.shared.Logger
import kotlinx.serialization.Serializable

@Database(
    entities = [
        DbBortError::class,
        DbJob::class,
    ],
    version = 1,
    // See room.schemaLocation in build.gradle for configuration of export location.
    exportSchema = true,
    // When we bump the version, enable auto-generated migrations by uncommenting:
    // autoMigrations = [
    //     AutoMigration (from = 1, to = 2),
    // ],
)
@TypeConverters(ErrorConverters::class)
abstract class BortErrorsDb : RoomDatabase() {
    abstract fun dao(): BortErrorsDao

    companion object {
        fun create(application: Application): BortErrorsDb = Room.databaseBuilder(
            application,
            BortErrorsDb::class.java,
            "bort_errors",
        ).build()
    }
}

@Entity(tableName = "errors")
data class DbBortError(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timeMs: Long,
    val type: BortErrorType,
    val eventData: Map<String, String>,
    val uploaded: Boolean,
)

@Entity(tableName = "jobs")
data class DbJob(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val jobName: String,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val result: String? = null,
)

/**
 * Utility to serialize maps.
 */
@Serializable
class MapWrapper(private val map: Map<String, String>) {
    companion object {
        fun Map<String, String>.toJson(): String = BortJson.encodeToString(serializer(), MapWrapper(this))
        fun fromJson(json: String): Map<String, String> = BortJson.decodeFromString<MapWrapper>(serializer(), json).map
    }
}

/**
 * Provide explicit type converters for enums (otherwise Room will use enum name).
 */
class ErrorConverters {
    @TypeConverter
    fun decodeErrorType(value: String): BortErrorType =
        BortErrorType.entries.find { it.eventType == value } ?: UnknownError

    @TypeConverter
    fun encodeErrorType(errorType: BortErrorType): String = errorType.eventType

    @TypeConverter
    fun decodeStringMap(json: String): Map<String, String> = try {
        fromJson(json)
    } catch (e: Exception) {
        Logger.e("Error deserializing Bort error", e)
        mapOf()
    }

    @TypeConverter
    fun encodeStringMap(stringMap: Map<String, String>): String = stringMap.toJson()
}
