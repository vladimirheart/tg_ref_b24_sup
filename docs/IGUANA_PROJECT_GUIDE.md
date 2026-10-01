# Подробное руководство по проекту Iguana

## 1. Что такое Iguana

Iguana - это единый support-контур, в котором соединены:

- клиентские обращения из внешних каналов;
- рабочее место операторов;
- шаблоны вопросов и прикладные маршруты обращения;
- аналитика и dashboard;
- база знаний;
- эксплуатационные и monitoring-сценарии;
- управление runtime-процессами ботов.

Проект ориентирован на практическую поддержку бизнеса, поэтому в нём важны не только исходники, но и данные: canonical PostgreSQL state, object/attachment storage, shared-конфиги, архивные migration evidence и фактическое состояние каналов.

## 2. Из чего состоит система

### 2.1 `spring-panel`

`spring-panel` - это центральное приложение на `Spring Boot`, которое отвечает за:

- web UI;
- операторский workflow;
- настройки;
- dashboard и аналитику;
- базу знаний;
- orchestration bot runtime;
- server-side API и служебные сценарии.

Обычно именно с запуска `spring-panel` начинается работа с системой.

### 2.2 `java-bot`

`java-bot` - это multi-module Maven-проект, который содержит runtime для каналов:

- `bot-core` - общее ядро;
- `bot-telegram` - Telegram;
- `bot-vk` - VK;
- `bot-max` - MAX.

На практике панель управляет ботами, а боты обеспечивают внешний транспорт и часть message lifecycle.

### 2.3 Shared-конфиги

Shared JSON-файлы лежат в `config/shared/`:

- `settings.json`
- `locations.json`
- `org_structure.json`
- `monitoring-credentials.key`

Это важная часть состояния системы. Если перенести только код, но забыть эти файлы, новая машина поднимет приложение не в том прикладном состоянии.

## 3. Карта репозитория

| Путь | Что внутри | Почему важно |
| --- | --- | --- |
| `spring-panel/` | Spring Boot панель | Основной UI и backend |
| `java-bot/` | runtime модулей ботов | Внешние каналы и transport layer |
| `config/shared/` | JSON-конфиги | Shared бизнес-настройки |
| `attachments/` | файлы пользователей и knowledge assets | Без них часть контента будет недоступна |
| `bot_databases/` | `bot-<channelId>.db` | Legacy per-channel shard-данные для controlled import/диагностики |
| `docs/` | документация | Операционная и архитектурная база знаний |
| `logs/` | runtime-логи | Полезно для диагностики, но не обязательно для переноса |
| `run/` | служебные runtime-файлы | Обычно не переносятся |
| `ai-context/` | task-tracking, правила, changelog | Нужен в основном для сопровождения разработки |

## 4. Основные прикладные сущности

### 4.1 Диалоги и обращения

Центр продукта - это обращения клиентов и история сообщений. Внутри системы это обычно включает:

- тикет или диалог;
- канал обращения;
- клиента и его идентификаторы;
- назначенного ответственного;
- участников;
- статусы;
- сообщения;
- служебные признаки SLA, авто-действий и follow-up логики.

### 4.2 Клиенты

Карточка клиента хранит:

- имя и aliases;
- телефоны и usernames;
- привязки к каналам;
- историю обращений;
- блокировки и другие служебные признаки.

### 4.3 Шаблоны вопросов

Шаблоны вопросов задают question flow при старте обращения. В проекте они используются для:

- уточнения проблемы;
- выбора бизнеса, направления или продукта;
- сбора аналитических атрибутов;
- маршрутизации пользовательского сценария.

### 4.4 Аналитические атрибуты

Часть ответов из шаблонов может попадать в dashboard и аналитические выборки. Поэтому значения и атрибуты нужно вести аккуратно и предсказуемо, без разрыва между UI, ботами и analytics-слоем.

## 5. Runtime-контуры и БД

### 5.1 Production storage

Canonical production business/runtime storage — PostgreSQL. `spring-panel` не выбирает SQLite как live datasource и не создаёт отдельные business SQLite-файлы для panel/identity/monitoring/client/knowledge/object контуров.

Legacy `*.db` и per-channel shard files, если они ещё присутствуют, являются historical/test evidence only. Текущий runtime не создаёт technical worker SQLite store и не содержит first-party archive/import/recovery path для этих файлов.

### 5.2 Historical bot shard directory

`bot_databases/` может существовать как остаточный каталог со старыми `bot-<channelId>.db`. Он не нужен normal startup, не является production state и не должен переноситься как часть рабочего runtime package, если только отдельно не сохраняется audit/history evidence.

