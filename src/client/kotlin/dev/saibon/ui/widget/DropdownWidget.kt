package dev.saibon.ui.widget

import dev.saibon.ui.style.Panel
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A real dropdown menu: click the button to open a popup list positioned
 * under it (or above it, if there isn't room below), click a row to select
 * it and close the popup. The popup is capped at [MAX_VISIBLE_ROWS] rows —
 * only that many row widgets ever exist at once, showing a virtualized
 * window into [options] that slides via [DropdownButtonWidget.scrollOffset]
 * on mouse-wheel or scrollbar-drag input, so a huge option list (e.g. the
 * item-list category filter) can't blow the popup past screen bounds.
 * Popup rows/scrollbar are injected into [screen]'s own widget list
 * ([Screens.getWidgets], the same Fabric-API mechanism every overlay panel
 * in this codebase already uses to add widgets to a screen it doesn't own)
 * rather than opening a new screen — replacing the screen would force-close
 * a real, server-driven Hypixel menu for the overlay use sites (AH/Bazaar
 * panels), which is not safe to do from a settings widget.
 *
 * Because the popup is injected into a shared widget list rather than owning
 * its own z-order, vanilla's own hit-testing (`ContainerEventHandler.getChildAt`,
 * confirmed by decompiling 26.2's `ContainerEventHandler.class`) resolves
 * clicks by *first list match*, not by what's drawn on top — so a popup that
 * overlaps an earlier-added widget (e.g. an item grid tile beneath it) would
 * otherwise leak clicks/scrolls straight through to it. [DropdownButtonWidget]
 * fixes this by hooking `ScreenMouseEvents.allowMouseClick/Scroll/Drag`
 * (fired *before* vanilla's own routing) to claim input for its own popup
 * bounds and veto everything else underneath while open, and by closing on
 * an outside click. Combined with [Panel]'s panels now being fully opaque,
 * nothing beneath an open dropdown is visible *or* reachable through it.
 */
object DropdownWidget {
    private const val ROW_HEIGHT = 14
    private const val MAX_VISIBLE_ROWS = 10
    private const val SCROLLBAR_WIDTH = 4

    fun <T : Any> create(
        screen: Screen,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        label: Component,
        options: List<T>,
        initial: T,
        stringify: (T) -> Component,
        onChange: (T) -> Unit
    ): AbstractWidget = DropdownButtonWidget(screen, x, y, width, height, label, options, initial, stringify, onChange)

    private class DropdownButtonWidget<T : Any>(
        private val screen: Screen,
        x: Int, y: Int, width: Int, height: Int,
        private val label: Component,
        private val options: List<T>,
        initial: T,
        private val stringify: (T) -> Component,
        private val onChange: (T) -> Unit
    ) : AbstractWidget(x, y, width, height, label) {

        private var selected: T = initial
        private val popupWidgets = mutableListOf<AbstractWidget>()
        private val isOpen get() = popupWidgets.isNotEmpty()
        private val visibleRows = min(options.size, MAX_VISIBLE_ROWS)
        private val maxScroll = max(0, options.size - visibleRows)
        private var scrollOffset = 0
        private var popupTop = 0
        private var popupLeft = 0
        private var draggingScrollbar = false

        init {
            // Fabric's Event doesn't support unregistering, so these listeners live for the
            // screen's whole lifetime and no-op via the isOpen checks below when closed.
            ScreenMouseEvents.allowMouseClick(screen).register(ScreenMouseEvents.AllowMouseClick { _, event -> handleAllowClick(event) })
            ScreenMouseEvents.allowMouseScroll(screen).register(
                ScreenMouseEvents.AllowMouseScroll { _, mouseX, mouseY, _, scrollY -> handleAllowScroll(mouseX, mouseY, scrollY) }
            )
            ScreenMouseEvents.allowMouseDrag(screen).register(ScreenMouseEvents.AllowMouseDrag { _, event, _, _ -> handleAllowDrag(event) })
            ScreenMouseEvents.allowMouseRelease(screen).register(
                ScreenMouseEvents.AllowMouseRelease { _, _ -> draggingScrollbar = false; true }
            )
        }

        override fun onClick(event: MouseButtonEvent, doubled: Boolean) {
            if (isOpen) closePopup() else openPopup()
        }

        /**
         * Fires before vanilla's own click routing. While the popup is open, a click that lands
         * on one of our own popup widgets is dispatched to it directly (bypassing vanilla's
         * first-match-in-list-order `getChildAt`, which would otherwise hand it to whatever
         * Saibon widget happens to have been added earlier at the same screen position) and
         * vetoed so the occluded widget underneath never also sees it. A click elsewhere closes
         * the popup but is left un-vetoed, so it still reaches its real target normally (e.g.
         * clicking straight from this popup into a sibling dropdown's toggle button).
         */
        private fun handleAllowClick(event: MouseButtonEvent): Boolean {
            if (!isOpen) return true
            val target = popupWidgets.firstOrNull { it.isMouseOver(event.x(), event.y()) }
            if (target != null) {
                target.mouseClicked(event, false)
                if (target is DropdownScrollbarWidget) draggingScrollbar = true
                return false
            }
            if (!isMouseOver(event.x(), event.y())) closePopup()
            return true
        }

        private fun handleAllowScroll(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
            if (!isOpen || scrollY == 0.0) return true
            if (popupWidgets.any { it.isMouseOver(mouseX, mouseY) }) {
                scrollBy(scrollY)
                return false
            }
            return true
        }

        private fun handleAllowDrag(event: MouseButtonEvent): Boolean {
            if (!draggingScrollbar) return true
            popupWidgets.filterIsInstance<DropdownScrollbarWidget>().firstOrNull()?.jumpTo(event.y())
            return false
        }

        private fun openPopup() {
            closePopup()
            val popupHeight = visibleRows * ROW_HEIGHT
            val openUpward = y + height + popupHeight > screen.height
            popupLeft = x
            popupTop = if (openUpward) y - popupHeight else y + height

            val selectedIndex = options.indexOf(selected)
            scrollOffset = if (selectedIndex < 0) 0 else (selectedIndex - visibleRows / 2).coerceIn(0, maxScroll)

            refreshPopupRows()
        }

        private fun refreshPopupRows() {
            val widgets = Screens.getWidgets(screen)
            popupWidgets.forEach { widgets.remove(it) }
            popupWidgets.clear()

            val rowWidth = if (maxScroll > 0) width - SCROLLBAR_WIDTH else width
            for (i in 0 until visibleRows) {
                val option = options[scrollOffset + i]
                val rowY = popupTop + i * ROW_HEIGHT
                val row = DropdownRowWidget(popupLeft, rowY, rowWidth, ROW_HEIGHT, stringify(option), option == selected) {
                    selected = option
                    onChange(option)
                    closePopup()
                }
                popupWidgets += row
                widgets += row
            }

            if (maxScroll > 0) {
                val scrollbar = DropdownScrollbarWidget(
                    popupLeft + rowWidth, popupTop, SCROLLBAR_WIDTH, visibleRows * ROW_HEIGHT,
                    totalOptions = options.size, visibleRows = visibleRows,
                    getScroll = { scrollOffset },
                    onJumpTo = { fraction -> setScroll((fraction * maxScroll).roundToInt()) }
                )
                popupWidgets += scrollbar
                widgets += scrollbar
            }
        }

        private fun scrollBy(wheelDelta: Double): Boolean {
            if (maxScroll <= 0) return false
            return setScroll(scrollOffset + if (wheelDelta > 0) -1 else 1)
        }

        private fun setScroll(newOffset: Int): Boolean {
            val clamped = newOffset.coerceIn(0, maxScroll)
            if (clamped == scrollOffset) return false
            scrollOffset = clamped
            refreshPopupRows()
            return true
        }

        private fun closePopup() {
            draggingScrollbar = false
            if (popupWidgets.isEmpty()) return
            val widgets = Screens.getWidgets(screen)
            popupWidgets.forEach { widgets.remove(it) }
            popupWidgets.clear()
        }

        override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            Panel.draw(extractor, x, y, width, height, if (isHovered || isOpen) Panel.HOVER_BACKGROUND else Panel.BACKGROUND)
            val font = Minecraft.getInstance().font
            extractor.text(font, "${label.string}: ", x + 4, y + (height - 8) / 2, 0xFFE0E0E0.toInt())
            val valueX = x + 4 + font.width("${label.string}: ")
            extractor.text(font, stringify(selected), valueX, y + (height - 8) / 2, 0xFFFFFFFF.toInt())
            extractor.text(font, if (isOpen) "^" else "v", x + width - 10, y + (height - 8) / 2, 0xFFA0A0A0.toInt())
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            output.add(NarratedElementType.TITLE, message)
        }
    }

    private class DropdownRowWidget(
        x: Int, y: Int, width: Int, height: Int,
        label: Component,
        private val selected: Boolean,
        private val onPick: () -> Unit
    ) : AbstractWidget(x, y, width, height, label) {

        override fun onClick(event: MouseButtonEvent, doubled: Boolean) = onPick()

        override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            val background = when {
                selected -> Panel.SELECTED_BACKGROUND
                isHovered -> Panel.HOVER_BACKGROUND
                else -> Panel.BACKGROUND
            }
            Panel.draw(extractor, x, y, width, height, background)
            extractor.text(Minecraft.getInstance().font, message, x + 4, y + (height - 8) / 2, 0xFFFFFFFF.toInt())
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            output.add(NarratedElementType.TITLE, message)
        }
    }

    /**
     * Vertical scrollbar drawn alongside a capped popup. Click/drag-to-jump is driven entirely
     * by [DropdownButtonWidget]'s `ScreenMouseEvents.allowMouseClick`/`allowMouseDrag` handlers
     * (which dispatch into [jumpTo] directly) rather than this widget's own `onClick`/`onDrag`,
     * since those handlers already intercept the click/drag before vanilla's own routing would
     * otherwise reach this widget — see the class doc on [DropdownWidget].
     */
    private class DropdownScrollbarWidget(
        x: Int, y: Int, width: Int, height: Int,
        private val totalOptions: Int,
        private val visibleRows: Int,
        private val getScroll: () -> Int,
        private val onJumpTo: (Double) -> Unit
    ) : AbstractWidget(x, y, width, height, Component.empty()) {

        private val thumbHeight = max(8, height * visibleRows / totalOptions)

        override fun onClick(event: MouseButtonEvent, doubled: Boolean) = jumpTo(event.y())

        fun jumpTo(mouseY: Double) {
            val usableTrack = (height - thumbHeight).coerceAtLeast(1)
            val relativeY = (mouseY - y - thumbHeight / 2.0).coerceIn(0.0, usableTrack.toDouble())
            onJumpTo(relativeY / usableTrack)
        }

        override fun extractWidgetRenderState(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
            extractor.fill(x, y, x + width, y + height, 0xFF1C1C1C.toInt())
            val maxScroll = totalOptions - visibleRows
            val thumbY = if (maxScroll <= 0) y else y + (height - thumbHeight) * getScroll() / maxScroll
            extractor.fill(x, thumbY, x + width, thumbY + thumbHeight, if (isHovered) 0xFFC0C0C0.toInt() else 0xFF909090.toInt())
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {}
    }
}
