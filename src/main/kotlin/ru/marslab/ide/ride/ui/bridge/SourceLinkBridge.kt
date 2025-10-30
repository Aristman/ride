package ru.marslab.ide.ride.ui.bridge

import com.intellij.openapi.diagnostic.Logger
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.misc.BoolRef
import ru.marslab.ide.ride.service.rag.RagSourceLinkService

/**
 * JavaScript-Java bridge для обработки source link кликов
 */
class SourceLinkBridge : CefMessageRouterHandlerAdapter() {

    private val logger = Logger.getInstance(SourceLinkBridge::class.java)

    companion object {
        private const val JAVA_SCRIPT_BINDING_NAME = "javaBridge"
        private const val QUERY_FUNCTION_NAME = "query"
    }

    override fun onQuery(
        browser: CefBrowser?,
        frame: CefFrame?,
        queryId: Long,
        persistent: BoolRef,
        request: CefMessageRouterHandlerAdapter.Request?
    ): Boolean {
        try {
            val requestJson = request?.url ?: return false

            logger.info("Received bridge request: $requestJson")

            // Парсим JSON запрос
            when {
                requestJson.contains("openSourceFile") -> {
                    handleOpenSourceFile(requestJson)
                    sendResponse(browser, frame, queryId, """{"success": true}""")
                    return true
                }
                else -> {
                    logger.warn("Unknown bridge request: $requestJson")
                    sendResponse(browser, frame, queryId, """{"success": false, "error": "Unknown request"}""")
                    return true
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing bridge request", e)
            sendResponse(browser, frame, queryId, """{"success": false, "error": "${e.message}"}""")
            return true
        }
    }

    private fun handleOpenSourceFile(requestJson: String) {
        try {
            // Извлекаем команду из JSON запроса
            val commandRegex = Regex(""command":\s*"([^"]+)"")
            val match = commandRegex.find(requestJson)
            val command = match?.groupValues?.get(1)

            if (command != null) {
                val sourceLinkService = RagSourceLinkService.getInstance()
                val openAction = sourceLinkService.extractSourceInfo(command)

                if (openAction != null) {
                    val success = sourceLinkService.handleOpenAction(openAction)
                    logger.info("Source link action executed: $command, success: $success")
                } else {
                    logger.warn("Failed to parse source link command: $command")
                }
            } else {
                logger.warn("No command found in request: $requestJson")
            }
        } catch (e: Exception) {
            logger.error("Error handling open source file request", e)
        }
    }

    private fun sendResponse(browser: CefBrowser?, frame: CefFrame?, queryId: Long, response: String) {
        try {
            frame?.executeJavaScript(
                "window.javaBridgeResponse && window.javaBridgeResponse($queryId, $response)",
                frame?.url,
                0
            )
        } catch (e: Exception) {
            logger.error("Error sending bridge response", e)
        }
    }

    /**
     * Создает JavaScript код для регистрации bridge
     */
    fun createBridgeJavaScript(): String {
        return """
        (function() {
            // Создаем Java bridge объект
            window.javaBridge = {
                openSourceFile: function(command) {
                    return new Promise((resolve, reject) => {
                        // Сохраняем callback'и
                        window.javaBridgeResponse = function(queryId, response) {
                            try {
                                const data = JSON.parse(response);
                                if (data.success) {
                                    resolve(data);
                                } else {
                                    reject(new Error(data.error || 'Unknown error'));
                                }
                            } catch (e) {
                                reject(e);
                            }
                        };

                        // Отправляем запрос
                        window.query(JSON.stringify({
                            action: 'openSourceFile',
                            command: command
                        }));
                    });
                }
            };

            // Обновляем функцию openSourceFile для использования bridge
            window.openSourceFile = function(command) {
                window.javaBridge.openSourceFile(command).catch(function(error) {
                    console.error('Failed to open source file:', error);
                });
            };

            console.log('Source link bridge initialized');
        })();
        """.trimIndent()
    }
}