### 5.3 Attachments

`attachments/` - это не вторичный мусор, а рабочее хранилище файлов:

- пользовательские вложения;
- knowledge base файлы;
- аватары;
- иные артефакты UI и runtime.

Если не перенести `attachments/`, часть исторических данных визуально и функционально "сломается".

Отдельно важно помнить, что исторически часть bot-side файлов может лежать и в `java-bot/attachments/`, если runtime запускался из каталога `java-bot`.

Для быстрой инвентаризации storage и поиска path drift используйте:

```bash
python scripts/report-iguana-storage.py
```

Либо запустите тот же inventory из админки:

- `Настройки -> Storage inventory Iguana`
- панель сохранит markdown/json snapshot в `run/storage-inventory/` и покажет raw report прямо в modal-окне

Скрипт:

- считает размер и состав filesystem storage roots;
- показывает количество файлов, общий объём, top extensions и крупнейшие файлы;
- не открывает legacy relational DB files: relational checks остаются в PostgreSQL/backend diagnostics.

## 6. Конфигурация и переменные окружения

### 6.1 Что конфигурируется окружением

Через env обычно задаются:

- токены и секреты каналов;
- canonical PostgreSQL datasource parameters;
- пути к storage;
- сетевые и runtime-параметры запуска.

### 6.2 Наиболее важные env-переменные

Production database/runtime:

- `APP_DB_MODE=postgresql`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `APP_INTEGRATION_TRANSPORT_MODE`
- `APP_COORDINATION_MODE`

Legacy `APP_DB_*`, `SUPPORT_BOT_DATABASE_PATH` и `APP_BOT_DATABASE_DIR` могут встречаться только в historical/test evidence; они не являются поддерживаемыми runtime inputs и не включают SQLite runtime для панели.

Storage:

- `APP_STORAGE_ATTACHMENTS`
- `APP_STORAGE_KNOWLEDGE_BASE`
- `APP_STORAGE_AVATARS`
- `APP_STORAGE_WEBFORMS`

Каналы:

- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_BOT_USERNAME`
- `VK_BOT_ENABLED`
- `VK_BOT_TOKEN`
- `VK_GROUP_ID`
- `MAX_BOT_ENABLED`
- `MAX_BOT_TOKEN`
- `MAX_SUPPORT_CHAT_ID`

Подробности и примеры смотрите в [environment_variables.md](environment_variables.md).

### 6.3 Что настраивается через UI

После базового запуска значительная часть прикладной конфигурации живёт в панели:

- каналы и боты;
- шаблоны вопросов;
- сценарии оценок;
- авто-действия;
- сотрудники и роли;
- оргструктура;
- некоторые dashboard-виджеты и аналитические сущности.

## 7. Как запускать проект на Windows

### 7.1 Базовый сценарий

```powershell
cd spring-panel
.\run-windows.bat
```

Что делает скрипт:

- проверяет наличие `Java 17`;
- пытается использовать локальный Maven wrapper;
- при необходимости подбирает свободный HTTP-порт вместо занятого `8080`;
- запускает `spring-boot:run`.

### 7.2 Переменные запуска панели

Чаще всего достаточно:

```powershell
$env:JAVA_OPTS='-Xmx1024m'
$env:SPRING_OPTS='--server.port=8080'
.\run-windows.bat
```

Дополнительно проект поддерживает эксплуатационные env-ключи для путей к БД и storage, если нужно запускать не с дефолтными файлами.

### 7.3 Что делать после старта панели

1. Открыть `http://localhost:8080/` или порт, который выбрал startup-скрипт.
2. Проверить вход в панель.
3. Перейти в `Настройки -> Каналы (боты)`.
4. Проверить конфигурацию канала.
5. Запустить нужный bot runtime.

## 8. Как обычно запускается рабочий контур

Рекомендуемая последовательность:

1. Убедиться, что canonical PostgreSQL и shared JSON-конфиги доступны; legacy SQLite artifacts не участвуют в normal startup.
2. Поднять `spring-panel`.
3. Убедиться, что страница настроек открывается без ошибок.
4. Проверить список каналов и состояние bot runtime.
5. Запустить нужных ботов из панели.
6. Проверить входящие обращения тестовым сообщением.

Важно: запуск панели сам по себе не всегда означает, что все каналы уже принимают сообщения. Часто отдельно нужно поднять соответствующие bot runtime.

## 9. Операционные сценарии внутри Iguana

