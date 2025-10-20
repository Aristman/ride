package ru.marslab.ide.ride.model.agent

/**
 * Контейнер для форматированного вывода агента
 *
 * @property blocks Список форматированных блоков
 * @property rawContent Сырой контент для fallback (опционально)
 */
data class FormattedOutput(
    val blocks: List<FormattedOutputBlock>,
    val rawContent: String? = null
) {
    companion object {
        /**
         * Создает вывод с одним блоком
         */
        fun single(block: FormattedOutputBlock): FormattedOutput {
            return FormattedOutput(listOf(block))
        }

        /**
         * Создает вывод с несколькими блоками
         */
        fun multiple(blocks: List<FormattedOutputBlock>): FormattedOutput {
            return FormattedOutput(blocks.sortedBy { it.order })
        }

        /**
         * Создает вывод из markdown контента
         */
        fun markdown(content: String): FormattedOutput {
            return single(FormattedOutputBlock.markdown(content))
        }

        /**
         * Создает пустой вывод
         */
        fun empty(): FormattedOutput {
            return FormattedOutput(emptyList())
        }
    }

    /**
     * Получить блоки определенного типа
     */
    fun getBlocksByType(type: AgentOutputType): List<FormattedOutputBlock> {
        return blocks.filter { it.type == type }
    }

    /**
     * Получить первый блок определенного типа
     */
    fun getFirstBlockByType(type: AgentOutputType): FormattedOutputBlock? {
        return blocks.find { it.type == type }
    }

    /**
     * Проверить, содержит ли вывод блоки определенного типа
     */
    fun hasBlockType(type: AgentOutputType): Boolean {
        return blocks.any { it.type == type }
    }

    /**
     * Получить общее количество блоков
     */
    fun getBlockCount(): Int = blocks.size

    /**
     * Проверить, является ли вывод пустым
     */
    fun isEmpty(): Boolean = blocks.isEmpty() && rawContent.isNullOrEmpty()

    /**
     * Проверить, содержит ли вывод какие-либо данные
     */
    fun isNotEmpty(): Boolean = !isEmpty()
}