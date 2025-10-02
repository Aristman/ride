package ru.marslab.ide.ride.agent.validation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ru.marslab.ide.ride.model.ParsedResponse
import ru.marslab.ide.ride.model.ResponseFormat
import ru.marslab.ide.ride.model.ResponseSchema

/**
 * Простейшая валидация JSON: сверяем, что в ответе присутствуют поля из схемы-примера
 * и типы базово совпадают. Схема ожидается как пример JSON структуры.
 */
class JsonResponseValidator : ResponseValidator {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun validate(parsed: ParsedResponse, schema: ResponseSchema): String? {
        if (schema.format != ResponseFormat.JSON) return null
        if (parsed !is ParsedResponse.JsonResponse) return "Неверный тип распарсенного ответа: ожидается JSON"
        if (schema.schemaDefinition.isBlank()) return null // ничего валидировать

        return try {
            val schemaJson: JsonElement = json.parseToJsonElement(schema.schemaDefinition)
            validateAgainstExample(schemaJson, parsed.jsonElement)
        } catch (e: Exception) {
            // Если схема невалидна как JSON — предупреждаем, но не валим ответ
            null
        }
    }

    private fun validateAgainstExample(example: JsonElement, actual: JsonElement, path: String = "$"): String? {
        if (example is JsonObject && actual is JsonObject) {
            for ((k, v) in example) {
                val child = actual[k] ?: return "Отсутствует поле '$path.$k'"
                val err = validateAgainstExample(v, child, "$path.$k")
                if (err != null) return err
            }
            return null
        }
        if (example is JsonPrimitive && actual is JsonPrimitive) {
            // Базовая проверка типа
            val expType = primitiveType(example)
            val actType = primitiveType(actual)
            if (expType != actType) return "Несовпадение типа в '$path': ожидается $expType, получено $actType"
            return null
        }
        // Поддержка массивов и смешанных типов упрощена — пропускаем строгую проверку
        return null
    }

    private fun primitiveType(p: JsonPrimitive): String = when {
        p.isString -> "string"
        p.booleanOrNull != null -> "boolean"
        p.longOrNull != null || p.doubleOrNull != null -> "number"
        else -> "unknown"
    }
}
