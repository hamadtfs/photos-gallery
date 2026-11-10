package com.rasheed.album.data

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

class PrivateVault(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun keys(): Set<String> = prefs.getStringSet(KEY_ITEMS, emptySet()).orEmpty()

    fun hasPin(): Boolean = prefs.getString(KEY_PIN, null) != null

    fun setPin(pin: String) {
        require(pin.length in 4..8 && pin.all { it.isDigit() }) { "PIN must be 4–8 digits" }
        prefs.edit().putString(KEY_PIN, hash(pin)).apply()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN).apply()
    }

    fun checkPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN, null) ?: return false
        return stored == hash(pin)
    }

    fun add(keys: Collection<String>) {
        val next = keys().toMutableSet().apply { addAll(keys) }
        prefs.edit().putStringSet(KEY_ITEMS, next).apply()
    }

    fun remove(keys: Collection<String>) {
        val next = keys().toMutableSet().apply { removeAll(keys.toSet()) }
        prefs.edit().putStringSet(KEY_ITEMS, next).apply()
    }

    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFS = "album_private"
        const val KEY_ITEMS = "items"
        const val KEY_PIN = "pin"
    }
}
