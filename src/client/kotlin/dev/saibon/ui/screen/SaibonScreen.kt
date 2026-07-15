package dev.saibon.ui.screen

import dev.saibon.ui.SaibonCategory
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Empty settings shell: sidebar + search bar, no populated settings yet.
 * Later stages register real settings sections into this same layout.
 */
class SaibonScreen : Screen(Component.literal("Saibon")) {

    companion object {
        private const val SIDEBAR_WIDTH = 120
        private const val MARGIN = 8
        private const val ROW_HEIGHT = 20
        private const val SIDEBAR_BACKGROUND = 0x88101010.toInt()
        private const val CONTENT_BACKGROUND = 0x44101010.toInt()
        private const val PLACEHOLDER_TEXT_COLOR = 0xA0A0A0
    }

    private val categoryButtons = mutableListOf<Button>()
    private var filter: String = ""
    private var selected: SaibonCategory = SaibonCategory.GENERAL

    override fun init() {
        val box = EditBox(font, MARGIN, MARGIN, SIDEBAR_WIDTH - MARGIN * 2, ROW_HEIGHT, Component.literal("Search"))
        box.setHint(Component.literal("Search..."))
        box.setResponder { text ->
            filter = text
            rebuildSidebar()
        }
        addRenderableWidget(box)

        rebuildSidebar()
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
                }.bounds(MARGIN, y, SIDEBAR_WIDTH - MARGIN * 2, ROW_HEIGHT).build()
                categoryButtons += button
                addRenderableWidget(button)
                y += ROW_HEIGHT + 4
            }
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        extractBackground(extractor, mouseX, mouseY, delta)

        extractor.fill(0, 0, SIDEBAR_WIDTH, height, SIDEBAR_BACKGROUND)
        extractor.fill(SIDEBAR_WIDTH, 0, width, height, CONTENT_BACKGROUND)

        super.extractRenderState(extractor, mouseX, mouseY, delta)

        extractor.text(
            font,
            "${selected.displayName} - nothing here yet",
            SIDEBAR_WIDTH + MARGIN,
            MARGIN,
            PLACEHOLDER_TEXT_COLOR,
            false
        )
    }

    override fun isPauseScreen(): Boolean = false
}
