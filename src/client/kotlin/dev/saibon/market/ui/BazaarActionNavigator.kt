package dev.saibon.market.ui

import dev.saibon.core.Saibon
import dev.saibon.mixin.AbstractContainerScreenAccessor
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One click in a [BazaarActionNavigator] sequence: the screen it expects to land on, and how to find/click the target slot within it — never a fixed slot index, always a live text/lore scan via [slotMatcher]. */
data class ClickStep(
    val expectedTitlePattern: Regex?,
    val description: String,
    val clickType: ContainerInput = ContainerInput.PICKUP,
    val button: Int = 0,
    val slotMatcher: (Slot) -> Boolean
)

/**
 * Drives a sequence of real clicks through the Bazaar's own multi-screen
 * flow (each action — Buy Instantly, Create Buy Order, etc. — opens a new
 * server-assigned screen). Every step re-scans the newly-opened screen's
 * actual slots for one matching [ClickStep.slotMatcher] and verifies the
 * screen's title against [ClickStep.expectedTitlePattern] before clicking —
 * never a fixed slot index or an assumed screen — and the whole sequence
 * aborts with a clear message if either check fails. That title/slot
 * verification at every step is this navigator's only guard against a
 * desynced sequence (e.g. the player closed the menu mid-flow and something
 * unrelated opened next); it does not separately try to distinguish "our
 * own synthesized click" from a genuine user click, since the verification
 * above already catches acting on the wrong screen.
 *
 * Real clicks go through [AbstractContainerScreenAccessor.invokeSlotClicked]
 * (see that mixin's doc comment for why `slotClicked` needs an invoker).
 *
 * Dry-run mode only verifies and logs the *first* step against the real,
 * already-open screen — later steps' screens don't exist yet without an
 * actual click, so they're logged as unverified previews rather than
 * pretending to validate something that was never opened.
 */
object BazaarActionNavigator {
    private val timeoutExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Saibon-BazaarActionNavigator-Timeout").apply { isDaemon = true }
    }

    private class Run(
        val steps: List<ClickStep>,
        val onLog: (String) -> Unit,
        val onComplete: () -> Unit,
        val onFail: (String) -> Unit
    ) {
        var index = 0
        var awaitingScreen = false
        var timeoutTask: ScheduledFuture<*>? = null
    }

    private val active = AtomicBoolean(false)
    private var current: Run? = null

    fun init() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            val run = current
            if (run != null && run.awaitingScreen && screen is AbstractContainerScreen<*>) {
                run.awaitingScreen = false
                run.timeoutTask?.cancel(false)
                handleScreen(run, screen)
            }
        }
    }

    fun run(initialScreen: AbstractContainerScreen<*>, steps: List<ClickStep>, onLog: (String) -> Unit, onComplete: () -> Unit, onFail: (String) -> Unit) {
        if (steps.isEmpty()) {
            onComplete()
            return
        }
        if (!active.compareAndSet(false, true)) {
            onFail("Another Bazaar action is already in progress.")
            return
        }
        current = Run(steps, onLog, onComplete, onFail)
        handleScreen(current!!, initialScreen)
    }

    fun runDryRun(initialScreen: AbstractContainerScreen<*>, steps: List<ClickStep>, onLog: (String) -> Unit, onComplete: () -> Unit, onFail: (String) -> Unit) {
        if (steps.isEmpty()) {
            onComplete()
            return
        }
        val first = steps.first()
        if (first.expectedTitlePattern != null && !first.expectedTitlePattern.containsMatchIn(initialScreen.title.string)) {
            onFail("Expected a menu matching \"${first.expectedTitlePattern}\" for \"${first.description}\", but saw \"${initialScreen.title.string}\".")
            return
        }
        val slot = initialScreen.menu.slots.firstOrNull { !it.item.isEmpty && first.slotMatcher(it) }
        if (slot == null) {
            onFail("Couldn't find the expected slot for \"${first.description}\" in \"${initialScreen.title.string}\".")
            return
        }
        onLog("[DRY RUN] Step 1/${steps.size} verified: would click slot ${slot.index} (\"${slot.item.hoverName.string}\") for \"${first.description}\".")
        for ((i, step) in steps.drop(1).withIndex()) {
            onLog("[DRY RUN] Step ${i + 2}/${steps.size} (unverified — only checked once step ${i + 1} actually opens it): ${step.description}")
        }
        onComplete()
    }

    private fun handleScreen(run: Run, screen: AbstractContainerScreen<*>) {
        val step = run.steps[run.index]
        run.onLog("Step ${run.index + 1}/${run.steps.size}: ${step.description}")

        if (step.expectedTitlePattern != null && !step.expectedTitlePattern.containsMatchIn(screen.title.string)) {
            fail(run, "Expected a menu matching \"${step.expectedTitlePattern}\" for \"${step.description}\", but saw \"${screen.title.string}\".")
            return
        }
        val slot = screen.menu.slots.firstOrNull { !it.item.isEmpty && step.slotMatcher(it) }
        if (slot == null) {
            fail(run, "Couldn't find the expected slot for \"${step.description}\" in \"${screen.title.string}\".")
            return
        }

        (screen as AbstractContainerScreenAccessor).invokeSlotClicked(slot, slot.index, step.button, step.clickType)
        advance(run)
    }

    private fun advance(run: Run) {
        run.index++
        if (run.index >= run.steps.size) {
            active.set(false)
            current = null
            run.onComplete()
            return
        }

        run.awaitingScreen = true
        val timeoutMs = Saibon.config.data.market.bazaarActionTimeoutMs.toLong()
        run.timeoutTask = timeoutExecutor.schedule({
            Minecraft.getInstance().execute {
                if (run.awaitingScreen) {
                    run.awaitingScreen = false
                    fail(run, "Timed out waiting for the next Bazaar menu to open.")
                }
            }
        }, timeoutMs, TimeUnit.MILLISECONDS)
    }

    private fun fail(run: Run, message: String) {
        run.timeoutTask?.cancel(false)
        active.set(false)
        current = null
        run.onFail(message)
    }
}
