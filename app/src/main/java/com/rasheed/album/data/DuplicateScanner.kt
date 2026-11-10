package com.rasheed.album.data

import android.content.ContentResolver
import java.security.MessageDigest

data class DuplicateGroup(
    val id: String,
    val items: List<MediaFile>,
) {
    val keeper: MediaFile get() = items.first()
    val extras: List<MediaFile> get() = items.drop(1)
}

object DuplicateScanner {
    fun find(resolver: ContentResolver, items: List<MediaFile>): List<DuplicateGroup> {
        val candidates = items.filter { it.sizeBytes >= 1_024L }
            .groupBy { "${it.sizeBytes}|${it.mimeType}|${it.isVideo}" }
            .values
            .filter { it.size >= 2 }
        if (candidates.isEmpty()) return emptyList()

        val byHash = LinkedHashMap<String, MutableList<MediaFile>>()
        candidates.forEach { group ->
            group.forEach { item ->
                val hash = fingerprint(resolver, item) ?: return@forEach
                byHash.getOrPut(hash) { ArrayList() }.add(item)
            }
        }

        return byHash.values
            .filter { it.size >= 2 }
            .map { matches ->
                val ordered = matches.sortedWith(
                    compareByDescending<MediaFile> { it.takenAt }
                        .thenByDescending { it.sizeBytes }
                        .thenBy { it.key },
                )
                DuplicateGroup(id = ordered.first().key, items = ordered)
            }
            .sortedByDescending { it.items.size }
    }

    private fun fingerprint(resolver: ContentResolver, item: MediaFile): String? {
        return try {
            resolver.openInputStream(item.uri).use { input ->
                if (input == null) return null
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                var read: Int
                var total = 0L
                val limit = if (item.sizeBytes > 8L * 1024L * 1024L) 1024L * 1024L else Long.MAX_VALUE
                while (input.read(buffer).also { read = it } > 0) {
                    digest.update(buffer, 0, read)
                    total += read
                    if (total >= limit) break
                }
                val hex = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                "${item.sizeBytes}|$hex|$limit"
            }
        } catch (_: Exception) {
            null
        }
    }
}
