package funlauncher

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModificationDownloader {

    suspend fun download(
        modFile: File,
        destination: java.io.File,
        onProgress: (Float, String) -> Unit
    ) {
        try {
            withContext(Dispatchers.IO) {
                val response = Network.client.get(modFile.url) {
                    onDownload { bytesSentTotal, contentLength ->
                        val progress = if (contentLength > 0) {
                            bytesSentTotal.toFloat() / contentLength
                        } else 0f
                        onProgress(progress, "Скачивание...")
                    }
                }
                destination.parentFile.mkdirs()
                destination.writeBytes(response.body())
                onProgress(1f, "Завершено")
            }
        } catch (e: Exception) {
            onProgress(0f, "Ошибка")
            throw e
        }
    }
}
