package com.locktodo.app

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** The user's own lock-screen wallpaper, copied into private app storage. */
object Wallpaper {

    private val _version = MutableStateFlow(0)
    /** Bumps whenever the wallpaper changes so screens can reload it. */
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun file(c: Context) = File(c.filesDir, "lock_wallpaper")

    fun exists(c: Context) = file(c).exists()

    suspend fun set(c: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(c.filesDir, "lock_wallpaper.tmp")
            c.contentResolver.openInputStream(uri)!!.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            tmp.renameTo(file(c))
        }.getOrDefault(false).also { if (it) _version.value++ }
    }

    fun clear(c: Context) {
        file(c).delete()
        _version.value++
    }

    /** Decodes the wallpaper (honouring photo rotation) scaled down to roughly the requested size. */
    suspend fun load(c: Context, targetW: Int, targetH: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val f = file(c)
        if (!f.exists()) return@withContext null
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)) { decoder, info, _ ->
                val sample = max(
                    1,
                    minOf(info.size.width / max(1, targetW), info.size.height / max(1, targetH)),
                )
                decoder.setTargetSampleSize(sample)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }.asImageBitmap()
        }.getOrNull()
    }
}
