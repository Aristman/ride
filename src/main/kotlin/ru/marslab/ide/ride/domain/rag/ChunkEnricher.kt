package ru.marslab.ide.ride.domain.rag

/**
 * Обогащает выбранные чанки данными из файловой системы (MCP) или иных источников.
 */
interface ChunkEnricher {
    /**
     * Возвращает обогащённые чанки для дальнейшего включения в промпт.
     */
    suspend fun enrich(chunkIds: List<String>): List<EnrichedChunk>
}
