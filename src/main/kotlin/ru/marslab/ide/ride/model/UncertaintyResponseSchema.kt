package ru.marslab.ide.ride.model

/**
 * Схема ответа для агента с анализом неопределенности
 */
object UncertaintyResponseSchema {

    /**
     * Создает JSON схему для ответов с анализом неопределенности
     */
    fun createJsonSchema(): ResponseSchema {
        val schemaJson = """
            {
              "type": "object",
              "properties": {
                "isFinal": {
                  "type": "boolean",
                  "description": "Флаг окончательного ответа (true = ответ полный, false = требуются уточнения)"
                },
                "uncertainty": {
                  "type": "number",
                  "minimum": 0.0,
                  "maximum": 1.0,
                  "description": "Уровень неопределенности ответа (0.0 - 1.0)"
                },
                "clarifyingQuestions": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  },
                  "description": "Список уточняющих вопросов, если isFinal = false"
                },
                "message": {
                  "type": "string",
                  "description": "Основное сообщение от агента"
                },
                "reasoning": {
                  "type": "string",
                  "description": "Пояснение причины неопределенности (опционально)"
                }
              },
              "required": ["isFinal", "message"],
              "additionalProperties": false
            }
        """.trimIndent()

        val description = """
            Структурируй ответ в JSON формате с анализом неопределенности:

            - isFinal: true/false - является ли ответ окончательным
            - uncertainty: число от 0.0 до 1.0 - уровень неопределенности
            - clarifyingQuestions: массив строк - уточняющие вопросы (если нужно)
            - message: string - основное сообщение пользователю
            - reasoning: string - пояснение причины неопределенности (опционально)

            Пример ответа с уточнениями:
            {
              "isFinal": false,
              "uncertainty": 0.3,
              "clarifyingQuestions": ["Какую версию Kotlin вы используете?", "Это веб-приложение или мобильное?"],
              "message": "Давайте уточню несколько деталей, чтобы дать точный ответ",
              "reasoning": "Недостаточно информации о технологическом стеке"
            }

            Пример окончательного ответа:
            {
              "isFinal": true,
              "uncertainty": 0.05,
              "clarifyingQuestions": [],
              "message": "Вот решение вашей проблемы...",
              "reasoning": "Достаточно информации для полного ответа"
            }
        """.trimIndent()

        return ResponseSchema.json(schemaJson, description)
    }

    /**
     * Создает XML схему для ответов с анализом неопределенности
     */
    fun createXmlSchema(): ResponseSchema {
        val schemaXml = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="response">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="isFinal" type="xs:boolean"/>
                    <xs:element name="uncertainty" type="xs:decimal"/>
                    <xs:element name="clarifyingQuestions" minOccurs="0">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="question" type="xs:string" maxOccurs="unbounded"/>
                        </xs:sequence>
                      </xs:complexType>
                    </xs:element>
                    <xs:element name="message" type="xs:string"/>
                    <xs:element name="reasoning" type="xs:string" minOccurs="0"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
        """.trimIndent()

        val description = """
            Структурируй ответ в XML формате с анализом неопределенности:

            Пример:
            <response>
              <isFinal>false</isFinal>
              <uncertainty>0.3</uncertainty>
              <clarifyingQuestions>
                <question>Какую версию Kotlin вы используете?</question>
                <question>Это веб-приложение или мобильное?</question>
              </clarifyingQuestions>
              <message>Давайте уточню несколько деталей</message>
              <reasoning>Недостаточно информации о технологическом стеке</reasoning>
            </response>
        """.trimIndent()

        return ResponseSchema.xml(schemaXml, description)
    }
}