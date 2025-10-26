use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::time::Duration;
use tokio::time::timeout;
use tracing::{info, warn, error, debug};
use reqwest::Client;

/// HTTP клиент для YandexGPT API
#[derive(Clone)]
pub struct YandexGPTClient {
    client: Client,
    api_key: String,
    folder_id: String,
    base_url: String,
    model: String,
    temperature: f32,
    max_tokens: u32,
}

/// Запрос к YandexGPT API
#[derive(Debug, Serialize)]
struct YandexGPTRequest {
    model_uri: String,
    completion_options: CompletionOptions,
    messages: Vec<Message>,
}

/// Опции генерации
#[derive(Debug, Serialize)]
struct CompletionOptions {
    stream: bool,
    temperature: f32,
    max_tokens: u32,
}

/// Сообщение в диалоге
#[derive(Debug, Serialize)]
struct Message {
    role: String,
    text: String,
}

/// Ответ от YandexGPT API
#[derive(Debug, Deserialize)]
struct YandexGPTResponse {
    result: CompletionResult,
}

/// Результат генерации
#[derive(Debug, Deserialize)]
struct CompletionResult {
    alternatives: Vec<Alternative>,
    usage: Usage,
}

/// Альтернативный вариант ответа
#[derive(Debug, Deserialize)]
struct Alternative {
    message: ResponseMessage,
    status: String,
}

/// Ответное сообщение
#[derive(Debug, Deserialize)]
struct ResponseMessage {
    role: String,
    text: String,
}

/// Статистика использования токенов
#[derive(Debug, Deserialize)]
struct Usage {
    #[serde(rename = "inputTextTokens")]
    input_text_tokens: String,
    #[serde(rename = "completionTokens")]
    completion_tokens: String,
    #[serde(rename = "totalTokens")]
    total_tokens: String,
}

/// Конфигурация YandexGPT
#[derive(Debug, Clone)]
pub struct YandexGPTConfig {
    pub api_key: String,
    pub folder_id: String,
    pub model: String,
    pub temperature: f32,
    pub max_tokens: u32,
    pub timeout: Duration,
}

impl Default for YandexGPTConfig {
    fn default() -> Self {
        Self {
            api_key: std::env::var("DEPLOY_PLUGIN_YANDEX_API_KEY")
                .unwrap_or_else(|_| "default_key".to_string()),
            folder_id: std::env::var("DEPLOY_PLUGIN_YANDEX_FOLDER_ID")
                .unwrap_or_else(|_| "default_folder".to_string()),
            // Рекомендуемый формат модели с версией
            model: "yandexgpt/latest".to_string(),
            temperature: 0.3,
            max_tokens: 2000,
            timeout: Duration::from_secs(30),
        }
    }
}

impl YandexGPTClient {
    /// Создает новый экземпляр клиента
    pub fn new(config: YandexGPTConfig) -> Self {
        let client = Client::builder()
            .timeout(config.timeout)
            .build()
            .expect("Failed to create HTTP client");

        Self {
            client,
            api_key: config.api_key,
            folder_id: config.folder_id,
            base_url: "https://llm.api.cloud.yandex.net/foundationModels/v1/completion".to_string(),
            model: config.model,
            temperature: config.temperature,
            max_tokens: config.max_tokens,
        }
    }

    /// Формирует model_uri на основе текущей конфигурации
    fn build_model_uri(&self) -> String {
        if self.model.starts_with("gpt://") {
            self.model.clone()
        } else {
            format!("gpt://{}/{}", self.folder_id, self.model)
        }
    }

