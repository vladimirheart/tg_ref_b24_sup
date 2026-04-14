# ⚙️ Конфигурация

Проект использует Spring Boot для панели и Java-бота. Настройки читаются из переменных окружения, `.env` (если загружаете его вручную) и JSON-файлов в `config/shared`.

## .env и переменные окружения

Создайте файл `.env` по примеру ниже и дополните его нужными значениями:

```
TELEGRAM_BOT_TOKEN=123:ABC
GROUP_CHAT_ID=-1001234567890
APP_DB_TICKETS=/srv/iguana/tickets.db
APP_DB_USERS=/srv/iguana/users.db
APP_DB_CLIENTS=/srv/iguana/clients.db
APP_DB_KNOWLEDGE=/srv/iguana/knowledge_base.db
APP_DB_OBJECTS=/srv/iguana/objects.db
APP_DB_SETTINGS=/srv/iguana/settings.db
APP_BOT_DATABASE_DIR=/srv/iguana/bots
```

Ключевые переменные:

- `TELEGRAM_BOT_TOKEN` — токен Telegram-бота.
- `GROUP_CHAT_ID` — ID рабочей группы/чата для уведомлений (можно оставить пустым и сохранить в панели).
- `APP_DB_*` — пути к SQLite-файлам для отдельных баз (заявки, пользователи, клиенты, база знаний, объекты, общие настройки).
- `APP_BOT_DATABASE_DIR` — каталог, в котором будут храниться отдельные базы для каждого бота.
- `DATABASE_URL` — опционально, строка подключения к PostgreSQL/MySQL вместо SQLite.

> 💡 ID группы поддержки для Telegram можно сохранить в панели администратора в разделе «Каналы (боты)». Если оставить пустым, бот запишет ID автоматически после добавления в чат.

## Общие JSON-настройки

Файлы `config/shared/settings.json`, `config/shared/locations.json` и `config/shared/org_structure.json` используются панелью и ботом. При изменении JSON-файлов перезапуск сервисов не требуется — они читаются напрямую с диска.

## `dialog_config`: SLA-эскалация через webhook

В `settings.json -> dialog_config` можно включить серверные webhook-уведомления для критичных нераспределённых диалогов:

- `sla_critical_escalation_enabled` — включает саму SLA-эскалацию (по умолчанию `true`).
- `sla_critical_escalation_webhook_enabled` — включает отправку webhook (по умолчанию `false`).
- `sla_critical_escalation_webhook_url` — legacy-поле с одним URL получателя webhook (обратная совместимость).
- `sla_critical_escalation_webhook_urls` — список URL для fan-out отправки в несколько incident-каналов (рекомендуемый формат).
- `sla_critical_escalation_webhook_cooldown_minutes` — минимальный интервал повторной отправки по одному тикету (по умолчанию `30`).
- `sla_critical_escalation_webhook_timeout_ms` — timeout HTTP-вызова webhook (по умолчанию `4000`).
- `sla_critical_minutes` — порог критичности SLA (используется для отбора тикетов).
- `sla_target_minutes` — целевой SLA в минутах (используется для расчёта `minutes_left`).

Фоновая проверка выполняется по расписанию (`panel.sla-escalation.webhook-check-interval-ms`, по умолчанию 120000 мс).
