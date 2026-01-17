package funlauncher

import java.awt.Desktop
import java.io.File
import java.net.URI

fun openFolder(path: String) {
    runCatching {
        Desktop.getDesktop().open(File(path))
    }.onFailure {
        println("Failed to open folder: $path")
        it.printStackTrace()
    }
}

fun openUri(uri: URI) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri)
        } else {
            // Fallback for systems where Desktop API is not supported (e.g., some Linux DEs)
            val os = System.getProperty("os.name").lowercase()
            val command = when {
                os.contains("nix") || os.contains("nux") || os.contains("aix") -> arrayOf("xdg-open", uri.toString())
                os.contains("mac") -> arrayOf("open", uri.toString())
                else -> null
            }
            command?.let { Runtime.getRuntime().exec(it) }
                ?: throw UnsupportedOperationException("Cannot open URI on this platform")
        }
    }.onFailure {
        println("Failed to open URI: $uri")
        it.printStackTrace()
    }
}
