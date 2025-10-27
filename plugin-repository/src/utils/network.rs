use anyhow::Result;
use rand::{thread_rng, Rng};
use std::time::Duration;

/// Утилиты сети: retry с экспоненциальной задержкой и джиттером
pub struct NetworkUtils;

impl NetworkUtils {
    /// Выполняет операцию с retry (экспоненциальная задержка + джиттер)
    pub async fn retry<F, Fut, T>(mut op: F, attempts: u32, base_delay_ms: u64) -> Result<T>
    where
        F: FnMut() -> Fut,
        Fut: std::future::Future<Output = Result<T>>,
    {
        let mut last_err: Option<anyhow::Error> = None;
        for i in 0..attempts {
            match op().await {
                Ok(v) => return Ok(v),
                Err(e) => {
                    last_err = Some(e);
                    if i + 1 < attempts {
                        let exp = 2u64.saturating_pow(i);
                        let jitter: u64 = thread_rng().gen_range(0..(base_delay_ms / 2).max(1));
                        let delay = base_delay_ms.saturating_mul(exp) + jitter;
                        tokio::time::sleep(Duration::from_millis(delay)).await;
                    }
                }
            }
        }
        Err(last_err.unwrap_or_else(|| anyhow::anyhow!("retry: неизвестная ошибка")))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_retry_succeeds_eventually() {
        let mut count = 0;
        let res: Result<i32> = NetworkUtils::retry(
            || {
                count += 1;
                async move {
                    if count < 3 { Err(anyhow::anyhow!("fail")) } else { Ok(42) }
                }
            },
            5,
            10,
        ).await;
        assert_eq!(res.unwrap(), 42);
    }
}