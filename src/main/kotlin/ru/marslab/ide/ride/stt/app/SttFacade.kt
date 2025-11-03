package ru.marslab.ide.ride.stt.app

import com.intellij.openapi.components.Service
import ru.marslab.ide.ride.stt.domain.SpeechToTextService
import ru.marslab.ide.ride.stt.domain.SttConfig
import ru.marslab.ide.ride.stt.infrastructure.AudioRecorder
import ru.marslab.ide.ride.stt.infrastructure.YandexSpeechSttService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application Service: фасад для записи и распознавания речи
 */
@Service(Service.Level.APP)
class SttFacade {
    private val recorder = AudioRecorder()
    private val sttService: SpeechToTextService = YandexSpeechSttService()
    private val isRecording = AtomicBoolean(false)

    fun startRecording(config: SttConfig = SttConfig()): Boolean {
        if (isRecording.get()) return false
        recorder.start()
        isRecording.set(true)
        return true
    }

    fun stopAndRecognize(config: SttConfig = SttConfig(lang = "ru-RU")): Result<String> {
        if (!isRecording.get()) return Result.failure(IllegalStateException("Not recording"))
        val bytes = recorder.stopAndGetBytes()
        isRecording.set(false)
        if (bytes.isEmpty()) return Result.failure(IllegalStateException("Empty audio buffer"))
        return sttService.recognizeLpcm(bytes, config)
    }

    fun cancel() {
        isRecording.set(false)
        recorder.cancel()
    }

    fun isRecording(): Boolean = isRecording.get()
}
