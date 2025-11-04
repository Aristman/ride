package ru.marslab.ide.ride.stt.domain

/**
 * Конфигурация сервиса распознавания речи
 */
data class SttConfig(
    val lang: String = "ru-RU",
    val format: AudioFormat = AudioFormat.LPCM,
    val sampleRateHertz: Int = 16_000,
    val channels: Int = 1,
    val bitsPerSample: Int = 16
) {
    enum class AudioFormat { OGG_OPUS, LPCM }
}
