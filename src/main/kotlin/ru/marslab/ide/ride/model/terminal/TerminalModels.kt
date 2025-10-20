package ru.marslab.ide.ride.model.terminal

/**
 * Модель для выполнения команд в терминале
 *
 * @property command Команда для выполнения
 * @property workingDir Рабочая директория (опционально)
 * @property timeout Таймаут выполнения в миллисекундах (по умолчанию 30000)
 * @property environmentVariables Переменные окружения (опционально)
 */
data class TerminalCommand(
    val command: String,
    val workingDir: String? = null,
    val timeout: Long = 30000L,
    val environmentVariables: Map<String, String> = emptyMap()
)

/**
 * Результат выполнения команды в терминале
 *
 * @property command Исходная команда
 * @property exitCode Код завершения (0 = успех)
 * @property stdout Стандартный вывод
 * @property stderr Вывод ошибок
 * @property executionTime Время выполнения в миллисекундах
 * @property success Флаг успешного выполнения
 */
data class TerminalCommandResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val executionTime: Long,
    val success: Boolean
) {
    companion object {
        /**
         * Создает успешный результат выполнения команды
         */
        fun success(
            command: String,
            stdout: String,
            stderr: String = "",
            executionTime: Long
        ): TerminalCommandResult {
            return TerminalCommandResult(
                command = command,
                exitCode = 0,
                stdout = stdout,
                stderr = stderr,
                executionTime = executionTime,
                success = true
            )
        }

        /**
         * Создает результат выполнения команды с ошибкой
         */
        fun error(
            command: String,
            exitCode: Int,
            stdout: String,
            stderr: String,
            executionTime: Long
        ): TerminalCommandResult {
            return TerminalCommandResult(
                command = command,
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
                executionTime = executionTime,
                success = false
            )
        }
    }
}

/**
 * Типы поддерживаемых команд
 */
enum class CommandType {
    SINGLE,     // Одна команда
    PIPELINE,   // Конвейер команд (cmd1 | cmd2)
    SCRIPT      // Скрипт (несколько команд)
}

/**
 * Режимы выполнения команд
 */
enum class ExecutionMode {
    SYNC,       // Синхронное выполнение
    ASYNC       // Асинхронное выполнение
}