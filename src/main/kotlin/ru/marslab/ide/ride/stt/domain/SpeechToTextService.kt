package ru.marslab.ide.ride.stt.domain

interface SpeechToTextService {
    fun recognizeLpcm(
        audioBytes: ByteArray,
        config: SttConfig = SttConfig()
    ): Result<String>
}
