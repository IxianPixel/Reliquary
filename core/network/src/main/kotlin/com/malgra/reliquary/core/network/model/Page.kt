package com.malgra.reliquary.core.network.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder

/**
 * A page of a list endpoint: `{"data": [...], "paginate": {...}}`.
 *
 * `GET /api/video/?watch=continue` answers with a bare `[]` when nothing is in
 * progress, so a JSON array is also accepted (with no pagination).
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
            is JsonArray -> Page(json.json.decodeFromJsonElement(ListSerializer(itemSerializer), element), null)
            else -> json.json.decodeFromJsonElement(surrogate, element).let { Page(it.data, it.paginate) }
        }
    }

    override fun serialize(encoder: Encoder, value: Page<T>) {
        encoder.encodeSerializableValue(surrogate, PageSurrogate(value.data, value.paginate))
    }
}
