package ru.marslab.ide.ride.stt.infrastructure

import com.intellij.openapi.components.service
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.marslab.ide.ride.settings.PluginSettings
import ru.marslab.ide.ride.stt.domain.SpeechToTextService
import ru.marslab.ide.ride.stt.domain.SttConfig
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class YandexSpeechSttService : SpeechToTextService {

    private val json = Json { ignoreUnknownKeys = true }
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun recognizeLpcm(audioBytes: ByteArray, config: SttConfig): Result<String> {
        if (audioBytes.isEmpty()) return Result.failure(IllegalArgumentException("Empty audio"))
        val settings = service<PluginSettings>()
        val apiKey = settings.getApiKey().trim()
        val folderId = settings.folderId.trim()
        if (apiKey.isBlank() || folderId.isBlank()) {
            return Result.failure(IllegalStateException("Yandex API Key or Folder ID is missing in settings"))
        }

        val query = buildString {
            append("lang=").append(config.lang)
            append("&format=lpcm")
            append("&sampleRateHertz=").append(config.sampleRateHertz)
        }
        val uri = URI.create("https://stt.api.cloud.yandex.net/speech/v1/stt:recognize?${query}")
        val req = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Api-Key $apiKey")
            .header("x-folder-id", folderId)
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(audioBytes))
            .build()
        return runCatching {
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) {
                throw IllegalStateException("STT error ${resp.statusCode()}: ${resp.body()}")
            }
            val root = json.parseToJsonElement(resp.body()).jsonObject
            val result = root["result"]?.jsonPrimitive?.contentOrNull
            if (result.isNullOrBlank()) throw IllegalStateException("STT: empty result")
            result
        }
    }
}
