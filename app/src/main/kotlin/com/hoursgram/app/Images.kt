package com.hoursgram.app

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val cache = LruCache<String, ImageBitmap>(80)

private fun decode(path: String, maxSide: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}

@Composable
fun rememberImage(path: String?, maxSide: Int): ImageBitmap? {
    val key = "$path@$maxSide"
    val state = produceState<ImageBitmap?>(initialValue = path?.let { cache.get(key) }, key) {
        if (path == null) {
            value = null
            return@produceState
        }
        value = cache.get(key) ?: withContext(Dispatchers.IO) {
            try {
                decode(path, maxSide)
            } catch (_: Throwable) {
                null
            }
        }?.also { cache.put(key, it) }
    }
    return state.value
}
