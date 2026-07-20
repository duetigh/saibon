package dev.saibon.market.model

/**
 * Wire format of `https://api.hypixel.net/v2/resources/skyblock/election` —
 * public, keyless. Shape confirmed live (`curl`, not guessed): `{success,
 * mayor: {key, name, perks: [{name, description}], minister: {key, name,
 * perk}}}`. Only `mayor.name`/`mayor.perks` are consumed
 * ([dev.saibon.market.MayorRepository]) — the current mayor at verification
 * time wasn't Derpy, so his specific `key`/shape when active couldn't be
 * confirmed live; detection is name-based for that reason (see
 * [dev.saibon.market.MayorRepository.isDerpyActive]).
 */
data class ElectionResponse(
    val success: Boolean = false,
    val mayor: MayorEntry? = null
)

data class MayorEntry(
    val key: String = "",
    val name: String = "",
    val perks: List<PerkEntry> = emptyList()
)

data class PerkEntry(
    val name: String = "",
    val description: String = ""
)