    /// Выполняет chat completion запрос
    pub async fn chat_completion(&self, prompt: &str) -> Result<String> {
        info!("🤖 Запрос к YandexGPT API");

        // Диагностические логи по конфигурации
        debug!("YandexGPT raw model from config: {}", self.model);
        debug!("YandexGPT folder_id from config: {}", self.folder_id);
        if self.folder_id.contains("${") {
            warn!("folder_id содержит плейсхолдер переменной окружения. Проверьте DEPLOY_PLUGIN_YANDEX_FOLDER_ID");
        }
        if !self.model.contains('/') && !self.model.starts_with("gpt://") {
            warn!("model без суффикса версии (например, '/latest'). Текущее значение: {}", self.model);
        }

        // Формируем корректный model_uri
        let model_uri = self.build_model_uri();
        info!("Используется модель: {}", model_uri);

        let request_body = YandexGPTRequest {
            model_uri,
            completion_options: CompletionOptions {
                stream: false,
                temperature: self.temperature,
                max_tokens: self.max_tokens,
            },
            messages: vec![
                Message {
                    role: "system".to_string(),
                    text: "Ты - полезный AI помощник, который отвечает на русском языке.".to_string(),
                },
                Message {
                    role: "user".to_string(),
                    text: prompt.to_string(),
                },
            ],
        };

        debug!("Отправка запроса: {}", serde_json::to_string(&request_body)?);

        let response = timeout(
            Duration::from_secs(30),
            self.client
                .post(&self.base_url)
                .header("Authorization", format!("Api-Key {}", self.api_key))
                .header("Content-Type", "application/json")
                .header("x-folder-id", &self.folder_id)
                .json(&request_body)
                .send()
        ).await
        .context("Таймаут запроса к YandexGPT API")?
        .context("Ошибка выполнения запроса к YandexGPT API")?;

        let status = response.status();
        debug!("Ответ статуса от YandexGPT: {}", status);
        let response_text = response.text().await
            .context("Не удалось прочитать ответ от YandexGPT")?;
        debug!("Сырый ответ YandexGPT (обрезан до 500 символов): {}", &response_text[..response_text.len().min(500)]);

        if !status.is_success() {
            let error_msg = format!("YandexGPT API вернул ошибку {}: {}", status, response_text);
            error!("{}", error_msg);

            // Авто-фолбэк на yandexgpt-lite/latest при invalid model_uri
            if response_text.contains("invalid model_uri") {
                // Строим альтернативный URI
                let alt_model = if self.model.contains("yandexgpt-lite") { self.model.clone() } else { self.model.replace("yandexgpt", "yandexgpt-lite") };
                let alt_uri = if alt_model.starts_with("gpt://") { alt_model.clone() } else { format!("gpt://{}/{}", self.folder_id, alt_model) };
                warn!("Пробуем fallback модель: {}", alt_uri);

                let alt_body = YandexGPTRequest {
                    model_uri: alt_uri,
                    completion_options: CompletionOptions { stream: false, temperature: self.temperature, max_tokens: self.max_tokens },
                    messages: vec![
                        Message { role: "system".to_string(), text: "Ты - полезный AI помощник, который отвечает на русском языке.".to_string() },
                        Message { role: "user".to_string(), text: prompt.to_string() },
                    ],
                };

                let alt_resp = timeout(
                    Duration::from_secs(30),
                    self.client
                        .post(&self.base_url)
                        .header("Authorization", format!("Api-Key {}", self.api_key))
                        .header("Content-Type", "application/json")
                        .header("x-folder-id", &self.folder_id)
                        .json(&alt_body)
                        .send()
                ).await
                .context("Таймаут запроса к YandexGPT API (fallback)")?
                .context("Ошибка выполнения запроса к YandexGPT API (fallback)")?;

                let alt_status = alt_resp.status();
                debug!("Fallback ответ статуса от YandexGPT: {}", alt_status);
                let alt_text = alt_resp.text().await.context("Не удалось прочитать ответ fallback от YandexGPT")?;
                debug!("Fallback сырой ответ YandexGPT (обрезан до 500 символов): {}", &alt_text[..alt_text.len().min(500)]);

                if !alt_status.is_success() {
                    let fb_err = format!("Fallback YandexGPT вернул ошибку {}: {}", alt_status, alt_text);
                    error!("{}", fb_err);
                    return Err(anyhow::anyhow!(error_msg));
                }

                let api_response: YandexGPTResponse = serde_json::from_str(&alt_text)
                    .with_context(|| format!("Ошибка парсинга JSON ответа от YandexGPT (fallback). Ответ: {}", alt_text))?;

                if let Some(alternative) = api_response.result.alternatives.first() {
                    if alternative.status == "ALTERNATIVE_STATUS_FINAL" || alternative.status == "ALTERNATIVE_STATUS_SUCCESS" {
                        info!("✅ Получен ответ от YandexGPT (fallback) ({} токенов)", api_response.result.usage.total_tokens);
                        return Ok(alternative.message.text.clone());
                    }
                }

                return Err(anyhow::anyhow!(error_msg));
            }

            return Err(anyhow::anyhow!(error_msg));
        }

        debug!("Ответ от YandexGPT API: {}", response_text);

        let api_response: YandexGPTResponse = serde_json::from_str(&response_text)
            .with_context(|| format!("Ошибка парсинга JSON ответа от YandexGPT. Ответ: {}", response_text))?;

        if let Some(alternative) = api_response.result.alternatives.first() {
            if alternative.status == "ALTERNATIVE_STATUS_FINAL" || alternative.status == "ALTERNATIVE_STATUS_SUCCESS" {
                info!("✅ Получен ответ от YandexGPT ({} токенов)", api_response.result.usage.total_tokens);
                debug!("Использование токенов: {:?}", api_response.result.usage);
                Ok(alternative.message.text.clone())
            } else {
                let error_msg = format!("YandexGPT вернул статус: {}", alternative.status);
                error!("{}", error_msg);
                Err(anyhow::anyhow!(error_msg))
            }
        } else {
            let error_msg = "YandexGPT не вернул альтернатив в ответе";
            error!("{}", error_msg);
            Err(anyhow::anyhow!(error_msg))
        }
    }

