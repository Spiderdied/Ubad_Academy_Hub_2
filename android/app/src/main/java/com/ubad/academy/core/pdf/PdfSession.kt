package com.ubad.academy.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Wraps the platform [PdfRenderer] (no WebView, no PDF.js).
 *
 * PdfRenderer allows one open page at a time and is not thread-safe, so every call is
 * serialised on [lock]. The file is opened through a descriptor, which means very large
 * PDFs are never read into memory; pages are rasterised on demand at the requested
 * width and kept in a small byte-bounded LRU cache.
 */
class PdfSession private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    private val lock = Any()
    private var closed = false
    val pageCount: Int = renderer.pageCount
    private val sizes = arrayOfNulls<Size>(pageCount)

    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** Page size in PDF points (1/72 inch); cached after the first lookup. */
    suspend fun pageSize(index: Int): Size = withContext(Dispatchers.IO) {
        sizes[index] ?: synchronized(lock) {
            if (closed) return@synchronized DEFAULT_SIZE
            renderer.openPage(index).use { Size(it.width, it.height) }.also { sizes[index] = it }
        }
    }

    fun cachedSize(index: Int): Size? = sizes.getOrNull(index)

    fun cached(index: Int, widthPx: Int): Bitmap? = cache.get(key(index, widthPx))

    /** Renders page [index] [widthPx] wide (height follows the aspect ratio). Null once closed. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(key(index, widthPx))?.let { return@withContext it }
        synchronized(lock) {
            if (closed) return@synchronized null
            renderer.openPage(index).use { page ->
                sizes[index] = Size(page.width, page.height)
                val w = widthPx.coerceIn(1, MAX_WIDTH)
                val h = (w.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(1, MAX_SIDE)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                // Pages are transparent by default; paper is white regardless of theme.
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }?.also { cache.put(key(index, widthPx), it) }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            runCatching { renderer.close() }
            runCatching { fd.close() }
        }
        cache.evictAll()
    }

    private fun key(i: Int, w: Int) = "$i@$w"

    sealed interface OpenResult {
        data class Ok(val session: PdfSession) : OpenResult
        data object Missing : OpenResult
        data object Protected : OpenResult
        data object Broken : OpenResult
    }

    companion object {
        /** Largest bitmap width/side we allow (a 2560×4096 page is ~40 MB). */
        const val MAX_WIDTH = 2560
        const val MAX_SIDE = 4096
        /** US Letter in points, used until a page's real size is known. */
        val DEFAULT_SIZE = Size(612, 792)

        suspend fun open(file: File): OpenResult = withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() == 0L) return@withContext OpenResult.Missing
            val fd = try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (e: Exception) {
                return@withContext OpenResult.Missing
            }
            try {
                val r = PdfRenderer(fd)
                if (r.pageCount <= 0) { r.close(); fd.close(); OpenResult.Broken } else OpenResult.Ok(PdfSession(fd, r))
            } catch (e: SecurityException) {
                fd.close(); OpenResult.Protected
            } catch (e: Exception) {
                fd.close(); OpenResult.Broken
            }
        }

        private fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 6).coerceAtMost(96L * 1024 * 1024).toInt()
    }
}
