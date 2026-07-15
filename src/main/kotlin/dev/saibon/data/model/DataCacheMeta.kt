package dev.saibon.data.model

/** Persisted alongside the cached dataset files, recording which [DatasetEntry.version] each one is. */
data class DataCacheMeta(
    val versions: Map<String, Int> = emptyMap()
)
