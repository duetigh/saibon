package dev.saibon.ui.settings

import dev.saibon.ui.SaibonCategory

/**
 * Central registry of [SettingsSection]s, mirroring the registrar-list
 * pattern in [dev.saibon.core.command.CommandRegistry] so each feature module
 * can self-register a settings section without editing [dev.saibon.ui.screen.SaibonScreen].
 */
object SettingsRegistry {
    private val sections = mutableListOf<SettingsSection>()

    fun register(section: SettingsSection) {
        sections += section
    }

    fun sectionsFor(category: SaibonCategory): List<SettingsSection> =
        sections.filter { it.category == category }.sortedBy { it.order }
}