### 9.1 Работа оператора

Оператор обычно выполняет такой цикл:

1. Открывает страницу диалогов.
2. Видит очередь новых и активных обращений.
3. Заходит в конкретный диалог.
4. Смотрит историю сообщений и контекст клиента.
5. Отвечает клиенту из панели.
6. При необходимости переадресует, подключает участников или закрывает обращение.

### 9.2 Question flow

Если у канала включены шаблоны вопросов, клиент может проходить через question flow до открытия или полноценного наполнения обращения. Это влияет на:

- стартовый текст;
- аналитические атрибуты;
- маршрутизацию;
- ветвление сценария;
- последующие dashboard-разрезы.

### 9.3 Авто-действия

В системе есть сценарии авто-закрытия и сопутствующей логики. Если что-то "не срабатывает", проверять нужно одновременно:

- настройки шаблонов и каналов;
- scheduler/планировщики;
- факт записи данных в runtime БД;
- логи панели и конкретного bot runtime.

## 10. Что важно знать про перенос на другую машину

Для рабочего переноса нужно мыслить не только исходниками, но и состоянием системы.

Переносить нужно как минимум:

- исходники репозитория;
- `config/shared/`;
- PostgreSQL backup/restore artifact или доступ к canonical PostgreSQL;
- `bot_databases/` и legacy root `*.db` только если они нужны для controlled archive/import/recovery;
- `attachments/`, если нужны файлы и история вложений;
- актуальную документацию.

Обычно не нужно переносить:

- `.git/`;
- `.venv/`;
- `node_modules/`;
- `target/`;
- старые `logs/`;
- `run/`;
- временные recovery-каталоги.

Полный пошаговый сценарий вынесен в [IGUANA_TRANSFER_WINDOWS.md](IGUANA_TRANSFER_WINDOWS.md).

## 11. Типовой troubleshooting

### 11.1 Панель не стартует

Проверьте:

- установлен ли `JDK 17`;
- не занят ли HTTP-порт;
- доступен ли canonical PostgreSQL и корректны ли datasource credentials;
- корректны ли PostgreSQL datasource/storage env values;
- что пишет `logs/spring-panel.log`.

### 11.2 Бот не стартует

Проверьте:

- токены канала;
- доступность нужного порта;
- bot-specific лог;
- состояние channel config в панели;
- доступность canonical PostgreSQL, queue/API boundary и bot-specific configuration.

### 11.3 В панели нет данных или пропали вложения

Почти всегда это означает одну из причин:

- подключён неверный или пустой PostgreSQL database/schema;
- не перенесён required filesystem/object-storage content;
- выставлены неверные `APP_STORAGE_*` пути;
- восстановлен не тот PostgreSQL backup или используется неверное окружение.

### 11.4 После переноса "всё открылось, но не то"

Обычно виноваты:

- отсутствующие `config/shared/*.json`;
- новый пустой runtime-контур вместо реальных БД;
- другие токены каналов;
- другой набор env-переменных на новой машине.

## 12. Что читать дальше

Если нужна глубокая эксплуатация:

- [IGUANA_DATA_LIFECYCLE_AND_STORAGE_STRATEGY.md](IGUANA_DATA_LIFECYCLE_AND_STORAGE_STRATEGY.md)
- [windows_setup.md](windows_setup.md)
- [configuration.md](configuration.md)
- [environment_variables.md](environment_variables.md)
- [database-paths.md](database-paths.md)
- [database_distribution.md](database_distribution.md)

Если нужна transport/runtime-часть:

- [java_bot.md](java_bot.md)
- [vk_bot_setup.md](vk_bot_setup.md)
- [max_bot_setup.md](max_bot_setup.md)
- [BOT_RUNTIME_CONTRACT.md](BOT_RUNTIME_CONTRACT.md)

Если нужна архитектурная перспектива:

- [OOP_ARCHITECTURE_OVERVIEW.md](OOP_ARCHITECTURE_OVERVIEW.md)
- [ARCHITECTURE_AUDIT_2026-04-08.md](ARCHITECTURE_AUDIT_2026-04-08.md)
- [ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md](ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md)

## 13. Короткий вывод

Iguana нужно воспринимать как рабочую систему со stateful-контуром:

- код;
- canonical PostgreSQL data;
- shared JSON-конфиги;
- filesystem/object-storage content;
- bot runtime, queue/API boundaries и channel configuration.

Чем точнее эта картина отражена в документации и переносе, тем меньше риск получить "пустую", частично сломанную или непредсказуемую копию на новой машине.
