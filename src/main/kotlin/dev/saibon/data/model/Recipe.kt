package dev.saibon.data.model

enum class RecipeType { CRAFTING, FORGE, NPC }

data class RecipeIngredient(
    val itemId: String = "",
    val amount: Int = 1
)

/** One entry from the `recipes` dataset, keyed by [itemId] (the crafted result). */
data class Recipe(
    val itemId: String = "",
    val type: RecipeType = RecipeType.CRAFTING,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val resultCount: Int = 1,
    /** NPC-recipe purchase cost, or — reused for FORGE recipes — an extra coin
     * cost on top of [ingredients] for the handful that need one (e.g. Travel Scrolls). */
    val npcCost: Double? = null,
    /** Forge processing time in seconds, wiki-sourced. Null for non-FORGE recipes. */
    val durationSeconds: Int? = null
)
