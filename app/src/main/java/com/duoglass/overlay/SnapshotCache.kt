package com.duoglass.overlay

import android.graphics.Bitmap
import android.os.SystemClock

/**
 * The last picture of each panel kind (inner / cover), shared across
 * displays. A panel that has just switched on takes ~0.3–0.4 s before it can
 * be captured; its previous picture bridges that gap under heavy frost.
 */
class SnapshotCache(private val maxAgeMs: Long) {
    private class Entry(val bitmap: Bitmap, val at: Long)

    private val entries = HashMap<Boolean, Entry>()

    fun put(inner: Boolean, bitmap: Bitmap) {
        entries[inner] = Entry(bitmap, SystemClock.uptimeMillis())
    }

    /** A recent picture of this panel kind at this size, or null. */
    fun get(inner: Boolean, width: Int, height: Int): Bitmap? {
        val e = entries[inner] ?: return null
        if (SystemClock.uptimeMillis() - e.at > maxAgeMs) return null
        if (e.bitmap.width != width || e.bitmap.height != height) return null
        return e.bitmap
    }

    fun ageMs(inner: Boolean): Long = entries[inner]?.let { SystemClock.uptimeMillis() - it.at } ?: -1
}
