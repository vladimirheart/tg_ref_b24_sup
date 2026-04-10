# 🟢 Настройка MAX-бота (Java)

Интеграция выполнена по официальному API MAX: https://dev.max.ru/docs-api.

## 1. Получите токен бота

В MAX Business Platform откройте: **Чат-боты → Интеграция → Получить токен**.

## 2. Создайте канал в панели

В панели: **Настройки → Каналы (боты) → Новый канал**.

- Платформа: `MAX`
- Токен: токен MAX бота
- (опционально) `support_chat_id`: ID чата операторов в MAX

## 3. Запустите бота

Нажмите «Запустить бота» у созданного канала.

Для платформы `MAX` панель запускает Maven-модуль `bot-max`.

## 4. Настройте Webhook в MAX

Создайте подписку на обновления в MAX API:

```bash
curl -X POST "https://platform-api.max.ru/subscriptions" \
  -H "Authorization: <MAX_BOT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://<your-domain>/webhooks/max/<channel_id>",
    "update_types": ["message_created"],
    "secret": "<optional-secret>"
  }'
```

Если указываете `secret`, проверка выполняется по заголовку `X-Max-Bot-Api-Secret`.
`<channel_id>` — это ID канала из панели; endpoint принимает webhook в `spring-panel`
и проксирует его во внутренний процесс `bot-max`.

## 5. Проверка

1. Напишите боту в MAX (`/start` или текст).
2. Убедитесь, что:
   - бот отвечает в диалоге;
   - в панели создаётся заявка;
   - входящее сообщение попадает в историю диалога.
