package dev.saibon.update

/** Trivial `major.minor.patch` comparator for the `X.Y.Z` tag scheme this project already uses. */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        fun parse(text: String): SemVer? {
            val parts = text.trim().removePrefix("v").split(".")
            if (parts.size != 3) return null
            val (major, minor, patch) = parts.map { it.toIntOrNull() ?: return null }
            return SemVer(major, minor, patch)
        }
    }
}
