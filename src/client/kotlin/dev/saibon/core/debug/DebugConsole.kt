package dev.saibon.core.debug

import dev.saibon.core.Saibon
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * A plain Swing window, separate from the Minecraft/GLFW window, for dumping ad-hoc debug
 * output that's awkward to get out of `logs/latest.log` in the moment (e.g. asking the user to
 * reproduce a bug and hand back exactly what happened). Not tied to any one feature — anything
 * in the mod can call [log] and a line shows up here, with a "Copy All" button so the user can
 * paste the whole session back verbatim instead of hunting through a log file.
 *
 * [SaibonClient][dev.saibon.client.SaibonClient] calls [show] unconditionally on client init, so
 * this window is always open alongside the game rather than something the user has to remember
 * to summon via `/saibondebug` mid-bug-report — by the time something goes wrong it's already
 * there to copy from. `/saibondebug` still exists to reopen it if it gets closed.
 *
 * Deliberately separate from [Saibon.logger] rather than a log-appender/handler on it: this is
 * for a human to glance at and copy *right now*, not a permanent record, so callers opt in per
 * call instead of every log line in the mod showing up here.
 *
 * Swing and Minecraft's LWJGL/GLFW window coexist fine in the same JVM on Windows (no shared
 * event loop to conflict over, unlike macOS where AWT needs the main thread) — this hasn't been
 * tried on macOS/Linux.
 */
object DebugConsole {
    private const val MAX_LINES = 2000
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private var frame: JFrame? = null
    private var textArea: JTextArea? = null

    fun log(message: String) {
        val line = "[${LocalTime.now().format(TIME_FORMAT)}] $message"
        SwingUtilities.invokeLater {
            val area = ensureWindow()
            area.append(line + "\n")
            trimIfTooLong(area)
            area.caretPosition = area.document.length
        }
    }

    fun show() {
        SwingUtilities.invokeLater { ensureWindow() }
    }

    private fun ensureWindow(): JTextArea {
        textArea?.let { area ->
            frame?.let { f ->
                if (!f.isVisible) f.isVisible = true
                return area
            }
        }

        val area = JTextArea().apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            lineWrap = false
        }
        textArea = area

        val copyButton = JButton("Copy All").apply {
            addActionListener {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(area.text), null)
            }
        }
        val clearButton = JButton("Clear").apply {
            addActionListener { area.text = "" }
        }

        val buttonPanel = JPanel().apply {
            add(copyButton)
            add(clearButton)
        }

        val newFrame = JFrame("Saibon Debug Console").apply {
            defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
            layout = BorderLayout()
            preferredSize = Dimension(900, 500)
            add(JScrollPane(area), BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
            pack()
            isVisible = true
            toFront()
        }
        frame = newFrame
        return area
    }

    private fun trimIfTooLong(area: JTextArea) {
        val lineCount = area.lineCount
        if (lineCount <= MAX_LINES) return
        val cutoffLine = lineCount - MAX_LINES
        val cutoffOffset = area.getLineStartOffset(cutoffLine)
        area.replaceRange("", 0, cutoffOffset)
    }
}
