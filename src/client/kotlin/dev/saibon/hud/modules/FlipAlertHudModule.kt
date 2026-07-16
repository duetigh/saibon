package dev.saibon.hud.modules

import dev.saibon.core.Saibon
import dev.saibon.hud.HudAnchor
import dev.saibon.hud.HudElementState
import dev.saibon.hud.HudModule
import dev.saibon.hud.HudSize
import dev.saibon.itemlist.ItemIcons
import dev.saibon.itemlist.RarityColors
import dev.saibon.market.flip.FlipCandidate
import dev.saibon.market.flip.FlipEngine
import dev.saibon.ui.style.Panel
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The pasted spec's §4.3 "toast notification ... when a high-confidence flip
 * appears" — the first real [HudModule], proving `HudEngine` end-to-end.
 * Read-only: shows the item/profit for a few seconds, never buys/bids
 * anything itself — that's still `FlipScreen`'s "copy /viewauction command"
 * button, clicked by the player. Sound alert deferred: this pass only adds
 * the visual toast, since the exact notify-sound API wasn't verified against
 * this MC build (unlike the HUD/chat APIs above, which were checked via
 * `javap` before use).
 */
object FlipAlertHudModule : HudModule {
    override val id = "flip_alert_toast"
    override val title = "Flip Alert Toast"
    override val defaultState = HudElementState(anchor = HudAnchor.BOTTOM_RIGHT, offsetX = 8, offsetY = 8)

    private const val WIDTH = 170
    private const val HEIGHT = 32

    private var active: FlipCandidate? = null
    private var expiresAtMillis: Long = 0
    private val initialized = AtomicBoolean(false)

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        FlipEngine.onNewCandidate { candidate -> maybeShow(candidate) }
    }

    private fun maybeShow(candidate: FlipCandidate) {
        val config = Saibon.config.data.flip
        if (!config.alertEnabled) return
        if (candidate.estimatedProfit < config.alertMinProfit) return
        if (candidate.marginPercent < config.alertMinMarginPercent) return

        active = candidate
        expiresAtMillis = System.currentTimeMillis() + config.alertDisplaySeconds * 1000L
    }

    override fun measure(): HudSize = HudSize(WIDTH, HEIGHT)

    override fun render(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        val candidate = active ?: return
        if (System.currentTimeMillis() > expiresAtMillis) {
            active = null
            return
        }

        Panel.draw(extractor, 0, 0, WIDTH, HEIGHT, Panel.SELECTED_BACKGROUND)
        val font = Minecraft.getInstance().font
        extractor.item(ItemIcons.stackFor(candidate.item), 4, (HEIGHT - 16) / 2)
        extractor.text(font, candidate.item.name, 24, 5, RarityColors.of(candidate.item.tier), true)
        extractor.text(
            font,
            "+%,.0f coins (%.1f%%)".format(candidate.estimatedProfit, candidate.marginPercent),
            24, 17, 0xFF55FF55.toInt(), true
        )
    }
}
