package com.malgra.reliquary.core.network.model

import android.util.Log
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * A page of a list endpoint: `{"data": [...], "paginate": {...}}`.
 *
 * `GET /api/video/?watch=continue` answers with a bare `[]` when nothing is in
 * progress, so a JSON array is also accepted (with no pagination).
 *
 * The API is unversioned and its data can predate its own serializers, so an
 * item that can't be read is skipped (and logged) instead of failing the page.
 */
@Serializable(with = PageSerializer::class)
data class Page<T>(val data: List<T>, val paginate: PaginateDto?)

@Serializable
internal data class PageSurrogate<T>(
    val data: List<T> = emptyList(),
    val paginate: PaginateDto? = null,
)

class PageSerializer<T>(private val itemSerializer: KSerializer<T>) : KSerializer<Page<T>> {
    private val surrogate = PageSurrogate.serializer(itemSerializer)

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        SerialDescriptor("com.malgra.reliquary.core.network.model.Page", surrogate.descriptor)

    override fun deserialize(decoder: Decoder): Page<T> {
        val json = decoder as? JsonDecoder
            ?: return decoder.decodeSerializableValue(surrogate).let { Page(it.data, it.paginate) }
        return when (val element = json.decodeJsonElement()) {
            is JsonArray -> Page(json.json.decodeItems(element), null)
            is JsonObject -> Page(
                data = (element["data"] as? JsonArray)?.let { json.json.decodeItems(it) }.orEmpty(),
                paginate = element["paginate"]
                    ?.takeUnless { it is JsonNull }
                    ?.let { json.json.decodeFromJsonElement(PaginateDto.serializer(), it) },
            )
            else -> throw SerializationException("Expected a page object or an array")
        }
    }

    private fun Json.decodeItems(array: JsonArray): List<T> = array.mapNotNull { item ->
        try {
            decodeFromJsonElement(itemSerializer, item)
        } catch (e: SerializationException) {
            Log.w(TAG, "Skipped an unreadable ${itemSerializer.descriptor.serialName}", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Skipped an unreadable ${itemSerializer.descriptor.serialName}", e)
            null
        }
    }

    override fun serialize(encoder: Encoder, value: Page<T>) {
        encoder.encodeSerializableValue(surrogate, PageSurrogate(value.data, value.paginate))
    }

    private companion object {
        const val TAG = "Page"
    }
}
