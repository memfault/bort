package com.memfault.bort.metrics

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.memfault.bort.metrics.PropertyType.BOOL
import com.memfault.bort.metrics.PropertyType.DOUBLE
import com.memfault.bort.metrics.PropertyType.INT
import com.memfault.bort.metrics.PropertyType.STRING
import javax.inject.Inject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Stores device properties, ready to be uploaded.
 */
class DevicePropertiesStore @Inject constructor(private val db: DevicePropertiesDb) {
    /**
     * Add or update a property. Only update if value is changed.
     */
    suspend fun upsert(name: String, value: String, type: PropertyType, internal: Boolean) {
        db.deviceProperty().upsert(DbDeviceProperty(name = name, value = value, type = type, internal = internal))
    }

    suspend fun upsert(name: String, value: String, internal: Boolean = false) =
        upsert(name = name, value = value, type = STRING, internal = internal)

    suspend fun upsert(name: String, value: Double, internal: Boolean = false) =
        upsert(name = name, value = value.toString(), type = DOUBLE, internal = internal)

    suspend fun upsert(name: String, value: Long, internal: Boolean = false) =
        upsert(name = name, value = value.toString(), type = INT, internal = internal)

    suspend fun upsert(name: String, value: Int, internal: Boolean = false) =
        upsert(name = name, value = value.toString(), type = INT, internal = internal)

    suspend fun upsert(name: String, value: Boolean, internal: Boolean = false) =
        upsert(name = name, value = value.toString(), type = BOOL, internal = internal)

    /**
     * Collects (changed) device properties which need to be uploaded.
     */
    suspend fun collectDeviceProperties(internal: Boolean): Map<String, JsonPrimitive> {
        return db.deviceProperty().collectUpdatedAndMarkUploaded(internal = internal).map { it.name to it.jsonValue() }
            .toMap()
    }

    companion object {
        /**
         * All values are stored as strings, with a separate type column. When reading, we make a best-effort attempt to
         * transform into a value of the correct type. If that is not possible, we leave as a string.
         *
         * [JsonPrimitive] is used as a convenient union type (which we can also use directly in
         * [HeartbeatFileUploadPayload] to be converted into correctly-typed json).
         */
        fun DbDeviceProperty.jsonValue() = when (type) {
            BOOL -> value.asBoolProperty()
            INT -> value.asIntProperty()
            DOUBLE -> value.asDoubleProperty()
            STRING -> JsonPrimitive(value)
        } ?: JsonPrimitive(value)

        fun String.asBoolProperty() = when (this) {
            "true", "1" -> JsonPrimitive(true)
            "false", "0" -> JsonPrimitive(false)
            else -> null
        }

        fun String.asIntProperty() = toLongOrNull()?.let { JsonPrimitive(it) }

        fun String.asDoubleProperty() = toDoubleOrNull()?.let { JsonPrimitive(it) }
    }
}

@Database(entities = [DbDeviceProperty::class], version = 1)
abstract class DevicePropertiesDb : RoomDatabase() {
    abstract fun deviceProperty(): DevicePropertyDao

    companion object {
        fun create(context: Context): DevicePropertiesDb = Room.databaseBuilder(
            context,
            DevicePropertiesDb::class.java, "device_properties"
        ).build()
    }
}

enum class PropertyType {
    BOOL,
    INT,
    DOUBLE,
    STRING,
}

@Entity
data class DbDeviceProperty(
    @PrimaryKey
    val name: String,
    val value: String,
    val type: PropertyType,
    val internal: Boolean,
    val changed: Boolean = true,
)

@Dao
abstract class DevicePropertyDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(property: DbDeviceProperty): Long

    @Query(
        "UPDATE DbDeviceProperty SET changed = 1, value = :value, type = :type WHERE name = :name AND " +
            "(value != :value OR type != :type)"
    )
    abstract suspend fun updateIfChanged(name: String, value: String, type: PropertyType)

    @Transaction
    open suspend fun upsert(property: DbDeviceProperty) {
        val id = insert(property)
        // Did not exist
        if (id == -1L) {
            updateIfChanged(name = property.name, value = property.value, type = property.type)
        }
    }

    @Query("SELECT * FROM DbDeviceProperty WHERE changed = 1 AND internal = :internal")
    abstract suspend fun updatedProperties(internal: Boolean): List<DbDeviceProperty>

    @Query("UPDATE DbDeviceProperty SET changed = 0 WHERE name IN (:name)")
    abstract suspend fun markUploaded(name: List<String>)

    @Transaction
    open suspend fun collectUpdatedAndMarkUploaded(internal: Boolean): List<DbDeviceProperty> {
        val properties = updatedProperties(internal)
        markUploaded(properties.map { it.name })
        return properties
    }

    @Query("DELETE FROM DbDeviceProperty")
    @VisibleForTesting
    abstract suspend fun deleteAll()
}
