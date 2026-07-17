package dev.saibon.hud

import dev.saibon.core.Saibon
import dev.saibon.ui.style.Panel
import dev.saibon.ui.widget.DropdownWidget
import dev.saibon.ui.widget.SliderWidget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * The pasted spec's "GUI Locations" screen / `PLAN.md` Stage 2's HUD editor,
 * built for real here: dims the screen, shows every registered [HudModule]
 * as a draggable outlined box, and exposes anchor/scale/enable/reset controls
 * for whichever box is selected. Dragging only ever adjusts
 * [HudElementState.offsetX]/[offsetY] by the raw mouse delta — anchor is
 * changed explicitly via the dropdown, never inferred from drag position, so
 * there's no corner-snap magnetism to get subtly wrong without a real
 * in-game session to verify it against.
 */
class HudEditScreen : Screen(Component.literal("HUD Locations")) {

    private data class Box(val module: HudModule, var x: Int, var y: Int, var w: Int, var h: Int)

    private val boxes = mutableListOf<Box>()
    private var selectedId: String? = null
    private var draggingId: String? = null

    private val controlWidgets = mutableListOf<AbstractWidget>()

    override fun init() {
        rebuildBoxes()
        rebuildControls()
    }

    private fun rebuildBoxes() {
        boxes.clear()
        for (module in HudEngine.allModules()) {
            val state = HudEngine.stateFor(module)
            val size = module.measure()
            val (x, y) = HudEngine.origin(state, width, height, size)
            boxes += Box(module, x, y, (size.width * state.scale).toInt().coerceAtLeast(8), (size.height * state.scale).toInt().coerceAtLeast(8))
        }
    }

    private fun rebuildControls() {
        controlWidgets.forEach { removeWidget(it) }
        controlWidgets.clear()

        val module = boxes.firstOrNull { it.module.id == selectedId }?.module ?: return
        val state = HudEngine.stateFor(module)

        val barY = height - 28
        var x = 8
        val anchorDropdown = DropdownWidget.create(
            this, x, barY, 120, 20, Component.literal("Anchor"),
            HudAnchor.entries.toList(), state.anchor, { Component.literal(it.name.lowercase().replace('_', ' ')) }
        ) { anchor -> state.anchor = anchor; Saibon.config.save(); rebuildBoxes(); rebuildControls() }
        addRenderableWidget(anchorDropdown)
        controlWidgets += anchorDropdown
        x += 124

        val scaleSlider = SliderWidget(x, barY, 120, 20, 0.5f, 2.0f, state.scale, { "Scale %.2fx".format(it) }) { value ->
            state.scale = value
            Saibon.config.save()
            rebuildBoxes()
        }
        addRenderableWidget(scaleSlider)
        controlWidgets += scaleSlider
        x += 124

        val enableButton = Button.builder(Component.literal(if (state.enabled) "Enabled" else "Disabled")) {
            state.enabled = !state.enabled
            Saibon.config.save()
            rebuildControls()
        }.bounds(x, barY, 80, 20).build()
        addRenderableWidget(enableButton)
        controlWidgets += enableButton
        x += 84

        val resetButton = Button.builder(Component.literal("Reset")) {
            Saibon.config.data.hud.elements.remove(module.id)
            Saibon.config.save()
            rebuildBoxes()
            rebuildControls()
        }.bounds(x, barY, 60, 20).build()
        addRenderableWidget(resetButton)
        controlWidgets += resetButton
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.mouseClicked(event, doubleClick)) return true

        val hit = boxes.firstOrNull { box ->
            event.x() >= box.x && event.x() < box.x + box.w && event.y() >= box.y && event.y() < box.y + box.h
        }
        if (hit != null) {
            selectedId = hit.module.id
            draggingId = hit.module.id
            rebuildControls()
            return true
        }
        return false
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val id = draggingId
        if (id != null) {
            val module = boxes.firstOrNull { it.module.id == id }?.module
            if (module != null) {
                val state = HudEngine.stateFor(module)
                // HudEngine.origin() subtracts offsetX/offsetY for right/bottom anchors (measuring
                // inward from that edge), so a rightward/downward mouse drag must shrink, not grow,
                // the offset for those anchors or the box would crawl the opposite way of the mouse.
                val xSign = when (state.anchor) {
                    HudAnchor.TOP_RIGHT, HudAnchor.MIDDLE_RIGHT, HudAnchor.BOTTOM_RIGHT -> -1
                    else -> 1
                }
                val ySign = when (state.anchor) {
                    HudAnchor.BOTTOM_LEFT, HudAnchor.BOTTOM_CENTER, HudAnchor.BOTTOM_RIGHT -> -1
                    else -> 1
                }
                state.offsetX += xSign * dragX.toInt()
                state.offsetY += ySign * dragY.toInt()
                rebuildBoxes()
                return true
            }
        }
        return super.mouseDragged(event, dragX, dragY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (draggingId != null) {
            draggingId = null
            Saibon.config.save()
            return true
        }
        return super.mouseReleased(event)
    }

    override fun extractRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        extractor.fill(0, 0, width, height, 0xA0000000.toInt())

        val font = Minecraft.getInstance().font
        for (box in boxes) {
            val outlineColor = if (box.module.id == selectedId) Panel.ACCENT else Panel.BORDER_LIGHT
            extractor.fill(box.x, box.y, box.x + box.w, box.y + box.h, Panel.SELECTED_BACKGROUND)
            extractor.outline(box.x, box.y, box.w, box.h, outlineColor)
            extractor.text(font, box.module.title, box.x + 2, box.y + 2, 0xFFFFFFFF.toInt(), true)
        }

        super.extractRenderState(extractor, mouseX, mouseY, delta)

        extractor.text(font, "Drag a box to reposition it. Click a box to edit its anchor/scale.", 8, 8, 0xFFE0E0E0.toInt(), true)
        if (boxes.isEmpty()) {
            extractor.text(font, "No HUD modules registered yet.", 8, 24, 0xFFA0A0A0.toInt(), true)
        }
    }

    override fun isPauseScreen(): Boolean = false
}
