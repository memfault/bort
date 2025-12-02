package com.memfault.bort.metrics.database

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.memfault.bort.BortJson
import com.memfault.bort.reporting.AggregationType
import com.memfault.bort.reporting.DataType
import com.memfault.bort.reporting.MetricType
import com.memfault.bort.reporting.NumericAgg
import com.memfault.bort.reporting.StateAgg
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

const val CURRENT_METRICS_DB_NAME = "metrics_v2"

/**
 * For now, we can avoid writing DB migrations like a normal DB and just update the schema directly by changing the
 * DB name ([CURRENT_METRICS_DB_NAME]). We then want to delete the previous DB, and make sure we don't re-use
 * the DB name by keeping track of it in [PREVIOUS_METRICS_DB_NAMES]. Once we've shipped to production, we might have
 * to be more careful deciding whether to use this method as it'll wipe all existing data (mostly important for
 * carryovers or daily heartbeats).
 */
val PREVIOUS_METRICS_DB_NAMES = listOf(
    "metrics",
)

@Database(
    entities = [
        DbReport::class,
        DbMetricMetadata::class,
        DbMetricValue::class,
    ],
    version = 2,
    // See room.schemaLocation in build.gradle for configuration of export location.
    exportSchema = true,
    // When we bump the version, enable auto-generated migrations by uncommenting:
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
)
@TypeConverters(MetricsConverters::class)
abstract class MetricsDb : RoomDatabase() {
    abstract fun dao(): MetricsDao

    companion object {
        fun create(application: Application): MetricsDb = Room.databaseBuilder(
            application,
            MetricsDb::class.java,
            CURRENT_METRICS_DB_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()
    }
}

@Entity(tableName = "reports")
data class DbReport(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,
    val startTimeMs: Long,
    val endTimeMs: Long? = null,
    val name: String? = null,
    val softwareVersion: String? = null,
)

@Entity(
    tableName = "metric_metadata",
    indices = [
        Index(
            value = ["reportId", "eventName"],
            unique = true,
        ),
    ],
    foreignKeys = [
        ForeignKey(
            entity = DbReport::class,
            parentColumns = ["id"],
            childColumns = ["reportId"],
        ),
    ],
)
data class DbMetricMetadata(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reportId: Long,
    val eventName: String,
    val metricType: MetricType,
    val dataType: DataType,
    val carryOver: Boolean,
    val aggregations: Aggs,
    val internal: Boolean,
)

@Entity(
    tableName = "metric_values",
    indices = [
        Index(
            value = ["metadataId"],
        ),
    ],
    foreignKeys = [
        ForeignKey(
            entity = DbMetricMetadata::class,
            parentColumns = ["id"],
            childColumns = ["metadataId"],
        ),
    ],
)
data class DbMetricValue(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val metadataId: Long,
    val version: Int,
    val timestampMs: Long,
    val stringVal: String? = null,
    val numberVal: Double? = null,
    val boolVal: Boolean? = null,
    val newField: Boolean? = null,
)

@VisibleForTesting
data class DbDump(
    val reports: List<DbReport> = emptyList(),
    val metadata: List<DbMetricMetadata> = emptyList(),
    val values: List<DbMetricValue> = emptyList(),
)

// Unfortunately, our native service writes booleans as 1 or 0 for true or false, meaning to
// retain backwards compatibility we'll also want to write booleans in the same way, especially
// once we migrate to only using the Room metrics database.
fun DbMetricValue.jsonValue(): JsonPrimitive =
    boolVal?.let { JsonPrimitive(if (it) "1" else "0") }
        ?: numberVal?.let { JsonPrimitive(it) }
        ?: stringVal?.let { JsonPrimitive(it) }
        ?: JsonNull

data class DbNumericAggs(
    val min: Double,
    val max: Double,
    val sum: Double,
    val mean: Double,
    val count: Long,
)

@Serializable
data class Aggs(private val numeric: List<NumericAgg>, private val state: List<StateAgg>) {
    constructor(both: List<AggregationType>) : this(
        numeric = both.filterIsInstance<NumericAgg>(),
        state = both.filterIsInstance<StateAgg>(),
    )

    val aggregations: List<AggregationType> get() = numeric + state

    fun toJson(): String = BortJson.encodeToString(serializer(), this)

    companion object {
        fun fromJson(json: String): Aggs = BortJson.decodeFromString(serializer(), json)
    }
}

/**
 * Provide explicit type converters for enums (otherwise Room will use enum name).
 */
class MetricsConverters {
    @TypeConverter
    fun decodeMetricType(value: String): MetricType? = MetricType.lookup[value]

    @TypeConverter
    fun encodeMetricType(metricType: MetricType): String = metricType.value

    @TypeConverter
    fun decodeDataType(value: String): DataType? = DataType.lookup[value]

    @TypeConverter
    fun encodeDataType(dataType: DataType): String = dataType.value

    @TypeConverter
    fun decodeAggs(value: String): Aggs? = try {
        Aggs.fromJson(value)
    } catch (e: SerializationException) {
        null
    }

    @TypeConverter
    fun encodeAggs(aggs: Aggs): String = aggs.toJson()
}
