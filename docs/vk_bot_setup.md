# 🔵 Настройка VK-бота (Java)

## 1. Получите токен сообщества

- Перейдите в настройки сообщества VK → «Работа с API» → «Ключи доступа».
- Создайте ключ с правами на сообщения.

## 2. Задайте переменные окружения / канал

Для ручного runtime используются platform-specific keys, например:

```bash
export VK_BOT_ENABLED=true
export VK_BOT_TOKEN="<your_vk_token>"
export VK_GROUP_ID=123456
export VK_OPERATOR_CHAT_ID=0
```

В штатном contour канал создаётся и настраивается через «Настройки → Каналы (боты)».

## 3. Запуск через панель

Выберите VK-канал и запустите bot runtime (в production этим владеет bot supervisor). Бот работает как отдельный Java process, но отдельная business SQLite БД для канала не создаётся: business state остаётся в canonical backend/PostgreSQL contour, а transport взаимодействует через текущий queue/API/JDBC contract согласно окружению.

Legacy `bot-<channelId>.db` допускается только как import/diagnostic evidence и не является live source of truth.

## 4. Проверка

Отправьте сообщение в сообщество VK и проверьте, что обращение появляется в панели, а runtime/logs не показывают startup или delivery errors.
