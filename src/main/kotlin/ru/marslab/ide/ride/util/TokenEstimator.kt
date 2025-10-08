package ru.marslab.ide.ride.util

/**
 * Утилита для приблизительной оценки количества токенов в тексте
 * 
 * Использует упрощенную эвристику:
 * - 1 токен ≈ 4 символа для английского текста
 * - 1 токен ≈ 2-3 символа для русского текста (используем среднее 2.5)
 * - Учитываем пробелы и знаки препинания
 */
object TokenEstimator {
    
    /**
     * Оценивает количество токенов в тексте
     * 
     * @param text Текст для оценки
     * @return Приблизительное количество токенов
     */
    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        
        // Подсчитываем количество слов (разделенных пробелами)
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val wordCount = words.size
        
        // Подсчитываем количество символов
        val charCount = text.length
        
        // Определяем долю кириллических символов
        val cyrillicCount = text.count { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
        val cyrillicRatio = if (charCount > 0) cyrillicCount.toDouble() / charCount else 0.0
        
        // Используем разные коэффициенты для разных языков
        val estimatedTokens = if (cyrillicRatio > 0.3) {
            // Преимущественно русский текст: ~2.5 символа на токен
            (charCount / 2.5).toInt()
        } else {
            // Преимущественно английский текст: ~4 символа на токен
            (charCount / 4.0).toInt()
        }
        
        // Возвращаем максимум из двух оценок (по словам и по символам)
        // Это дает более точную оценку для коротких текстов
        return maxOf(estimatedTokens, wordCount)
    }
    
    /**
     * Оценивает токены для запроса и ответа вместе
     * 
     * @param request Текст запроса
     * @param response Текст ответа
     * @return Общее приблизительное количество токенов
     */
    fun estimateTotalTokens(request: String, response: String): Int {
        return estimateTokens(request) + estimateTokens(response)
    }
}
