package ru.marslab.ide.ride.model.chat

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Контекст для обработки запроса агентом
 *
 * @property project Текущий проект IntelliJ IDEA
 * @property history История предыдущих сообщений
 * @property currentFile Текущий открытый файл (опционально)
 * @property selectedText Выделенный текст в редакторе (опционально)
 * @property additionalContext Дополнительный контекст для агента
 */
data class ChatContext(
    val project: Project,
    val history: List<Message> = emptyList(),
    val currentFile: VirtualFile? = null,
    val selectedText: String? = null,
    val additionalContext: Map<String, Any> = emptyMap()
) {
    /**
     * Возвращает последние N сообщений из истории
     */
    fun getRecentHistory(count: Int): List<Message> {
        return history.takeLast(count)
    }
    
    /**
     * Проверяет, есть ли выделенный текст
     */
    fun hasSelectedText(): Boolean = !selectedText.isNullOrBlank()
    
    /**
     * Проверяет, есть ли текущий файл
     */
    fun hasCurrentFile(): Boolean = currentFile != null
}
