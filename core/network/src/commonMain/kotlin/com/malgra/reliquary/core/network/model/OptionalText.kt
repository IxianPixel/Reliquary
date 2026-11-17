package com.malgra.reliquary.core.network.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Free text that Tube Archivist may send as a string, null, or `false` (older
 * data stores a missing description as `false`). Anything but a string is null.
 */
object OptionalTextSerializer : KSerializer<String?> {
    private val delegate = String.serializer().nullable

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): String? {
        val json = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val element = json.decodeJsonElement()
        return (element as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    override fun serialize(encoder: Encoder, value: String?) = delegate.serialize(encoder, value)
}
