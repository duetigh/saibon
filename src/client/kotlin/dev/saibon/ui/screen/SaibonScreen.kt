package dev.saibon.ui.screen

import dev.saibon.ui.SaibonCategory
import dev.saibon.ui.settings.SettingsRegistry
import dev.saibon.ui.style.Panel
import dev.saibon.ui.widget.CategoryTileWidget
import dev.saibon.ui.widget.SearchEditBox
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Categorized, searchable settings screen. The sidebar lists [SaibonCategory]
 * entries as item-icon tiles (NEU-style); the content pane lays out whatever
 * [SettingsRegistry] sections feature modules have registered for the
 * selected category. The search box filters both sidebar categories and
 * setting labels within the selected one.
 */
class SaibonScreen : Screen(Component.literal("Saibon")) {

    private data class ContentLabel(val x: Int, val y: Int, val text: String, val isTitle: Boolean)

    companion object {
        private const val SIDEBAR_WIDTH = 120
        private const val MARGIN = 8
        private const val ROW_HEIGHT = 20
        private const val ROW_GAP = 4
        private const val WIDGET_WIDTH = 150
        private const val PLACEHOLDER_TEXT_COLOR = 0xA0A0A0
        private const val TITLE_TEXT_COLOR = 0xFFD700

        private fun iconFor(category: SaibonCategory): Item = when (category) {
            SaibonCategory.GENERAL -> Items.COMPASS
            SaibonCategory.HUD -> Items.PAINTING
            SaibonCategory.FEATURES -> Items.REDSTONE_TORCH
            SaibonCategory.DATA -> Items.CHEST
            SaibonCategory.UPDATES -> Items.EXPERIENCE_BOTTLE
            SaibonCategory.ABOUT -> Items.BOOK
        }
    }

    private val categoryTiles = mutableListOf<CategoryTileWidget>()
    private val contentWidgets = mutableListOf<AbstractWidget>()
    private val contentLabels = mutableListOf<ContentLabel>()
    private var filter: String = ""
    private var selected: SaibonCategory = SaibonCategory.GENERAL

    override fun init() {
        val box = SearchEditBox(font, MARGIN, MARGIN, SIDEBAR_WIDTH - MARGIN * 2, ROW_HEIGHT, Component.literal("Search"))
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
        categoryTiles.forEach { removeWidget(it) }
        categoryTiles.clear()

        var y = MARGIN * 2 + ROW_HEIGHT
        SaibonCategory.entries
            .filter { it.displayName.contains(filter, ignoreCase = true) }
            .forEach { category ->
                val tile = CategoryTileWidget(
                    MARGIN, y, SIDEBAR_WIDTH - MARGIN * 2, ROW_HEIGHT,
                    Component.literal(category.displayName),
                    category,
                    ItemStack(iconFor(category)),
                    category == selected
                ) { picked ->
                    selected = picked
                    categoryTiles.forEach { it.setSelected(it.category == selected) }
                    rebuildContent()
                }
                categoryTiles += tile
                addRenderableWidget(tile)
                y += ROW_HEIGHT + ROW_GAP
            }
    }

    private fun rebuildContent() {
        contentWidgets.forEach { removeWidget(it) }
        contentWidgets.clear()
        contentLabels.clear()

        val labelX = SIDEBAR_WIDTH + MARGIN * 2
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
        Panel.draw(extractor, 0, 0, SIDEBAR_WIDTH, height)
        Panel.draw(extractor, SIDEBAR_WIDTH + MARGIN, 0, width - SIDEBAR_WIDTH - MARGIN, height)

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
