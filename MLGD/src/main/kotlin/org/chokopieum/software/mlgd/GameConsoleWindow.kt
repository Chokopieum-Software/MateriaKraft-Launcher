package org.chokopieum.software.mlgd

import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/**
 * Простое окно Swing для отображения логов игры в реальном времени.
 */
class GameConsoleWindow(buildName: String) {

    private val frame: JFrame = JFrame("Game Console - $buildName")
    private val textArea: JTextArea = JTextArea()

    init {
        // Запускаем создание UI в потоке обработки событий Swing
        SwingUtilities.invokeLater {
            // Настраиваем текстовую область
            textArea.isEditable = false
            textArea.lineWrap = true
            textArea.wrapStyleWord = true

            // Добавляем текстовую область в панель с прокруткой
            val scrollPane = JScrollPane(textArea)
            scrollPane.preferredSize = Dimension(750, 450)

            // Настраиваем основное окно
            frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE // Закрывать только это окно, а не все приложение
            frame.layout = BorderLayout()
            frame.add(scrollPane, BorderLayout.CENTER)
            frame.pack() // Устанавливаем размер окна по содержимому
            frame.setLocationRelativeTo(null) // Центрируем окно
            frame.isVisible = true
        }
    }

    /**
     * Добавляет строку лога в текстовую область.
     * Этот метод потокобезопасен, так как использует SwingUtilities.invokeLater.
     * @param message Сообщение для добавления.
     */
    fun appendLog(message: String) {
        SwingUtilities.invokeLater {
            textArea.append("$message\n")
            // Автоматически прокручиваем вниз
            textArea.caretPosition = textArea.document.length
        }
    }



    /**
     * Закрывает окно консоли.
     * Должен вызываться, когда игровой процесс завершается.
     */
    fun close() {
        SwingUtilities.invokeLater {
            frame.dispose()
        }
    }
}
