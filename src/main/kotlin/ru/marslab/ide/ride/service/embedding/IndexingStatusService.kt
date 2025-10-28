package ru.marslab.ide.ride.service.embedding

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Глобальный сервис состояния индексации, чтобы UI мог восстанавливаться после закрытия окна настроек
 */
@Service(Level.APP)
class IndexingStatusService {

    data class Progress(
        val percent: Int = 0,
        val filesProcessed: Int = 0,
        val totalFiles: Int = 0,
        val currentFile: String = ""
    )

    interface Listener {
        fun onProgress(progress: Progress)
        fun onFinished() {}
        fun onStarted() {}
    }

    private val inProgressFlag = AtomicBoolean(false)
    @Volatile private var lastProgress: Progress = Progress()
    private val listeners = CopyOnWriteArrayList<Listener>()

    fun isInProgress(): Boolean = inProgressFlag.get()

    fun getLastProgress(): Progress = lastProgress

    fun setInProgress(value: Boolean) {
        val changed = inProgressFlag.getAndSet(value) != value
        if (changed) {
            if (value) notifyStarted() else notifyFinished()
        }
    }

    fun updateProgress(progress: Progress) {
        lastProgress = progress
        notifyProgress(progress)
    }

    fun addListener(listener: Listener, fireImmediate: Boolean = true) {
        listeners.add(listener)
        if (fireImmediate) {
            if (isInProgress()) listener.onStarted()
            listener.onProgress(lastProgress)
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyProgress(progress: Progress) {
        listeners.forEach { runCatching { it.onProgress(progress) } }
    }

    private fun notifyFinished() {
        listeners.forEach { runCatching { it.onFinished() } }
    }

    private fun notifyStarted() {
        listeners.forEach { runCatching { it.onStarted() } }
    }
}
