package splash

import java.awt.Dimension
import java.awt.Image
import java.awt.Toolkit
import java.util.Properties
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JWindow
import javax.swing.SwingConstants
import kotlin.math.min

fun createAndShowSplashScreen(statusLabel: JLabel): JWindow? {
    return runCatching {
        JWindow().apply {
            val props = Properties().apply {
                Thread.currentThread().contextClassLoader.getResourceAsStream("app.properties")?.use(::load)
            }
            val version = props.getProperty("version", "Unknown")
            val buildNumber = props.getProperty("buildNumber", "N/A")
            val versionText = "$version ($buildNumber)"

            val versionLabel = JLabel(versionText, SwingConstants.RIGHT).apply {
                foreground = java.awt.Color.WHITE
            }
            statusLabel.apply {
                foreground = java.awt.Color.WHITE
                horizontalAlignment = SwingConstants.RIGHT
            }

            val originalImage = ImageIO.read(Thread.currentThread().contextClassLoader.getResource("banner.png"))
            val screenSize = Toolkit.getDefaultToolkit().screenSize
            val targetWidth = screenSize.width / 2.5
            val targetHeight = screenSize.height / 2.5
            val ratio = min(targetWidth / originalImage.width, targetHeight / originalImage.height)
            val newWidth = (originalImage.width * ratio).toInt()
            val newHeight = (originalImage.height * ratio).toInt()
            val finalImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)

            contentPane = JLayeredPane().apply {
                preferredSize = Dimension(newWidth, newHeight)
                val margin = 10
                
                add(JLabel(ImageIcon(finalImage)).apply {
                    setBounds(0, 0, newWidth, newHeight)
                }, JLayeredPane.DEFAULT_LAYER)
                
                add(statusLabel.apply {
                    setBounds(0, newHeight - 30 - margin, newWidth - margin, 20)
                }, JLayeredPane.PALETTE_LAYER)

                add(versionLabel.apply {
                    setBounds(0, newHeight - 15 - margin, newWidth - margin, 20)
                }, JLayeredPane.PALETTE_LAYER)
            }
            pack()
            setLocationRelativeTo(null)
            isVisible = true
        }
    }.onFailure {
        println("Failed to create splash screen: ${it.stackTraceToString()}")
    }.getOrNull()
}
