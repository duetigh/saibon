package dev.saibon.ui.screen

import dev.saibon.update.VersionManifest
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/** Renders a fetched [VersionManifest]'s changelog text in-game, never a browser popup. */
class ChangelogScreen(private val manifest: VersionManifest) :
    Screen(Component.literal("Saibon v${manifest.latestVersion}")) {

    companion object {
        private const val MARGIN = 12
        private const val BUTTON_HEIGHT = 20
    }

    override fun init() {
        addRenderableWidget(
            Button.builder(Component.literal("Close")) { onClose() }
                .bounds(MARGIN, height - MARGIN - BUTTON_HEIGHT, 80, BUTTON_HEIGHT).build()
        )
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        extractBackground(extractor, mouseX, mouseY, delta)
        super.extractRenderState(extractor, mouseX, mouseY, delta)

        extractor.text(font, title, MARGIN, MARGIN, 0xFFFFD700.toInt())
        extractor.textWithWordWrap(
            font,
            Component.literal(manifest.changelog),
            MARGIN,
            MARGIN + 16,
            width - MARGIN * 2,
            0xFFC0C0C0.toInt()
        )
    }

    override fun isPauseScreen(): Boolean = false
}
