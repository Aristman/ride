package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.agent.a2a.*
import ru.marslab.ide.ride.model.orchestrator.AgentType
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * A2A агент для взаимодействия с пользователем
 *
 * Независимая реализация для взаимодействия с пользователем через
 * различные интерфейсы: консоль, диалоги, подтверждения и т.д.
 */
class A2AUserInteractionAgent : BaseA2AAgent(
    agentType = AgentType.USER_INTERACTION,
    a2aAgentId = "a2a-user-interaction-agent",
    supportedMessageTypes = setOf(
        "USER_INPUT_REQUEST",
        "USER_CONFIRMATION_REQUEST",
        "USER_SELECTION_REQUEST",
        "USER_NOTIFICATION_REQUEST",
        "USER_PROGRESS_REQUEST",
        "USER_FEEDBACK_REQUEST"
    ),
    publishedEventTypes = setOf(
        "TOOL_EXECUTION_STARTED",
        "TOOL_EXECUTION_COMPLETED",
        "TOOL_EXECUTION_FAILED",
        "USER_INTERACTION_INITIATED",
        "USER_INTERACTION_COMPLETED"
    )
) {

    // Хранилище для отложенных запросов и ответов
    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()
    private val userResponses = ConcurrentHashMap<String, UserResponse>()

    override suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        return try {
            when (request.messageType) {
                "USER_INPUT_REQUEST" -> handleUserInputRequest(request, messageBus)
                "USER_CONFIRMATION_REQUEST" -> handleUserConfirmationRequest(request, messageBus)
                "USER_SELECTION_REQUEST" -> handleUserSelectionRequest(request, messageBus)
                "USER_NOTIFICATION_REQUEST" -> handleUserNotificationRequest(request, messageBus)
                "USER_PROGRESS_REQUEST" -> handleUserProgressRequest(request, messageBus)
                "USER_FEEDBACK_REQUEST" -> handleUserFeedbackRequest(request, messageBus)
                else -> createErrorResponse(request.id, "Unsupported message type: ${request.messageType}")
            }
        } catch (e: Exception) {
            logger.error("Error in user interaction agent", e)
            createErrorResponse(request.id, "User interaction failed: ${e.message}")
        }
    }

    private suspend fun handleUserInputRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val prompt = data["prompt"] as? String ?: ""
        val inputType = data["input_type"] as? String ?: "text"
        val defaultValue = data["default_value"] as? String
        val validation = data["validation"] as? Map<String, Any>
        val timeout = data["timeout"] as? Long ?: 30000L // 30 секунд по умолчанию
        val multiline = data["multiline"] as? Boolean ?: false
        val secret = data["secret"] as? Boolean ?: false

        publishEvent(
            messageBus = messageBus,
            eventType = "USER_INTERACTION_INITIATED",
            payload = MessagePayload.CustomPayload(
                type = "USER_INPUT_STARTED",
                data = mapOf<String, Any>(
                    "request_id" to request.id,
                    "prompt" to prompt,
                    "input_type" to inputType,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        )

        val result = withContext(Dispatchers.Default) {
            requestUserInput(request.id, prompt, inputType, defaultValue, validation, timeout, multiline, secret)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "USER_INTERACTION_COMPLETED",
            payload = MessagePayload.CustomPayload(
                type = "USER_INPUT_COMPLETED",
                data = mapOf<String, Any>(
                    "request_id" to request.id,
                    "response_received" to result.success,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = result.success,
            payload = MessagePayload.CustomPayload(
                type = "USER_INPUT_RESULT",
                data = mapOf<String, Any>(
                    "user_input" to result.value,
                    "input_type" to inputType,
                    "validated" to result.validated,
                    "validation_errors" to result.validationErrors,
                    "metadata" to mapOf(
                        "agent" to "USER_INTERACTION",
                        "interaction_type" to "input",
                        "response_time_ms" to result.responseTime,
                        "timeout_used" to (result.responseTime >= timeout),
                        "default_used" to result.usedDefault
                    )
                )
            )
        )
    }

    private suspend fun handleUserConfirmationRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val message = data["message"] as? String ?: ""
        val defaultValue = data["default"] as? Boolean ?: false
        val timeout = data["timeout"] as? Long ?: 15000L // 15 секунд по умолчанию
        val options = data["options"] as? Map<String, String> ?: mapOf(
            "true" to "Yes",
            "false" to "No"
        )

        publishEvent(
            messageBus = messageBus,
            eventType = "USER_INTERACTION_INITIATED",
            payload = MessagePayload.CustomPayload(
                type = "USER_CONFIRMATION_STARTED",
                data = mapOf<String, Any>(
                    "request_id" to request.id,
                    "message" to message,
                    "default" to defaultValue,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        )

        val result = withContext(Dispatchers.Default) {
            requestUserConfirmation(request.id, message, defaultValue, timeout, options)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "USER_INTERACTION_COMPLETED",
            payload = MessagePayload.CustomPayload(
                type = "USER_CONFIRMATION_COMPLETED",
                data = mapOf<String, Any>(
                    "request_id" to request.id,
                    "response_received" to result.success,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = result.success,
            payload = MessagePayload.CustomPayload(
                type = "USER_CONFIRMATION_RESULT",
                data = mapOf<String, Any>(
                    "confirmed" to (result.value == "true"),
                    "user_choice" to result.value,
                    "display_text" to (options[result.value] ?: result.value),
                    "metadata" to mapOf(
                        "agent" to "USER_INTERACTION",
                        "interaction_type" to "confirmation",
                        "response_time_ms" to result.responseTime,
                        "timeout_used" to (result.responseTime >= timeout),
                        "default_used" to (result.value == defaultValue.toString())
                    )
                )
            )
        )
    }

    private suspend fun handleUserSelectionRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val message = data["message"] as? String ?: ""
        val options = data["options"] as? List<Map<String, Any>> ?: emptyList()
        val multiple = data["multiple"] as? Boolean ?: false
        val timeout = data["timeout"] as? Long ?: 30000L
        val defaultIndex = data["default_index"] as? Int

        publishEvent(
            messageBus = messageBus,
            eventType = "USER_INTERACTION_INITIATED",
            payload = MessagePayload.CustomPayload(
                type = "USER_SELECTION_STARTED",
                data = mapOf<String, Any>(
                    "request_id" to request.id,
                    "message" to message,
                    "options_count" to options.size,
                    "multiple" to multiple,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        )

        val result = withContext(Dispatchers.Default) {
            requestUserSelection(request.id, message, options, multiple, timeout, defaultIndex)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "USER_INTERACTION_COMPLETED",
            payload = MessagePayload.CustomPayload(
                type = "USER_SELECTION_COMPLETED",
                data = mapOf<String, Any>(
                    "request_id" to request.id,
                    "response_received" to result.success,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = result.success,
            payload = MessagePayload.CustomPayload(
                type = "USER_SELECTION_RESULT",
                data = mapOf<String, Any>(
                    "selected_indices" to result.selectedIndices,
                    "selected_values" to result.selectedValues,
                    "selected_display_text" to result.selectedDisplayText,
                    "multiple" to multiple,
                    "metadata" to mapOf(
                        "agent" to "USER_INTERACTION",
                        "interaction_type" to "selection",
                        "response_time_ms" to result.responseTime,
                        "timeout_used" to (result.responseTime >= timeout),
                        "default_used" to result.usedDefault
                    )
                )
            )
        )
    }

    private suspend fun handleUserNotificationRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val title = data["title"] as? String ?: "Notification"
        val message = data["message"] as? String ?: ""
        val level = data["level"] as? String ?: "info" // info, warning, error, success
        val duration = data["duration"] as? Long ?: 5000L // 0 = permanent
        val actions = data["actions"] as? List<Map<String, String>> ?: emptyList()

        publishEvent(
            messageBus = messageBus,
            eventType = "USER_INTERACTION_INITIATED",
            payload = MessagePayload.CustomPayload(
                type = "USER_NOTIFICATION_STARTED",
                data = mapOf<String, Any>(
                    "request_id" to request.id,
                    "title" to title,
                    "message" to message,
                    "level" to level,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        )

        val result = withContext(Dispatchers.Default) {
            showUserNotification(request.id, title, message, level, duration, actions)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "USER_INTERACTION_COMPLETED",
            payload = MessagePayload.CustomPayload(
                type = "USER_NOTIFICATION_COMPLETED",
                data = mapOf<String, Any>(
                    "request_id" to request.id,
                    "action_taken" to result.actionTaken,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "USER_NOTIFICATION_RESULT",
                data = mapOf<String, Any>(
                    "notification_id" to result.notificationId,
                    "action_taken" to result.actionTaken,
                    "display_duration_ms" to result.displayDuration,
                    "metadata" to mapOf(
                        "agent" to "USER_INTERACTION",
                        "interaction_type" to "notification",
                        "level" to level,
                        "has_actions" to actions.isNotEmpty()
                    )
                )
            )
        )
    }

    private suspend fun handleUserProgressRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val title = data["title"] as? String ?: "Progress"
        val message = data["message"] as? String ?: ""
        val progress = data["progress"] as? Double ?: 0.0
        val indeterminate = data["indeterminate"] as? Boolean ?: false
        val cancellable = data["cancellable"] as? Boolean ?: false

        publishEvent(
            messageBus = messageBus,
            eventType = "USER_INTERACTION_INITIATED",
            payload = MessagePayload.CustomPayload(
                type = "USER_PROGRESS_STARTED",
                data = mapOf<String, Any>(
                    "request_id" to request.id,
                    "title" to title,
                    "progress" to progress,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        )

        val result = withContext(Dispatchers.Default) {
            showUserProgress(request.id, title, message, progress, indeterminate, cancellable)
        }

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "USER_PROGRESS_RESULT",
                data = mapOf<String, Any>(
                    "progress_id" to result.progressId,
                    "cancelled" to result.cancelled,
                    "metadata" to mapOf(
                        "agent" to "USER_INTERACTION",
                        "interaction_type" to "progress",
                        "cancellable" to cancellable,
                        "indeterminate" to indeterminate
                    )
                )
            )
        )
    }

    private suspend fun handleUserFeedbackRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response {
        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val title = data["title"] as? String ?: "Feedback"
        val message = data["message"] as? String ?: ""
        val feedbackType = data["feedback_type"] as? String ?: "rating" // rating, text, both
        val ratingScale = data["rating_scale"] as? Int ?: 5
        val optional = data["optional"] as? Boolean ?: true

        publishEvent(
            messageBus = messageBus,
            eventType = "USER_INTERACTION_INITIATED",
            payload = MessagePayload.CustomPayload(
                type = "USER_FEEDBACK_STARTED",
                data = mapOf<String, Any>(
                    "request_id" to request.id,
                    "title" to title,
                    "feedback_type" to feedbackType,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        )

        val result = withContext(Dispatchers.Default) {
            requestUserFeedback(request.id, title, message, feedbackType, ratingScale, optional)
        }

        publishEvent(
            messageBus = messageBus,
            eventType = "USER_INTERACTION_COMPLETED",
            payload = MessagePayload.CustomPayload(
                type = "USER_FEEDBACK_COMPLETED",
                data = mapOf<String, Any>(
                    "request_id" to request.id,
                    "feedback_provided" to result.success,
                    "timestamp" to System.currentTimeMillis()
                )
            )
        )

        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = request.id,
            success = true,
            payload = MessagePayload.CustomPayload(
                type = "USER_FEEDBACK_RESULT",
                data = mapOf<String, Any>(
                    "rating" to (result.rating ?: 0),
                    "comment" to result.comment,
                    "feedback_type" to feedbackType,
                    "skipped" to result.skipped,
                    "metadata" to mapOf(
                        "agent" to "USER_INTERACTION",
                        "interaction_type" to "feedback",
                        "rating_scale" to ratingScale,
                        "response_time_ms" to result.responseTime
                    )
                )
            )
        )
    }

    private fun createErrorResponse(requestId: String, error: String): AgentMessage.Response {
        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = requestId,
            success = false,
            payload = MessagePayload.ErrorPayload(error = error),
            error = error
        )
    }

    // Реализация методов взаимодействия с пользователем

    private suspend fun requestUserInput(
        requestId: String,
        prompt: String,
        inputType: String,
        defaultValue: String?,
        validation: Map<String, Any>?,
        timeout: Long,
        multiline: Boolean,
        secret: Boolean
    ): UserResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // В реальной реализации здесь был бы вызов UI компонента
            // Для демонстрации используем консольный ввод или эмуляцию
            val input = when {
                // Эмуляция для тестирования
                System.getProperty("a2a.test.mode") == "true" -> {
                    when (inputType) {
                        "email" -> "test@example.com"
                        "number" -> "42"
                        "phone" -> "+1234567890"
                        "url" -> "https://example.com"
                        else -> defaultValue ?: "Test input"
                    }
                }
                // Реальный ввод из консоли (если available)
                else -> {
                    print("$prompt${if (defaultValue != null) " [$defaultValue]" else ""}: ")
                    val line = readLine()
                    line ?: defaultValue ?: ""
                }
            }

            val validationErrors = if (validation != null) {
                validateInput(input, validation)
            } else emptyList()

            val isValid = validationErrors.isEmpty()
            val finalInput = if (isValid) input else (defaultValue ?: "")

            UserResponse(
                success = true,
                value = finalInput,
                validated = isValid,
                validationErrors = validationErrors,
                responseTime = System.currentTimeMillis() - startTime,
                usedDefault = input.isEmpty() && defaultValue != null
            )
        } catch (e: Exception) {
            logger.error("User input request failed", e)
            UserResponse(
                success = false,
                value = defaultValue ?: "",
                validated = false,
                validationErrors = listOf("Input request failed: ${e.message}"),
                responseTime = System.currentTimeMillis() - startTime,
                usedDefault = true
            )
        }
    }

    private suspend fun requestUserConfirmation(
        requestId: String,
        message: String,
        defaultValue: Boolean,
        timeout: Long,
        options: Map<String, String>
    ): UserResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // В реальной реализации здесь был бы диалог подтверждения
            val result = when {
                System.getProperty("a2a.test.mode") == "true" -> defaultValue.toString()
                else -> {
                    val yesOption = options["true"] ?: "Yes"
                    val noOption = options["false"] ?: "No"
                    val defaultText = if (defaultValue) yesOption else noOption

                    print("$message [$defaultText] (y/n): ")
                    val input = readLine()?.lowercase()

                    when {
                        input.isNullOrEmpty() -> defaultValue.toString()
                        input.startsWith("y") -> "true"
                        input.startsWith("n") -> "false"
                        else -> defaultValue.toString()
                    }
                }
            }

            UserResponse(
                success = true,
                value = result,
                validated = true,
                validationErrors = emptyList(),
                responseTime = System.currentTimeMillis() - startTime,
                usedDefault = false
            )
        } catch (e: Exception) {
            logger.error("User confirmation request failed", e)
            UserResponse(
                success = false,
                value = defaultValue.toString(),
                validated = false,
                validationErrors = listOf("Confirmation request failed: ${e.message}"),
                responseTime = System.currentTimeMillis() - startTime,
                usedDefault = true
            )
        }
    }

    private suspend fun requestUserSelection(
        requestId: String,
        message: String,
        options: List<Map<String, Any>>,
        multiple: Boolean,
        timeout: Long,
        defaultIndex: Int?
    ): SelectionResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            if (options.isEmpty()) {
                return@withContext SelectionResponse(
                    success = false,
                    selectedIndices = emptyList(),
                    selectedValues = emptyList(),
                    selectedDisplayText = emptyList(),
                    responseTime = System.currentTimeMillis() - startTime,
                    usedDefault = false
                )
            }

            // В реальной реализации здесь был бы диалог выбора
            val selected = when {
                System.getProperty("a2a.test.mode") == "true" -> {
                    if (multiple) listOf(defaultIndex ?: 0)
                    else listOf(defaultIndex ?: 0)
                }
                else -> {
                    println(message)
                    options.forEachIndexed { index, option ->
                        val display = option["display"] as? String ?: option["value"] as? String ?: index.toString()
                        println("  ${index + 1}. $display")
                    }

                    print("Enter choice${if (multiple) "s" else ""}${if (defaultIndex != null) " [${defaultIndex + 1}]" else ""}: ")
                    val input = readLine()

                    if (input.isNullOrEmpty()) {
                        listOf(defaultIndex ?: 0)
                    } else {
                        input.split(",").mapNotNull { it.trim().toIntOrNull()?.let { it - 1 } }
                            .filter { it in options.indices }
                            .distinct()
                    }
                }
            }

            val selectedValues = selected.map { options[it]["value"] as? String ?: it.toString() }
            val selectedDisplayText = selected.map {
                options[it]["display"] as? String ?: options[it]["value"] as? String ?: it.toString()
            }

            SelectionResponse(
                success = true,
                selectedIndices = selected,
                selectedValues = selectedValues,
                selectedDisplayText = selectedDisplayText,
                responseTime = System.currentTimeMillis() - startTime,
                usedDefault = false
            )
        } catch (e: Exception) {
            logger.error("User selection request failed", e)
            SelectionResponse(
                success = false,
                selectedIndices = defaultIndex?.let { listOf(it) } ?: emptyList(),
                selectedValues = emptyList(),
                selectedDisplayText = emptyList(),
                responseTime = System.currentTimeMillis() - startTime,
                usedDefault = true
            )
        }
    }

    private suspend fun showUserNotification(
        requestId: String,
        title: String,
        message: String,
        level: String,
        duration: Long,
        actions: List<Map<String, String>>
    ): NotificationResult = withContext(Dispatchers.IO) {
        try {
            val notificationId = UUID.randomUUID().toString()
            val startTime = System.currentTimeMillis()

            // В реальной реализации здесь было бы показано уведомление
            println("[$level] $title: $message")
            if (actions.isNotEmpty()) {
                println("Available actions:")
                actions.forEach { action ->
                    println("  - ${action["display"] ?: action["id"]}")
                }
            }

            // Эмуляция действия пользователя
            val actionTaken = when {
                System.getProperty("a2a.test.mode") == "true" -> actions.firstOrNull()?.get("id") ?: "ok"
                actions.isNotEmpty() -> {
                    print("Choose action: ")
                    readLine()?.takeIf { it.isNotBlank() } ?: "ok"
                }
                else -> "ok"
            }

            if (duration > 0) {
                Thread.sleep(duration)
            }

            NotificationResult(
                notificationId = notificationId,
                actionTaken = actionTaken,
                displayDuration = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            logger.error("Notification display failed", e)
            NotificationResult(
                notificationId = UUID.randomUUID().toString(),
                actionTaken = "error",
                displayDuration = 0L
            )
        }
    }

    private suspend fun showUserProgress(
        requestId: String,
        title: String,
        message: String,
        progress: Double,
        indeterminate: Boolean,
        cancellable: Boolean
    ): ProgressResult = withContext(Dispatchers.IO) {
        try {
            val progressId = UUID.randomUUID().toString()

            // В реальной реализации здесь был бы показан прогресс-бар
            val progressText = if (indeterminate) {
                "$title: $message (Working...)"
            } else {
                "$title: $message (${(progress * 100).toInt()}%)"
            }

            println(progressText)

            // Эмуляция отмены
            val cancelled = when {
                System.getProperty("a2a.test.mode") == "true" -> false
                cancellable -> {
                    print("Press Enter to cancel...")
                    val input = readLine()
                    input != null
                }
                else -> false
            }

            ProgressResult(
                progressId = progressId,
                cancelled = cancelled
            )
        } catch (e: Exception) {
            logger.error("Progress display failed", e)
            ProgressResult(
                progressId = UUID.randomUUID().toString(),
                cancelled = false
            )
        }
    }

    private suspend fun requestUserFeedback(
        requestId: String,
        title: String,
        message: String,
        feedbackType: String,
        ratingScale: Int,
        optional: Boolean
    ): FeedbackResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // В реальной реализации здесь был бы диалог обратной связи
            val rating = when {
                feedbackType == "text" -> null
                System.getProperty("a2a.test.mode") == "true" -> ratingScale
                else -> {
                    print("$title: $message\nRate (1-$ratingScale): ")
                    readLine()?.toIntOrNull() ?: ratingScale
                }
            }

            val comment = when {
                feedbackType == "rating" -> ""
                System.getProperty("a2a.test.mode") == "true" -> "Test feedback"
                else -> {
                    print("Additional comments (optional): ")
                    readLine() ?: ""
                }
            }

            val skipped = rating == null && comment.isEmpty() && optional

            FeedbackResponse(
                success = true,
                rating = rating,
                comment = comment,
                skipped = skipped,
                responseTime = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            logger.error("Feedback request failed", e)
            FeedbackResponse(
                success = false,
                rating = null,
                comment = "",
                skipped = optional,
                responseTime = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun validateInput(input: String, validation: Map<String, Any>): List<String> {
        val errors = mutableListOf<String>()

        // Проверка обязательного поля
        if (validation["required"] == true && input.isBlank()) {
            errors.add("This field is required")
        }

        // Проверка минимальной длины
        val minLength = validation["min_length"] as? Int
        if (minLength != null && input.length < minLength) {
            errors.add("Minimum length is $minLength characters")
        }

        // Проверка максимальной длины
        val maxLength = validation["max_length"] as? Int
        if (maxLength != null && input.length > maxLength) {
            errors.add("Maximum length is $maxLength characters")
        }

        // Проверка паттерна
        val pattern = validation["pattern"] as? String
        if (pattern != null && !Regex(pattern).matches(input)) {
            errors.add("Input format is invalid")
        }

        // Проверка типа
        when (validation["type"] as? String) {
            "email" -> {
                if (!Regex("""^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\.[A-Za-z]{2,})$""").matches(input)) {
                    errors.add("Invalid email format")
                }
            }
            "number" -> {
                if (input.toDoubleOrNull() == null) {
                    errors.add("Must be a number")
                }
            }
            "url" -> {
                if (!Regex("""^https?://[^\s/$.?#].[^\s]*$""").matches(input)) {
                    errors.add("Invalid URL format")
                }
            }
            "phone" -> {
                if (!Regex("""^[\d\s\-\+\(\)]+$""").matches(input)) {
                    errors.add("Invalid phone number format")
                }
            }
        }

        return errors
    }

    // Data классы для результатов

    private data class UserResponse(
        val success: Boolean,
        val value: String,
        val validated: Boolean,
        val validationErrors: List<String>,
        val responseTime: Long,
        val usedDefault: Boolean
    )

    private data class SelectionResponse(
        val success: Boolean,
        val selectedIndices: List<Int>,
        val selectedValues: List<String>,
        val selectedDisplayText: List<String>,
        val responseTime: Long,
        val usedDefault: Boolean
    )

    private data class NotificationResult(
        val notificationId: String,
        val actionTaken: String,
        val displayDuration: Long
    )

    private data class ProgressResult(
        val progressId: String,
        val cancelled: Boolean
    )

    private data class FeedbackResponse(
        val success: Boolean,
        val rating: Int?,
        val comment: String,
        val skipped: Boolean,
        val responseTime: Long
    )

    private data class PendingRequest(
        val requestId: String,
        val requestType: String,
        val timestamp: Long,
        val timeout: Long
    )
}