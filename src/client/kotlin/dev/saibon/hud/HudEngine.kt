package dev.saibon.hud

import dev.saibon.core.Saibon
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.resources.Identifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Saibon's in-world HUD engine: every enabled/toggleable overlay (fairy soul
 * waypoints, dungeon minimap, farming/mining/slayer/pet/fishing/combat
 * trackers, the flip-alert toast, ...) registers one [HudModule] here instead
 * of each hooking its own Fabric HUD-layer element, so there's a single place
 * that applies the user's saved position/scale/enabled state
 * (`HudConfig`/`HudElementState`) and a single edit-mode screen
 * (`HudEditScreen`) that can reposition all of them. Registers exactly one
 * root element with Fabric API's real HUD-layer registry
 * (`net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry`,
 * confirmed present in this project's `fabric-rendering-v1` dependency via
 * `javap` — this MC version's replacement for the old `HudRenderCallback`).
 */
object HudEngine {
    private val ROOT_ID = Identifier.fromNamespaceAndPath(Saibon.MOD_ID, "hud_root")

    private val modules = mutableListOf<HudModule>()
    private val initialized = AtomicBoolean(false)

    fun init() {
        if (!initialized.compareAndSet(false, true)) return

        HudElementRegistry.addLast(
            ROOT_ID,
            HudElement { extractor, delta ->
                for (module in modules) {
                    val state = stateFor(module)
                    if (!state.enabled) continue
                    runCatching { renderModule(module, state, extractor, delta) }
                        .onFailure { Saibon.logger.warn("Saibon HUD module '{}' failed to render, skipping this frame", module.id, it) }
                }
            }
        )
    }

    /** Registers a HUD module — called once per feature at client init, same shape as `SettingsRegistry.register`. */
    fun register(module: HudModule) {
        modules += module
    }

    fun allModules(): List<HudModule> = modules

    /** This module's saved state, or its own declared default on first use — never mutates config until the user actually changes something in `HudEditScreen`. */
    fun stateFor(module: HudModule): HudElementState =
        Saibon.config.data.hud.elements.getOrPut(module.id) { module.defaultState.copy() }

    /** Top-left screen-space corner [state]'s module content should be drawn at, given its (unscaled) [size] — anchors from whichever screen edge/center [HudAnchor] names, then applies the plain pixel [HudElementState.offsetX]/[offsetY]. */
    fun origin(state: HudElementState, screenWidth: Int, screenHeight: Int, size: HudSize): Pair<Int, Int> {
        val scaledWidth = (size.width * state.scale).toInt()
        val scaledHeight = (size.height * state.scale).toInt()
        val x = when (state.anchor) {
            HudAnchor.TOP_LEFT, HudAnchor.MIDDLE_LEFT, HudAnchor.BOTTOM_LEFT -> state.offsetX
            HudAnchor.TOP_CENTER, HudAnchor.CENTER, HudAnchor.BOTTOM_CENTER -> (screenWidth - scaledWidth) / 2 + state.offsetX
            HudAnchor.TOP_RIGHT, HudAnchor.MIDDLE_RIGHT, HudAnchor.BOTTOM_RIGHT -> screenWidth - scaledWidth - state.offsetX
        }
        val y = when (state.anchor) {
            HudAnchor.TOP_LEFT, HudAnchor.TOP_CENTER, HudAnchor.TOP_RIGHT -> state.offsetY
            HudAnchor.MIDDLE_LEFT, HudAnchor.CENTER, HudAnchor.MIDDLE_RIGHT -> (screenHeight - scaledHeight) / 2 + state.offsetY
            HudAnchor.BOTTOM_LEFT, HudAnchor.BOTTOM_CENTER, HudAnchor.BOTTOM_RIGHT -> screenHeight - scaledHeight - state.offsetY
        }
        return x to y
    }

    private fun renderModule(
        module: HudModule,
        state: HudElementState,
        extractor: net.minecraft.client.gui.GuiGraphicsExtractor,
        delta: net.minecraft.client.DeltaTracker
    ) {
        val size = module.measure()
        val (x, y) = origin(state, extractor.guiWidth(), extractor.guiHeight(), size)
        val pose = extractor.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(state.scale)
        module.render(extractor, delta)
        pose.popMatrix()
    }
}
