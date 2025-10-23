package ru.marslab.ide.ride.model.agent

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Сериализатор для Map<String, Any>, который сохраняет только примитивные типы
 */
object AnyMapSerializer : KSerializer<Map<String, Any>> {
    private val delegateSerializer = MapSerializer(String.serializer(), JsonElement.serializer())
    
    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, Any>) {
        val jsonMap = value.mapNotNull { (key, v) ->
            when (v) {
                is String -> key to JsonPrimitive(v)
                is Number -> key to JsonPrimitive(v)
                is Boolean -> key to JsonPrimitive(v)
                else -> null // Игнорируем сложные объекты
            }
        }.toMap()
        
        encoder.encodeSerializableValue(delegateSerializer, jsonMap)
    }

    override fun deserialize(decoder: Decoder): Map<String, Any> {
        val jsonMap = decoder.decodeSerializableValue(delegateSerializer)
        return jsonMap.mapValues { (_, jsonElement) ->
            when (val primitive = jsonElement as? JsonPrimitive) {
                null -> jsonElement.toString()
                else -> when {
                    primitive.isString -> primitive.content
                    primitive.content == "true" -> true
                    primitive.content == "false" -> false
                    primitive.content.toLongOrNull() != null -> primitive.content.toLong()
                    primitive.content.toDoubleOrNull() != null -> primitive.content.toDouble()
                    else -> primitive.content
                }
            }
        }
    }
}