    /// Выполняет запрос с retry логикой
    pub async fn chat_completion_with_retry(&self, prompt: &str, max_retries: u32) -> Result<String> {
        let mut last_error = None;

        for attempt in 0..=max_retries {
            match self.chat_completion(prompt).await {
                Ok(response) => return Ok(response),
                Err(e) => {
                    warn!("Попытка {} не удалась: {}", attempt + 1, e);
                    last_error = Some(e);

                    if attempt < max_retries {
                        let delay = Duration::from_millis(1000 * (2_u64.pow(attempt)));
                        info!("Повторная попытка через {:?}", delay);
                        tokio::time::sleep(delay).await;
                    }
                }
            }
        }

        Err(last_error.unwrap_or_else(|| anyhow::anyhow!("Все попытки провалились")))
    }

    /// Проверяет доступность API
    pub async fn health_check(&self) -> Result<bool> {
        info!("🔍 Проверка доступности YandexGPT API");

        match self.chat_completion("Привет! Просто проверка доступности API.").await {
            Ok(_) => {
                info!("✅ YandexGPT API доступен");
                Ok(true)
            }
            Err(e) => {
                warn!("❌ YandexGPT API недоступен: {}", e);
                Ok(false)
            }
        }
    }

    /// Получает информацию о модели
    pub fn get_model_info(&self) -> &str {
        &self.model
    }
}

/// Простая фабрика для создания клиентов
pub struct YandexGPTClientFactory;

impl YandexGPTClientFactory {
    /// Создает клиент из переменных окружения
    pub fn from_env() -> Result<YandexGPTClient> {
        let config = YandexGPTConfig::default();

        if config.api_key == "default_key" {
            return Err(anyhow::anyhow!(
                "Переменная окружения DEPLOY_PLUGIN_YANDEX_API_KEY не установлена"
            ));
        }

        if config.folder_id == "default_folder" {
            return Err(anyhow::anyhow!(
                "Переменная окружения DEPLOY_PLUGIN_YANDEX_FOLDER_ID не установлена"
            ));
        }

        Ok(YandexGPTClient::new(config))
    }

    /// Создает клиент с кастомной конфигурацией
    pub fn with_config(config: YandexGPTConfig) -> YandexGPTClient {
        YandexGPTClient::new(config)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_yandexgpt_client_creation() {
        let config = YandexGPTConfig {
            api_key: "test_key".to_string(),
            folder_id: "test_folder".to_string(),
            model: "yandexgpt/latest".to_string(),
            temperature: 0.3,
            max_tokens: 1000,
            timeout: Duration::from_secs(10),
        };

        let client = YandexGPTClient::new(config);
        assert_eq!(client.folder_id, "test_folder");
        assert_eq!(client.get_model_info(), "yandexgpt/latest");
    }

    #[tokio::test]
    async fn test_yandexgpt_factory_from_env_missing() {
        // Очищаем переменные окружения для теста
        std::env::remove_var("DEPLOY_PLUGIN_YANDEX_API_KEY");
        std::env::remove_var("DEPLOY_PLUGIN_YANDEX_FOLDER_ID");

        let result = YandexGPTClientFactory::from_env();
        assert!(result.is_err());
    }
}