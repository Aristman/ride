package ru.marslab.ide.ride.stt

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.marslab.ide.ride.stt.domain.SttConfig
import ru.marslab.ide.ride.stt.infrastructure.YandexSpeechSttService
import ru.marslab.ide.ride.settings.PluginSettings
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture

class YandexSpeechSttServiceTest {

    private class StubResponse(private val code: Int, private val bodyStr: String) : HttpResponse<String> {
        override fun statusCode(): Int = code
        override fun body(): String = bodyStr
        override fun uri(): URI = URI.create("https://stt.api.cloud.yandex.net")
        override fun version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
        override fun request(): HttpRequest = HttpRequest.newBuilder().uri(uri()).build()
        override fun previousResponse(): Optional<HttpResponse<String>> = Optional.empty()
        override fun headers(): HttpHeaders = HttpHeaders.of(mapOf(), { _, _ -> true })
        override fun sslSession(): Optional<javax.net.ssl.SSLSession> = Optional.empty()
        // Не переопределяем trailers(), так как он имеет реализацию по умолчанию
    }

    private class StubHttpClient(private val resp: HttpResponse<String>) : HttpClient() {
        override fun <T> send(request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
            @Suppress("UNCHECKED_CAST")
            return resp as HttpResponse<T>
        }
        override fun <T> sendAsync(request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler<T>): CompletableFuture<HttpResponse<T>> {
            return CompletableFuture.failedFuture(UnsupportedOperationException())
        }
        override fun cookieHandler(): Optional<java.net.CookieHandler> = Optional.empty()
        override fun connectTimeout(): Optional<Duration> = Optional.of(Duration.ofSeconds(1))
        override fun followRedirects(): Redirect = Redirect.NEVER
        override fun version(): Version = Version.HTTP_1_1
        override fun proxy(): Optional<java.net.ProxySelector> = Optional.empty()
        override fun authenticator(): Optional<java.net.Authenticator> = Optional.empty()
        override fun sslContext(): javax.net.ssl.SSLContext = javax.net.ssl.SSLContext.getDefault()
        override fun sslParameters(): javax.net.ssl.SSLParameters = javax.net.ssl.SSLParameters()
        override fun executor(): Optional<java.util.concurrent.Executor> = Optional.empty()
    }

    private fun settingsProvider(apiKey: String = "test-key", folderId: String = "test-folder"): () -> PluginSettings {
        return {
            val s = PluginSettings()
            s.saveApiKey(apiKey)
            s.folderId = folderId
            s
        }
    }

    @Test
    fun `recognizeLpcm returns failure on empty audio`() {
        val client = StubHttpClient(StubResponse(200, buildJsonObject { put("result", "") }.toString()))
        val service = YandexSpeechSttService(client, settingsProvider())
        val res = service.recognizeLpcm(byteArrayOf(), SttConfig())
        assertTrue(res.isFailure)
    }

    @Test
    fun `recognizeLpcm parses success response`() {
        val json = buildJsonObject { put("result", "привет мир") }.toString()
        val client = StubHttpClient(StubResponse(200, json))
        val service = YandexSpeechSttService(client, settingsProvider())
        val fakePcm = ByteArray(3200) { 0 } // 100ms тишины
        val res = service.recognizeLpcm(fakePcm, SttConfig())
        assertTrue(res.isSuccess)
        assertEquals("привет мир", res.getOrNull())
    }
}
