package com.memfault.bort.shared

import android.content.SharedPreferences

/**
 * Stages writes while a batch is open, applying them as one edit when it closes, to avoid a file rewrite and
 * fsync per write. Reads inside a batch see staged values.
 *
 * A batch belongs to this instance, not the caller: writes from anything else while one is open are staged too,
 * and are lost if the process dies before it closes.
 */
class BatchableSharedPreferences(
    private val delegate: SharedPreferences,
) : SharedPreferences {
    private val lock = Any()
    private var batchDepth = 0
    private var clearStaged = false
    private val staged = mutableMapOf<String, Any?>()

    suspend fun <T> withBatch(block: suspend () -> T): T {
        synchronized(lock) { batchDepth++ }
        return try {
            block()
        } finally {
            closeBatch()
        }
    }

    private fun closeBatch() {
        val (clear, values) = synchronized(lock) {
            if (--batchDepth != 0) return
            val snapshot = clearStaged to staged.toMap()
            clearStaged = false
            staged.clear()
            snapshot
        }
        if (clear || values.isNotEmpty()) writeDirect(clear, values)
    }

    /** The staged value for [key], [REMOVED] if staged for removal, or [NOT_STAGED]. */
    private fun stagedValue(key: String): Any? = synchronized(lock) {
        when {
            batchDepth == 0 -> NOT_STAGED
            staged.containsKey(key) -> staged[key]
            clearStaged -> REMOVED
            else -> NOT_STAGED
        }
    }

    override fun getString(key: String, defValue: String?): String? = when (val value = stagedValue(key)) {
        NOT_STAGED -> delegate.getString(key, defValue)
        REMOVED -> defValue
        else -> value as? String ?: defValue
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        when (val value = stagedValue(key)) {
            NOT_STAGED -> delegate.getStringSet(key, defValues)
            REMOVED -> defValues
            else -> value as? MutableSet<String> ?: defValues
        }

    override fun getInt(key: String, defValue: Int): Int = when (val value = stagedValue(key)) {
        NOT_STAGED -> delegate.getInt(key, defValue)
        REMOVED -> defValue
        else -> value as? Int ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long = when (val value = stagedValue(key)) {
        NOT_STAGED -> delegate.getLong(key, defValue)
        REMOVED -> defValue
        else -> value as? Long ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float = when (val value = stagedValue(key)) {
        NOT_STAGED -> delegate.getFloat(key, defValue)
        REMOVED -> defValue
        else -> value as? Float ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean = when (val value = stagedValue(key)) {
        NOT_STAGED -> delegate.getBoolean(key, defValue)
        REMOVED -> defValue
        else -> value as? Boolean ?: defValue
    }

    override fun contains(key: String): Boolean = when (stagedValue(key)) {
        NOT_STAGED -> delegate.contains(key)
        REMOVED -> false
        else -> true
    }

    @Suppress("UNCHECKED_CAST")
    override fun getAll(): MutableMap<String, *> = synchronized(lock) {
        val all = delegate.all.toMutableMap() as MutableMap<String, Any?>
        if (batchDepth == 0) return all
        if (clearStaged) all.clear()
        staged.forEach { (key, value) -> if (value === REMOVED) all.remove(key) else all[key] = value }
        all
    }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = delegate.registerOnSharedPreferenceChangeListener(listener)

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) = delegate.unregisterOnSharedPreferenceChangeListener(listener)

    override fun edit(): SharedPreferences.Editor =
        if (synchronized(lock) { batchDepth > 0 }) StagingEditor() else delegate.edit()

    @Suppress("UNCHECKED_CAST")
    private fun writeDirect(clear: Boolean, values: Map<String, Any?>) {
        val editor = delegate.edit()
        if (clear) editor.clear()
        values.forEach { (key, value) ->
            when (value) {
                REMOVED -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
                else -> editor.remove(key)
            }
        }
        editor.apply()
    }

    private inner class StagingEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearRequested = false

        /** A null value means removal, matching [SharedPreferences.Editor.putString]. */
        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            pending[key] = value ?: REMOVED
            return this
        }

        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, value: MutableSet<String>?) = put(key, value)
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = put(key, REMOVED)

        override fun clear(): SharedPreferences.Editor {
            clearRequested = true
            return this
        }

        override fun commit(): Boolean {
            stage()
            return true
        }

        override fun apply() = stage()

        private fun stage() {
            // The batch may have closed since edit(); don't strand the write.
            val closed = synchronized(lock) {
                if (batchDepth == 0) {
                    true
                } else {
                    if (clearRequested) {
                        staged.clear()
                        clearStaged = true
                    }
                    staged.putAll(pending)
                    false
                }
            }
            if (closed) writeDirect(clearRequested, pending)
        }
    }

    companion object {
        private val NOT_STAGED = Any()
        private val REMOVED = Any()
    }
}
