package ru.marslab.ide.ride.ui

import ru.marslab.ide.ride.model.XmlResponseData
import ru.marslab.ide.ride.model.JsonResponseData

/**
 * Форматирует ответы от агента для отображения в UI с учетом схемы ответа
 */
object ResponseFormatter {

    /**
     * Форматирует данные XmlResponseData для отображения с уточняющими вопросами
     */
    fun formatXmlResponseData(data: XmlResponseData): String {
        return buildString {
            if (data.isFinal) {
                // Окончательный ответ - только сообщение
                append(data.message)
            } else {
                // Уточняющий ответ - сообщение + вопросы

                // Добавляем заголовок
                append("❓ **Требуются уточнения**\n\n")

                // Добавляем основное сообщение если есть
                if (data.message.isNotBlank()) {
                    append(data.message.trim())
                    append("\n\n")
                }

                // Добавляем пояснение если есть
                data.reasoning?.let { reasoning ->
                    if (reasoning.isNotBlank()) {
                        append("*$reasoning*\n\n")
                    }
                }

                // Добавляем вопросы если есть
                if (data.clarifyingQuestions.isNotEmpty()) {
                    append("**Уточняющие вопросы:**\n")
                    data.clarifyingQuestions.forEachIndexed { index, question ->
                        append("${index + 1}. $question\n")
                    }
                }
            }
        }.trim()
    }

    /**
     * Форматирует данные JsonResponseData для отображения с уточняющими вопросами
     */
    fun formatJsonResponseData(data: JsonResponseData): String {
        return buildString {
            if (data.isFinal) {
                // Окончательный ответ - только сообщение
                append(data.message)
            } else {
                // Уточняющий ответ - сообщение + вопросы

                // Добавляем заголовок
                append("❓ **Требуются уточнения**\n\n")

                // Добавляем основное сообщение если есть
                if (data.message.isNotBlank()) {
                    append(data.message.trim())
                    append("\n\n")
                }

                // Добавляем пояснение если есть
                data.reasoning?.let { reasoning ->
                    if (reasoning.isNotBlank()) {
                        append("*$reasoning*\n\n")
                    }
                }

                // Добавляем вопросы если есть
                if (data.clarifyingQuestions.isNotEmpty()) {
                    append("**Уточняющие вопросы:**\n")
                    data.clarifyingQuestions.forEachIndexed { index, question ->
                        append("${index + 1}. $question\n")
                    }
                }
            }
        }.trim()
    }
}