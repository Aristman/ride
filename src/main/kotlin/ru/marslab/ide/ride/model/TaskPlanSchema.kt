package ru.marslab.ide.ride.model

import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Схема для парсинга плана задач от PlannerAgent
 */
object TaskPlanSchema {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Создает JSON схему для плана задач
     */
    fun createJsonSchema(): ResponseSchema {
        val schemaDefinition = """
{
  "description": "Описание общей цели плана",
  "tasks": [
    {
      "id": 1,
      "title": "Название задачи",
      "description": "Описание задачи",
      "prompt": "Промпт для выполнения задачи"
    }
  ]
}
        """.trimIndent()
        
        return ResponseSchema(
            format = ResponseFormat.JSON,
            schemaDefinition = schemaDefinition,
            parseResponse = { content -> parseJsonPlan(content) }
        )
    }
    
    /**
     * Создает XML схему для плана задач
     */
    fun createXmlSchema(): ResponseSchema {
        val schemaDefinition = """
<plan>
  <description>Описание общей цели плана</description>
  <tasks>
    <task id="1">
      <title>Название задачи</title>
      <description>Описание задачи</description>
      <prompt>Промпт для выполнения задачи</prompt>
    </task>
  </tasks>
</plan>
        """.trimIndent()
        
        return ResponseSchema(
            format = ResponseFormat.XML,
            schemaDefinition = schemaDefinition,
            parseResponse = { content -> parseXmlPlan(content) }
        )
    }
    
    /**
     * Парсит план из JSON
     */
    private fun parseJsonPlan(content: String): ParsedResponse? {
        return try {
            // Извлекаем JSON из markdown блока если есть
            val jsonContent = extractJsonFromMarkdown(content)
            val plan = json.decodeFromString<TaskPlan>(jsonContent)
            TaskPlanData(plan)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Парсит план из XML
     */
    private fun parseXmlPlan(content: String): ParsedResponse? {
        return try {
            // Извлекаем XML из markdown блока если есть
            val xmlContent = extractXmlFromMarkdown(content)
            
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(xmlContent.byteInputStream())
            
            val root = doc.documentElement
            val description = root.getElementsByTagName("description").item(0)?.textContent ?: ""
            
            val tasksElement = root.getElementsByTagName("tasks").item(0) as? Element
            val taskNodes = tasksElement?.getElementsByTagName("task")
            
            val tasks = mutableListOf<TaskItem>()
            if (taskNodes != null) {
                for (i in 0 until taskNodes.length) {
                    val taskElement = taskNodes.item(i) as Element
                    val id = taskElement.getAttribute("id").toIntOrNull() ?: (i + 1)
                    val title = taskElement.getElementsByTagName("title").item(0)?.textContent ?: ""
                    val desc = taskElement.getElementsByTagName("description").item(0)?.textContent ?: ""
                    val prompt = taskElement.getElementsByTagName("prompt").item(0)?.textContent ?: ""
                    
                    tasks.add(TaskItem(id, title, desc, prompt))
                }
            }
            
            val plan = TaskPlan(description, tasks)
            TaskPlanData(plan)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Извлекает JSON из markdown блока
     */
    private fun extractJsonFromMarkdown(content: String): String {
        val jsonBlockRegex = Regex("```json\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = jsonBlockRegex.find(content)
        return match?.groupValues?.get(1)?.trim() ?: content.trim()
    }
    
    /**
     * Извлекает XML из markdown блока
     */
    private fun extractXmlFromMarkdown(content: String): String {
        val xmlBlockRegex = Regex("```xml\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = xmlBlockRegex.find(content)
        return match?.groupValues?.get(1)?.trim() ?: content.trim()
    }
}

/**
 * Распарсенный план задач
 */
data class TaskPlanData(
    val plan: TaskPlan
) : ParsedResponse
