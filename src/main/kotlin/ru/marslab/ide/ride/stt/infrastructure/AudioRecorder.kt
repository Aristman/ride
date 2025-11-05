package ru.marslab.ide.ride.stt.infrastructure

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Простой рекордер через Java Sound API.
 * Пишет PCM 16kHz mono 16-bit, little-endian (LPCM), совместимо со STT v2.
 */
class AudioRecorder(
    private val sampleRate: Float = 16_000f,
    private val channels: Int = 1,
    private val bitsPerSample: Int = 16
) {
    private val isRecording = AtomicBoolean(false)
    private var line: TargetDataLine? = null
    private val bufferRef = AtomicReference<ByteArrayOutputStream?>(null)
    private var worker: Thread? = null

    fun start() {
        if (isRecording.get()) return
        val format = AudioFormat(
            sampleRate,
            bitsPerSample,
            channels,
            /* signed = */ true,
            /* bigEndian = */ false
        )
        val info = DataLine.Info(TargetDataLine::class.java, format)
        val targetLine = AudioSystem.getLine(info) as TargetDataLine
        targetLine.open(format)
        targetLine.start()
        line = targetLine
        isRecording.set(true)
        val baos = ByteArrayOutputStream()
        bufferRef.set(baos)
        worker = Thread {
            val buf = ByteArray(4096)
            try {
                while (isRecording.get()) {
                    val n = targetLine.read(buf, 0, buf.size)
                    if (n > 0) baos.write(buf, 0, n)
                }
            } catch (_: Exception) {
            } finally {
                runCatching { targetLine.stop() }
                runCatching { targetLine.close() }
            }
        }.apply { isDaemon = true; name = "AudioRecorder-Worker"; start() }
    }

    fun stopAndGetBytes(): ByteArray {
        if (!isRecording.get()) return ByteArray(0)
        isRecording.set(false)
        // Немедленно останавливаем и закрываем линию, чтобы разблокировать read()
        line?.run {
            runCatching { stop() }
            runCatching { close() }
        }
        line = null
        // Ждём завершения воркера надёжнее
        runCatching { worker?.join(1000) }
        worker = null
        val baos = bufferRef.getAndSet(null) ?: return ByteArray(0)
        return baos.toByteArray()
    }

    fun cancel() {
        isRecording.set(false)
        // Немедленно останавливаем и закрываем линию
        line?.run {
            runCatching { stop() }
            runCatching { close() }
        }
        line = null
        runCatching { worker?.join(1000) }
        worker = null
        bufferRef.getAndSet(null)
    }
}
