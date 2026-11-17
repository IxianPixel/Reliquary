package com.malgra.reliquary.core.network

import kotlinx.serialization.json.Json

/** How every Tube Archivist response is read: the API adds fields and sends nulls freely. */
val TubeArchivistJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
