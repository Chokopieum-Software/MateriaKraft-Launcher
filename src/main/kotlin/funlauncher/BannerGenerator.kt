/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import io.ktor.client.call.*
import io.ktor.client.request.*
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.IntBuffer
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class BannerGenerator(private val pathManager: PathManager) {

    private val bannerWidth = 480
    private val bannerHeight = 270
    private val iconSize = 128

    /**
     * Generates a banner from a modpack icon URL. This is a suspend function.
     * @param iconUrl The URL of the icon.
     * @param buildName The name of the build, used for the output filename.
     * @return The path to the generated banner, or null on failure.
     */
    suspend fun generateBanner(iconUrl: String, buildName: String): String? {
        return try {
            // 1. Download image using Ktor client
            val imageBytes = Network.client.get(iconUrl).body<ByteArray>()
            val iconImage = Image.makeFromEncoded(imageBytes)

            // 2. Calculate a more robust average color
            val averageColor = calculateAverageColor(iconImage.toAwtImage())
            val (startColor, endColor) = createGradientColors(averageColor)

            // 3. Create the banner using Skia
            val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(bannerWidth, bannerHeight)
            val canvas = surface.canvas

            // 4. Create and draw the gradient background
            val paint = Paint().apply {
                shader = Shader.makeLinearGradient(
                    0f, 0f, 0f, bannerHeight.toFloat(),
                    intArrayOf(startColor.rgb, endColor.rgb)
                )
            }
            canvas.drawPaint(paint)

            // 5. Draw the icon in the center
            val x = (bannerWidth - iconSize) / 2f
            val y = (bannerHeight - iconSize) / 2f
            canvas.drawImageRect(iconImage, org.jetbrains.skia.Rect.makeXYWH(x, y, iconSize.toFloat(), iconSize.toFloat()))

            // 6. Encode the banner to PNG bytes
            val bannerImage = surface.makeImageSnapshot()
            val pngData = bannerImage.encodeToData(EncodedImageFormat.PNG)
                ?: throw IllegalStateException("Failed to encode banner to PNG")

            // 7. Save the banner to a file
            val backgroundsDir = pathManager.getBackgroundsDir()
            backgroundsDir.createDirectories()
            val safeBuildName = buildName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val outputFile = backgroundsDir.resolve("$safeBuildName-banner.png")
            outputFile.writeBytes(pngData.bytes)

            outputFile.toString()
        } catch (e: Exception) {
            println("Failed to generate banner for $buildName: ${e.message}")
            if (e !is io.ktor.client.plugins.ClientRequestException) {
                e.printStackTrace()
            }
            null
        }
    }

    private fun Image.toAwtImage(): BufferedImage {
        val bitmap = Bitmap.makeFromImage(this)
        val info = ImageInfo(this.width, this.height, org.jetbrains.skia.ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bytes = bitmap.readPixels(info, (info.width * info.bytesPerPixel), 0, 0)
            ?: throw IllegalStateException("Failed to read pixels from Skia image")

        val bimg = BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB_PRE)
        val intBuffer: IntBuffer = ByteBuffer.wrap(bytes).asIntBuffer()
        val intArray = IntArray(intBuffer.remaining())
        intBuffer.get(intArray)

        bimg.raster.setDataElements(0, 0, this.width, this.height, intArray)
        return bimg
    }

    private fun calculateAverageColor(image: BufferedImage): Color {
        var redSum: Long = 0
        var greenSum: Long = 0
        var blueSum: Long = 0
        var pixelCount: Long = 0

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val pixel = image.getRGB(x, y)
                val alpha = (pixel shr 24) and 0xff
                if (alpha > 128) { // Only consider non-transparent pixels
                    redSum += (pixel shr 16) and 0xff
                    greenSum += (pixel shr 8) and 0xff
                    blueSum += (pixel) and 0xff
                    pixelCount++
                }
            }
        }

        if (pixelCount == 0L) return Color(45, 45, 45) // Default dark gray if image is fully transparent

        val avgRed = (redSum / pixelCount).toInt()
        val avgGreen = (greenSum / pixelCount).toInt()
        val avgBlue = (blueSum / pixelCount).toInt()

        return Color(avgRed, avgGreen, avgBlue)
    }

    private fun createGradientColors(baseColor: Color): Pair<Color, Color> {
        val hsb = Color.RGBtoHSB(baseColor.red, baseColor.green, baseColor.blue, null)
        val hue = hsb[0]
        val saturation = hsb[1]
        val brightness = hsb[2]

        // If the color is too dark, too bright, or grayscale, use a default blue gradient
        if (brightness < 0.1f || brightness > 0.95f || saturation < 0.1f) {
            return Pair(Color(0x3E5151), Color(0xDECBA4).darker())
        }

        val startBrightness = (brightness + 0.2f).coerceAtMost(1.0f)
        val endBrightness = (brightness - 0.2f).coerceAtLeast(0.0f)

        val startColor = Color.getHSBColor(hue, saturation, startBrightness)
        val endColor = Color.getHSBColor(hue, saturation, endBrightness)

        return Pair(startColor, endColor)
    }
}
