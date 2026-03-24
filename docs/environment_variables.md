# 🌍 Переменные окружения

Ниже перечислены основные переменные, используемые Java-панелью и Java-ботом.

## Базовые переменные

| Переменная | Описание | Где используется |
| --- | --- | --- |
| `TELEGRAM_BOT_TOKEN` | токен Telegram-бота | Java-бот |
| `TELEGRAM_BOT_USERNAME` | @username бота | Java-бот |
| `GROUP_CHAT_ID` | ID рабочей группы/чата | Java-бот |
| `VK_BOT_ENABLED` | включить VK-бота (`true/false`) | Java-бот |
| `VK_BOT_TOKEN` | токен VK | Java-бот |
| `VK_GROUP_ID` | ID сообщества VK | Java-бот |
| `VK_OPERATOR_CHAT_ID` | чат операторов VK | Java-бот |
| `MAX_BOT_ENABLED` | включить MAX-бота (`true/false`) | Java-бот |
| `MAX_BOT_TOKEN` | токен MAX | Java-бот |
| `MAX_SUPPORT_CHAT_ID` | чат операторов MAX | Java-бот |
| `DATABASE_URL` | строка подключения PostgreSQL/MySQL | Панель и бот (если используете внешнюю БД) |

## Базы данных

| Переменная | Описание | По умолчанию |
| --- | --- | --- |
| `APP_DB_TICKETS` | база заявок | `tickets.db` |
| `APP_DB_USERS` | база пользователей панели | `users.db` |
| `APP_DB_CLIENTS` | база клиентов | `clients.db` |
| `APP_DB_KNOWLEDGE` | база знаний | `knowledge_base.db` |
| `APP_DB_OBJECTS` | база объектов | `objects.db` |
| `APP_DB_SETTINGS` | общая база настроек | `settings.db` |
| `APP_DB_BOT` | база данных бота (если не используете отдельные базы) | `bot_database.db` |
| `APP_BOT_DATABASE_DIR` | каталог баз каждого бота | `../bot_databases` |

## Хранилища

| Переменная | Описание | По умолчанию |
| --- | --- | --- |
| `APP_STORAGE_ATTACHMENTS` | вложения | `../attachments` |
| `APP_STORAGE_KNOWLEDGE_BASE` | файлы базы знаний | `../attachments/knowledge_base` |
| `APP_STORAGE_AVATARS` | аватары | `../attachments/avatars` |
| `APP_STORAGE_WEBFORMS` | формы | `../attachments/forms` |

## Пример запуска

```bash
export TELEGRAM_BOT_TOKEN="123:ABC"
export APP_DB_TICKETS="/srv/iguana/tickets.db"
export APP_DB_CLIENTS="/srv/iguana/clients.db"
export APP_BOT_DATABASE_DIR="/srv/iguana/bots"
```
