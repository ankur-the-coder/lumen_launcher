package com.example.tvlauncher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap

/** Small memory-safe image loader. Never crops; remembers an ambient colour per image. */
object Images {
    private val cache = object : LruCache<String, Bitmap>(26 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val colors = ConcurrentHashMap<String, Int>()
    private val main = Handler(Looper.getMainLooper())

    fun color(path: String): Int? = colors[path]

    fun load(path: String, maxH: Int, cb: (Bitmap?) -> Unit) {
        val key = "$path@$maxH"
        val hit = cache.get(key)
        if (hit != null) {
            cb(hit)
            return
        }
        Bg.io.execute {
            val b = decode(path, 0, maxH, Bitmap.Config.ARGB_8888)
            if (b != null) {
                cache.put(key, b)
                if (!colors.containsKey(path)) colors[path] = U.dominant(b)
            }
            main.post { cb(b) }
        }
    }

    fun decode(path: String, maxW: Int, maxH: Int, cfg: Bitmap.Config): Bitmap? {
        try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) return null
            var s = 1
            while ((maxH <= 0 || o.outHeight / (s * 2) >= maxH) && (maxW <= 0 || o.outWidth / (s * 2) >= maxW) && (maxH > 0 || maxW > 0)) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            o2.inPreferredConfig = cfg
            return BitmapFactory.decodeFile(path, o2)
        } catch (e: OutOfMemoryError) {
            cache.evictAll()
            return null
        } catch (e: Exception) {
            return null
        }
    }
}
