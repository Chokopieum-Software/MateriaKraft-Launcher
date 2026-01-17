package ui.widgets

import org.jetbrains.skia.Data
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.svg.SVGDOM
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

object ImageLoader {
    private val httpClient = HttpClient(CIO)
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun loadAvatar(uuid: String?): ImageBitmap? {
        if (uuid == null) return null
        val url = "https://crafatar.com/avatars/$uuid?size=64&overlay"
        return loadImage(url)
    }

    suspend fun loadImage(url: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        cache[url]?.let { return@withContext it }

        try {
            val imageBitmap: ImageBitmap?
            if (url.trim().startsWith("<svg", ignoreCase = true)) {
                // Если строка является SVG-кодом, обрабатываем ее напрямую
                val bytes = url.toByteArray(Charsets.UTF_8)
                val data = Data.makeFromBytes(bytes)
                val svgDom = SVGDOM(data)

                val widthInt = 64
                val heightInt = 64
                svgDom.setContainerSize(widthInt.toFloat(), heightInt.toFloat())

                val bitmap = Bitmap()
                bitmap.allocN32Pixels(widthInt, heightInt)

                val canvas = Canvas(bitmap)
                canvas.clear(Color.TRANSPARENT)
                svgDom.render(canvas)

                imageBitmap = Image.makeFromBitmap(bitmap).toComposeImageBitmap()
            } else {
                // Иначе, пытаемся загрузить по URL
                val response = httpClient.get(url)
                val bytes = response.readBytes()
                val contentType = response.headers["Content-Type"]

                val isSvg = contentType?.contains("image/svg", ignoreCase = true) == true ||
                        url.endsWith(".svg", ignoreCase = true)

                imageBitmap = if (isSvg) {
                    val data = Data.makeFromBytes(bytes)
                    val svgDom = SVGDOM(data)

                    val widthInt = 64
                    val heightInt = 64
                    svgDom.setContainerSize(widthInt.toFloat(), heightInt.toFloat())

                    val bitmap = Bitmap()
                    bitmap.allocN32Pixels(widthInt, heightInt)

                    val canvas = Canvas(bitmap)
                    canvas.clear(Color.TRANSPARENT)
                    svgDom.render(canvas)

                    Image.makeFromBitmap(bitmap).toComposeImageBitmap()
                } else {
                    Image.makeFromEncoded(bytes).toComposeImageBitmap()
                }
            }

            imageBitmap.also { if (it != null) cache[url] = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun close() {
        httpClient.close()
        cache.clear()
    }
}