package dev.saibon.ui.screen

import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.settings.SettingsRegistry
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Categorized, searchable settings screen. The sidebar lists [SaibonCategory]
 * entries; the content pane lays out whatever [SettingsRegistry] sections
 * feature modules have registered for the selected category. The search box
 * filters both sidebar categories and setting labels within the selected one.
 */
class SaibonScreen : Screen(Component.literal("Saibon")) {

    private data class ContentLabel(val x: Int, val y: Int, val text: String, val isTitle: Boolean)

    companion object {
        private const val SIDEBAR_WIDTH = 120
        private const val MARGIN = 8
        private const val ROW_HEIGHT = 20
        private const val ROW_GAP = 4
        private const val WIDGET_WIDTH = 150
        private const val SIDEBAR_BACKGROUND = 0x88101010.toInt()
        private const val CONTENT_BACKGROUND = 0x44101010.toInt()
        private const val PLACEHOLDER_TEXT_COLOR = 0xA0A0A0
        private const val TITLE_TEXT_COLOR = 0xFFD700
    }

    private val categoryButtons = mutableListOf<Button>()
    private val contentWidgets = mutableListOf<AbstractWidget>()
    private val contentLabels = mutableListOf<ContentLabel>()
    private var filter: String = ""
    private var selected: SaibonCategory = SaibonCategory.GENERAL

    override fun init() {
        val box = EditBox(font, MARGIN, MARGIN, SIDEBAR_WIDTH - MARGIN * 2, ROW_HEIGHT, Component.literal("Search"))
        box.setHint(Component.literal("Search..."))
        box.setResponder { text ->
            filter = text
            rebuildSidebar()
            rebuildContent()
        }
        addRenderableWidget(box)

        rebuildSidebar()
        rebuildContent()
    }

    private fun rebuildSidebar() {
        categoryButtons.forEach { removeWidget(it) }
        categoryButtons.clear()

        var y = MARGIN * 2 + ROW_HEIGHT
        SaibonCategory.entries
            .filter { it.displayName.contains(filter, ignoreCase = true) }
            .forEach { category ->
                val button = Button.builder(Component.literal(category.displayName)) {
                    selected = category
                    rebuildContent()
                }.bounds(MARGIN, y, SIDEBAR_WIDTH - MARGIN * 2, ROW_HEIGHT).build()
                categoryButtons += button
                addRenderableWidget(button)
                y += ROW_HEIGHT + 4
            }
    }

    private fun rebuildContent() {
        contentWidgets.forEach { removeWidget(it) }
        contentWidgets.clear()
        contentLabels.clear()

        val labelX = SIDEBAR_WIDTH + MARGIN
        val widgetX = width - MARGIN - WIDGET_WIDTH
        var y = MARGIN

        val sections = SettingsRegistry.sectionsFor(selected)
        var matched = false

        for (section in sections) {
            val entries = section.entries.filter {
                filter.isBlank() || it.label.contains(filter, ignoreCase = true) || section.title.contains(filter, ignoreCase = true)
            }
            if (entries.isEmpty()) continue
            matched = true

            contentLabels += ContentLabel(labelX, y, section.title, isTitle = true)
            y += ROW_HEIGHT

            for (entry in entries) {
                contentLabels += ContentLabel(labelX, y + (ROW_HEIGHT - 8) / 2, entry.label, isTitle = false)
                val widget = entry.build(this, widgetX, y, WIDGET_WIDTH, ROW_HEIGHT)
                contentWidgets += widget
                addRenderableWidget(widget)
                y += ROW_HEIGHT + ROW_GAP
            }
            y += ROW_GAP
        }

        if (!matched) {
            val message = if (sections.isEmpty()) "Nothing here yet" else "No settings match \"$filter\""
            contentLabels += ContentLabel(labelX, MARGIN, message, isTitle = false)
        }
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        extractBackground(extractor, mouseX, mouseY, delta)

        extractor.fill(0, 0, SIDEBAR_WIDTH, height, SIDEBAR_BACKGROUND)
        extractor.fill(SIDEBAR_WIDTH, 0, width, height, CONTENT_BACKGROUND)

        super.extractRenderState(extractor, mouseX, mouseY, delta)

        contentLabels.forEach { label ->
            extractor.text(
                font,
                label.text,
                label.x,
                label.y,
                if (label.isTitle) TITLE_TEXT_COLOR else PLACEHOLDER_TEXT_COLOR,
                false
            )
        }
    }

    override fun isPauseScreen(): Boolean = false
}
