package com.malgra.reliquary.core.network

/** A Tube Archivist release version such as `v0.6.0`. */
data class TaVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<TaVersion> {
    override fun compareTo(other: TaVersion) =
        compareValuesBy(this, other, TaVersion::major, TaVersion::minor, TaVersion::patch)

    override fun toString() = "v$major.$minor.$patch"

    companion object {
        /** The oldest release whose API and media URLs Reliquary understands. */
        val MINIMUM = TaVersion(0, 6, 0)

        private val PATTERN = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?""")

        fun parse(raw: String): TaVersion? {
            val match = PATTERN.find(raw.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return TaVersion(major.toInt(), minor.toInt(), patch.toIntOrNull() ?: 0)
        }
    }
}
