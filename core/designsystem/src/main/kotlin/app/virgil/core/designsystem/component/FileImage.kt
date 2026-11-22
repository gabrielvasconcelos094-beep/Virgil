package app.virgil.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Carrega imagens locais (capas, ilustrações) fora da thread principal, reduzidas para
 * [targetWidthPx] com inSampleSize, e as mantém num cache em memória limitado (~24 MB).
 * Sem dependências externas: o app não carrega imagens da rede.
 */
object LocalImages {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(path: String, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$path@$targetWidthPx"
        cache.get(key)?.let { return@withContext it }
        val f = File(path)
        if (!f.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return@withContext null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
        cache.put(key, bmp)
        bmp
    }
}

@Composable
fun FileImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    targetWidthPx: Int = 480,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: @Composable () -> Unit = {},
) {
    val image by produceState<ImageBitmap?>(initialValue = null, path, targetWidthPx) {
        value = path?.let { LocalImages.load(it, targetWidthPx)?.asImageBitmap() }
    }
    val img = image
    if (img == null) placeholder() else Image(img, contentDescription, modifier, contentScale = contentScale)
}
