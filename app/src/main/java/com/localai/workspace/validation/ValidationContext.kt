package com.localai.workspace.validation

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/** Same private files/services, but no preference write can reach the normal application. */
internal class ValidationContext(base: Context, private val scopedCache:java.io.File?=null) : ContextWrapper(base) {
    private val preferences = mutableMapOf<String, SharedPreferences>()
    override fun getApplicationContext(): Context = this
    override fun getCacheDir(): java.io.File = scopedCache?.apply { mkdirs() } ?: super.getCacheDir()
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = synchronized(preferences) {
        preferences.getOrPut(name) { MemoryPreferences() }
    }
}

internal class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    override fun getAll(): MutableMap<String, *> = synchronized(values) { values.toMutableMap() }
    override fun contains(key: String?) = synchronized(values) { key in values }
    override fun getString(key: String?, defValue: String?): String? = synchronized(values) { values[key] as? String ?: defValue }
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = synchronized(values) { (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues?.toMutableSet() }
    override fun getInt(key: String?, defValue: Int) = synchronized(values) { values[key] as? Int ?: defValue }
    override fun getLong(key: String?, defValue: Long) = synchronized(values) { values[key] as? Long ?: defValue }
    override fun getFloat(key: String?, defValue: Float) = synchronized(values) { values[key] as? Float ?: defValue }
    override fun getBoolean(key: String?, defValue: Boolean) = synchronized(values) { values[key] as? Boolean ?: defValue }
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) { listener?.let { synchronized(values) { listeners.add(it) } } }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) { synchronized(values) { listeners.remove(listener) } }
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>(); private var clear = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, value: MutableSet<String>?) = apply { pending[key] = value?.toSet() }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            val callbacks = synchronized(values) {
                if(clear) values.clear()
                pending.forEach { (key, value) -> if(value == null) values.remove(key) else values[key] = value }
                listeners.toList()
            }
            callbacks.forEach { listener -> pending.keys.forEach { listener.onSharedPreferenceChanged(this@MemoryPreferences, it) } }
        }
    }
}
