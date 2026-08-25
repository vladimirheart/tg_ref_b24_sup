# Актуальная документация проекта Iguana

## Статус документа

- Актуально на `25 августа 2026 года`.
- Этот документ описывает текущее состояние репозитория и runtime-контуров на момент обновления.
- Если более узкоспециализированный документ в `docs/` описывает частный контур глубже, его нужно считать источником деталей, а этот документ - основной обзорной и навигационной точкой.

## 1. Назначение проекта

`Iguana` - это единый support-контур для работы с клиентскими обращениями, операторскими процессами и сопутствующей эксплуатацией.

Система объединяет:

- приём обращений из `Telegram`, `VK` и `MAX`;
- операторскую обработку диалогов;
- карточки клиентов и историю сообщений;
- шаблоны вопросов, маршрутизацию и правила SLA;
- AI Ops и AI-assisted workflow;
- мониторинг, инциденты, отчёты и эксплуатационные сценарии;
- управление runtime-процессами ботов;
- shared-конфигурацию проекта, вложения и интеграционные каталоги.

Главная особенность репозитория: здесь важны не только исходники, но и данные, конфигурация, runtime-файлы и инфраструктурные границы. Iguana нельзя воспринимать как “только код”.

## 2. Как читать эту документацию

Если нужно быстро войти в проект:

1. Прочитать этот документ.
2. Прочитать [README.md](../README.md).
3. Для запуска и окружения открыть [IGUANA_PROJECT_GUIDE.md](./IGUANA_PROJECT_GUIDE.md), [configuration.md](./configuration.md), [environment_variables.md](./environment_variables.md).
4. Для данных и storage открыть [database-paths.md](./database-paths.md), [database_distribution.md](./database_distribution.md), [IGUANA_DATA_LIFECYCLE_AND_STORAGE_STRATEGY.md](./IGUANA_DATA_LIFECYCLE_AND_STORAGE_STRATEGY.md).
5. Для production target-state открыть [target-production-architecture-plan.md](./target-production-architecture-plan.md), [POSTGRESQL_FIRST_READINESS_CLOSEOUT.md](./POSTGRESQL_FIRST_READINESS_CLOSEOUT.md) и [runbooks/postgresql-production-contour.md](./runbooks/postgresql-production-contour.md).
6. Для реального go-live checklist и пошагового запуска открыть [runbooks/production-launch-checklist.md](./runbooks/production-launch-checklist.md).

## 3. Текущее архитектурное состояние

### 3.1. Коротко

На `25 августа 2026 года` Iguana находится в состоянии `PostgreSQL-first` проекта с сохранённым `SQLite` compatibility perimeter.

Это означает:

- основной target-state системы - backend-owned production contour;
- `spring-panel` является центром ownership для business data и operator-facing workflow;
- `java-bot` больше не должен быть владельцем business schema в production-контуре;
- `RabbitMQ`, `Redis` и object storage уже входят в целевую модель;
- legacy `SQLite` остаётся для local/dev/bootstrap/compatibility сценариев и для части transitional артефактов.

### 3.2. Что считается главным архитектурным направлением

Текущее направление подтверждается одновременно:

- конфигурацией `spring-panel/src/main/resources/application.yml`;
- runtime contract между панелью и ботами;
- проектными правилами в `ai-context/rules/backend/`;
- документами по переходу к production contour в `docs/`.

Практический вывод:

- бизнес-данные и основные правила домена принадлежат backend-контуру;
- transport workers и channel runtimes должны работать через queue/API boundary;
- новые архитектурные изменения нельзя строить вокруг возврата к “бот как владелец бизнес-таблиц”.

## 4. Карта репозитория

| Путь | Назначение | Роль в архитектуре |
| --- | --- | --- |
| `spring-panel/` | основное Spring Boot приложение | UI, backend, orchestration, settings, analytics, incidents |
| `java-bot/` | multi-module runtime ботов | transport layer, inbound/outbound channel adapters |
| `config/shared/` | shared JSON-конфиги | общий прикладной контур настроек и справочников |
| `attachments/` | файловое хранилище | пользовательские вложения, аватары, KB-файлы, фото паспортов |
| `bot_databases/` | legacy shard-файлы `bot-<channelId>.db` | import/diagnostics perimeter, не target-state |
| `docs/` | документация | эксплуатация, архитектура, migration, storage, runbooks |
| `scripts/` | bootstrap и служебные сценарии | запуск, preflight, export, inventory, Windows automation |
| `run/` | pid и runtime state | локальные служебные файлы процессов |
| `logs/` | логи | диагностика панели и ботов |
| `ai-context/` | AI task-flow, changelog, правила | контур сопровождения разработки |

## 5. Верхнеуровневые подсистемы

## 5.1. `spring-panel`

`spring-panel` - центральное приложение системы. Оно отвечает за:

- web UI;
- безопасность и аутентификацию;
- операторский workflow;
- управление диалогами, задачами, инцидентами, клиентами и базой знаний;
- конфигурацию каналов, тем, параметров, каталогов и интеграций;
- orchestration и diagnostics bot runtime;
- аналитические и monitoring-сценарии;
- production readiness probes;
- integration boundary с `RabbitMQ`, `Redis`, object storage и внутренними API.

### 5.1.1. Основные пакеты `spring-panel`

Текущее устройство Java-кода в `com.example.panel`:

- `controller/`
  - HTTP entrypoints.
  - Включает page controllers, API controllers и специализированные slices вроде `Dialog*`, `Analytics*`, `Incident*`, `Settings*`.
- `service/`
  - Основная бизнес-логика, orchestration и runtime governance.
  - Это самый насыщенный слой проекта.
- `service/integration/`
  - transport/inbox/outbox, ingestion, event boundary, health snapshot и queue-сценарии.
- `repository/`
  - JPA repositories и JDBC-support классы.
- `entity/`
  - operator-facing и domain сущности: `Ticket`, `Message`, `Channel`, `Notification`, `Task`, `Incident`, `KnowledgeArticle`, client-таблицы и др.
- `model/`
  - DTO, summary models, page payloads, projection-like objects.
- `config/`
  - datasource, DB mode, session, cache, external DB settings, bootstrap defaults и др.
- `security/`
  - security config, filters, bootstrap, `UserDetailsService`.
- `storage/`
  - attachment/object storage boundary и вычисление storage keys.
- `background/`
  - фоновая синхронизация и housekeeping.
- `support/`
  - низкоуровневые JDBC/SQL helper-контракты.

### 5.1.2. Основные UI-страницы `spring-panel`

Серверные шаблоны лежат в `spring-panel/src/main/resources/templates/` и на текущий момент включают:

- `dialogs/` - рабочее место операторов и AI Ops;
- `settings/` - административный контур;
- `dashboard/` - сводная отчётность;
- `analytics/` - deeper analytics и monitoring UI;
- `clients/` - клиенты и unblock requests;
- `knowledge/` - база знаний;
- `incidents/` - incidents workbench;
- `tasks/` - задачи;
- `channels/`, `users/`, `passports/`, `auth/`;
- `fragments/` - общие фрагменты head/navbar;
- `error/` - специализированные error pages.

### 5.1.3. Внутренние срезы backend-а

По коду хорошо видны несколько крупных bounded use-case групп:

- dialogs workspace;
- settings domain;
- analytics и governance;
- incidents;
- monitoring;
- knowledge base;
- clients/profile/unblock;
- bot runtime orchestration;
- production readiness;
- integration transport и internal event flows.

Это важно для развития проекта: новые изменения нужно встраивать в существующий bounded use-case, а не создавать “универсальный god-service”.

## 5.2. `java-bot`

`java-bot` - multi-module Maven-проект с адаптерами внешних каналов и общим transport/runtime ядром.

Модули:

- `bot-core`
- `bot-telegram`
- `bot-vk`
- `bot-max`

### 5.2.1. Роль `bot-core`

`bot-core` содержит:

- общие entity/repository compatibility-слои;
- channel/runtime services;
- inbound message publication;
- feedback/outbound prompt delivery;
- ticket/task/chat-related compatibility service layer;
- настройку datasource/runtime mode;
- coordination и webhook delivery guard;
- transport outbox / dedup / delivery ledger слой;
- интеграцию с `spring-panel` через internal API boundary.

### 5.2.2. Роль channel-модулей

- `bot-telegram` - Telegram adapter/runtime.
- `bot-vk` - VK adapter/runtime и webhook controller.
- `bot-max` - MAX adapter/runtime, webhook и long polling lifecycle.

Эти модули не должны становиться владельцами operator-facing бизнес-модели. Их роль - transport boundary и platform-specific integration logic.

## 5.3. `config/shared`

Shared JSON-конфигурация остаётся важной частью системы.

Ключевые файлы:

- `settings.json` - прикладные настройки и каталоги;
- `locations.json` - дерево локаций и связанный operational context;
- `org_structure.json` - оргструктура;
- сопутствующие machine/business-specific JSON-артефакты.

Практический смысл:

- перенос проекта без этих файлов даёт неполноценный runtime;
- не вся прикладная конфигурация живёт в БД;
- часть административного и интеграционного поведения зависит от этих shared файлов.

## 6. Данные и ownership

## 6.1. Главный принцип

Текущий архитектурный принцип проекта:

- `spring-panel` владеет business data;
- production business contour должен жить в backend-owned data plane;
- bot runtimes не должны владеть business schema;
- legacy `SQLite` и bot shard-файлы - это transitional perimeter, а не цель.

Это зафиксировано в `ai-context/rules/backend/05-iguana-production-storage-boundaries.md`.

## 6.2. Runtime data contours

В compatibility- и local-runtime слоях используются следующие logical/physical contours:

| Контур | Текущий physical файл / режим | Комментарий |
| --- | --- | --- |
| `panel-runtime` | `panel_runtime.db` или primary external DB | диалоги, сообщения, задачи, notifications, knowledge, clients и большая часть business state |
| `panel-identity` | `panel_identity.db` | пользователи, роли, часть identity state |
| `monitoring` | `monitoring.db` | monitoring tables, history, rollups |
| `bot-runtime` | `bot_runtime.db` | bot-side compatibility/runtime data |
| `secondary transitional` | `clients.db`, `knowledge_base.db`, `objects.db` | исторические/переходные контуры |
| `legacy shards` | `bot-<channelId>.db` | import-only / diagnostics perimeter |

Подробное ownership-описание лежит в:

- [database-paths.md](./database-paths.md)
- [database_distribution.md](./database_distribution.md)
- [db/sqlite-target-topology.md](./db/sqlite-target-topology.md)
- `ai-context/rules/backend/04-sqlite-topology.md`

## 6.3. Что нельзя делать при новых изменениях

- Нельзя добавлять новые бизнес-таблицы в per-channel `bot-<channelId>.db`.
- Нельзя воспринимать `settings.db` как нормальный runtime business contour.
- Нельзя возвращать bot-side direct JDBC ownership как production-модель.
- Нельзя проектировать новый production-функционал, исходя из того, что `SQLite` - главный живой контур.

## 6.4. Attachment и object storage слой

Слой хранения бинарных данных состоит из:

- локального `attachments/`;
- логических подпапок knowledge/passport/avatar;
- object storage boundary в конфиге `app.storage.object.*`;
- сервисов `AttachmentObjectStorageService`, `AttachmentService`, `AttachmentStorageKeyResolver`, `ObjectPassportPhotoStorageService`.

Практический смысл:

- проект уже допускает production contour с `MinIO/S3`;
- локальное файловое хранилище сохраняется как совместимый и dev-friendly путь;
- вложения и metadata должны развиваться согласованно, без split brain между DB и filesystem.

## 7. Инфраструктурные зависимости

## 7.1. PostgreSQL

На `25 августа 2026 года` основной normal runtime default панели - `APP_DB_MODE=postgresql`.

Это видно в `spring-panel/src/main/resources/application.yml`.

PostgreSQL - это целевой primary contour для:

- operator-facing business state;
- incident domain;
- transport/inbox/outbox data;
- production data ownership.

## 7.2. RabbitMQ

`RabbitMQ` используется как transport boundary.

Текущий конфиг содержит:

- inbound exchange/queue;
- ticket-created queue;
- dead-letter routing;
- concurrency и prefetch настройки;
- routing keys по каналам `Telegram`, `VK`, `MAX`.

Это означает, что каналам и transport workers всё меньше нужна прямая зависимость от локального business JDBC path.

## 7.3. Redis

`Redis` - часть coordination contour.

В конфиге панели:

- `app.coordination.mode=redis`;
- lease namespace и production guardrails;
- требуется для PostgreSQL contour.

Практически это нужно для:

- coordination;
- leases;
- shared runtime semantics в multi-worker контуре.

## 7.4. Object storage

В target production contour объектное хранилище обязательно для production-like режима PostgreSQL.

Настройки:

- `APP_STORAGE_OBJECT_MODE`
- `APP_STORAGE_OBJECT_BUCKET`
- `APP_STORAGE_OBJECT_ENDPOINT`
- `APP_STORAGE_OBJECT_ACCESS_KEY`
- `APP_STORAGE_OBJECT_SECRET_KEY`
- и др.

## 8. Runtime modes и конфигурация

## 8.1. Панель

Основные runtime-переключатели панели:

- `APP_DB_MODE`
- `APP_INTEGRATION_TRANSPORT_MODE`
- `APP_STORAGE_OBJECT_MODE`
- `APP_COORDINATION_MODE`
- `APP_HTTP_PORT`

Текущее значение по умолчанию:

- `APP_DB_MODE=postgresql`
- `APP_INTEGRATION_TRANSPORT_MODE=jdbc` как config default, но production contour ориентирован на `rabbitmq`
- `APP_COORDINATION_MODE=redis`

## 8.2. Боты

Ключевые runtime-контракты ботов описаны в [BOT_RUNTIME_CONTRACT.md](./BOT_RUNTIME_CONTRACT.md).

Главное:

- production worker path - это `APP_DB_MODE=worker` + `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`;
- child process не должен наследовать canonical business DB credentials;
- `java-bot` в worker-режиме не должен молча откатываться в local business storage;
- `SQLite` path допустим только как явный compatibility/dev путь.

## 8.3. Запуск и orchestration

Панель умеет управлять ботами через launcher contract:

- `app.bots.launch-mode`
- `app.bots.executable-jars`
- `app.bots.preferred-production-launcher`
- `app.bots.recommended-artifact-directory`
- startup readiness timeout/poll interval

Текущий рекомендуемый production path:

- собранные prebuilt `jar`;
- явный `module -> jar path`;
- запуск без зависимости от `spring-boot:run` как боевого механизма.

## 9. Основные потоки данных

## 9.1. Входящее сообщение клиента

Актуальная целевая модель потока такая:

1. Внешняя платформа доставляет сообщение в channel runtime.
2. Runtime-слой канала преобразует его в внутренний event/command.
3. Сообщение проходит через queue/API boundary.
4. Backend ingest-слой панели принимает событие.
5. Панель создаёт или обновляет бизнес-состояние диалога.
6. UI и операторские сервисы читают уже backend-owned source of truth.

## 9.2. Ответ оператора

1. Оператор работает в `dialogs` workspace панели.
2. Reply формируется через panel-side workspace/reply services.
3. Сообщение и связанные side effects фиксируются в backend data contour.
4. Transport/export в канал уходит через channel transport boundary.
5. Delivery outcome попадает в audit/history/runtime diagnostics.

## 9.3. Настройки

1. Администратор открывает `settings`.
2. Сервер рендерит базовую оболочку и bootstrap payload.
3. Page-specific runtime-модули гидратируют секции и модалки.
4. Изменения сохраняются в соответствующий owner-контур:
   - shared config;
   - DB settings parameters;
   - runtime configuration;
   - integration-specific data.

## 9.4. UI preferences

1. Сервер может отдать bootstrap операторских UI preferences в `<head>`.
2. Клиентский runtime записывает/читает их через `window.iguanaUiPreferences`.
3. Для authenticated operator truth находится в server-backed preferences.
4. `localStorage` используется только как cache/fallback runtime layer.

## 10. UI-архитектура

## 10.1. Базовая модель UI

Текущий UI Iguana - это `server-rendered application` с progressive enhancement.

Базовая схема:

- HTML рендерится Thymeleaf-страницами;
- общие фрагменты и метаданные подключаются через `fragments/ui-head.html` и `fragments/navbar.html`;
- Bootstrap используется как базовый UI framework;
- page-specific поведение добавляется JavaScript runtime-модулями;
- CSS собирается из `SCSS` через Maven-пайплайн.

Это важно: проект не является SPA в классическом смысле. Основная точка истины для shell и базового контента остаётся на сервере.

## 10.2. Глобальный shell

Общие элементы оболочки:

- `fragments/ui-head.html`
  - favicon;
  - CSRF meta;
  - bootstrap UI preferences;
  - подключение `ui-preferences.js`, `theme.js`, `ui-config.js`, `modal-resize.js`.
- `fragments/navbar.html`
  - единый sidebar;
  - mobile toggle и overlay;
  - notifications dropdown;
  - runtime strip со статусами;
  - account block;
  - action menu;
  - модалка смены пароля.

Следствие для разработки:

- не нужно копировать shell по страницам;
- новые страницы должны использовать существующие fragments;
- любые изменения навигации нужно делать централизованно через sidebar fragment и его runtime.

## 10.3. Page preset модель

Текущая подсистема `ui-config.js` задаёт page presets через `data-ui-page` и UI tokens.

Поддерживаемые страницы:

- `dashboard`
- `analytics`
- `settings`
- `ui-kit`
- `dialogs`
- `clients`
- `knowledge`
- `channels`
- `users`
- `tasks`
- `passports`
- `public`
- `ai-ops`

Preset определяет часть UI-семантики:

- `density`
- `hero`
- `shell`
- `panel`
- частично page-specific дополнительные значения вроде `chart`, `detail`, `list`

Следствие:

- каждая новая или существенно меняемая страница должна иметь осмысленный `data-ui-page`;
- page-level визуальная логика должна строиться на preset/token semantics, а не на случайных ad hoc классах;
- если нужен новый визуальный режим, сначала надо понять, достаточно ли существующих preset values.

## 10.4. Подсистема тем и палитр

`theme.js` подтверждает текущий допустимый набор:

- `theme`: `light`, `dark`, `auto`
- `themePalette`: `neo`, `catppuccin`, `amber-minimal`

Текущие правила:

- тема и палитра применяются к `document.documentElement` и `body` через dataset;
- `auto` зависит от `prefers-color-scheme`;
- прямые raw-записи в `localStorage` считать нежелательными;
- изменение темы должно идти через общий runtime preferences API.

## 10.5. Подсистема UI preferences

Актуальный ownership описан также в [UI_PREFERENCES_OWNERSHIP.md](./UI_PREFERENCES_OWNERSHIP.md).

Источники истины разделены так:

- `settings.json`
  - shared project-level и integration-level настройки;
- `settings_parameters`
  - server-backed operator preferences и настраиваемые параметры;
- `localStorage`
  - cache/fallback/browser-only runtime слой.

Из текущего кода следует правило:

- если preference должна переживать браузер и принадлежит оператору, она должна жить в server-backed storage;
- если это только локальный временный UI state, допустим `localStorage`;
- новые UI preferences должны идти через `window.iguanaUiPreferences`, а не через произвольные ключи в браузере.

## 10.6. Текущее устройство страниц `dialogs`

`dialogs/index.html` и связанный runtime подтверждают, что `dialogs` - это самый сложный workspace проекта.

Ключевые принципы:

- есть общий page shell и toolbar;
- есть основной workspace-контур;
- есть модульные runtime-скрипты:
  - `dialogs-list-runtime.js`
  - `dialogs-shell-runtime.js`
  - `dialogs-avatar-runtime.js`
  - `dialogs-sla-runtime.js`
  - `dialogs-presentation-runtime.js`
  - `dialogs-ai-runtime.js`
  - `dialogs-details-history-runtime.js`
  - `dialogs-details-runtime.js`
  - `dialogs-workspace-runtime.js`
  - `dialogs-actions-runtime.js`
  - `dialogs-participants-runtime.js`
  - `dialogs-my-dialogs-runtime.js`
  - `dialogs-macro-runtime.js`
  - `dialogs-notifications-runtime.js`
  - `dialogs-templates-runtime.js`
  - `dialogs-flow-runtime.js`
  - `dialogs-experiment-runtime.js`
  - `dialogs-media-composer-enhancements.js`
  - `dialogs.js`

Практический вывод:

- новая логика `dialogs` по возможности должна идти в профильный runtime-модуль, а не раздувать один монолит;
- `dialogs.js` лучше воспринимать как orchestration/compat layer, а не как место для любого нового кода;
- workspace page проектируется как operational cockpit, а не как простая таблица.

## 10.7. Текущее устройство страницы `settings`

`settings/index.html` показывает устойчивую архитектуру административного UI:

- overview hero и tile-based входы;
- крупные административные модалки;
- payload bootstrap через `<script type="application/json">`;
- набор специализированных runtime-модулей по зонам ответственности.

Текущие runtime-срезы settings:

- shell/bootstrap/config
- appearance
- dialog config
- channel templates и channel editor
- network profiles
- locations/iiko/netbox
- parameters
- reporting/manager bindings
- production readiness
- admin shell

Практический вывод:

- настройки в Iguana уже не сводятся к одной гигантской JS-странице;
- новый settings-функционал нужно добавлять в bounded runtime slice;
- данные инициализации лучше передавать структурированным bootstrap payload, а не разбрасывать по глобальным переменным.

## 10.8. Sidebar и глобальная навигация

Текущий sidebar - это полноценный operational shell, а не просто список ссылок.

Он включает:

- grouped navigation;
- runtime strip;
- notification center;
- pin/unpin и reorder;
- operator account actions;
- mobile behavior.

Следствие:

- изменения в sidebar имеют cross-page влияние;
- любые новые разделы нужно встраивать в навигационные группы осмысленно;
- sidebar state должен проходить через общий preference/runtime слой.

## 10.9. Styling pipeline

CSS-файлы в `static/css/` собираются из `SCSS` через `dart-sass-maven-plugin`.

Текущие entrypoints:

- `style.scss`
- `app.scss`
- `sidebar.scss`
- `settings.scss`

Структура `scss/` показывает текущие контуры стилей:

- `style/`
  - base, fonts, legacy, theme
- `app/`
  - core, unified UI, dialogs, dashboard, analytics, knowledge, operations, tasks и др.
- `sidebar/`
  - shell, sections, notifications, runtime
- `settings/`
  - foundation, calm, workspace, task-dialogs

Практические правила:

- source of truth для стилей - `scss/`, а не вручную отредактированный собранный CSS;
- новые стили нужно добавлять в соответствующий bounded SCSS slice;
- нельзя смешивать legacy overrides и новый слой без необходимости;
- предпочтение отдаётся token/semi-structured UI semantics, а не ad hoc inline styling.

## 10.10. UI-правила для новых изменений

Ниже - текущие рабочие правила, которые подтверждаются структурой кода и уже выполненными рефакторингами:

1. Новые страницы должны использовать единый shell, а не дублировать `<head>` и sidebar.
2. Страница должна иметь `data-ui-page` и встраиваться в page preset модель.
3. Operator preferences нельзя проектировать как raw `localStorage` truth.
4. Page-specific JS должен жить в соответствующем runtime-модуле.
5. Крупные settings-сценарии должны входить через bounded section/modal flow, а не через одну бесконечную форму.
6. `dialogs` нужно развивать как workspace/cockpit, а не превращать обратно в простой CRUD-лист.
7. UI state, влияющий на несколько страниц или на оператора между сессиями, должен иметь серверный owner.
8. Стили нужно менять из SCSS-источников.
9. Глобальная навигация, тема, density и sidebar behavior считаются shared UI subsystem.
10. Если логика требует bootstrap данных от сервера, лучше передавать их структурированным JSON payload.

## 11. Документационные и архитектурные правила для разработки

## 11.1. Что считать источником истины

- Для общей входной картины - этот документ.
- Для runtime contract панели и ботов - [BOT_RUNTIME_CONTRACT.md](./BOT_RUNTIME_CONTRACT.md).
- Для production target-state - [target-production-architecture-plan.md](./target-production-architecture-plan.md).
- Для storage/data lifecycle - [IGUANA_DATA_LIFECYCLE_AND_STORAGE_STRATEGY.md](./IGUANA_DATA_LIFECYCLE_AND_STORAGE_STRATEGY.md).
- Для DB paths и topology - [database-paths.md](./database-paths.md), [database_distribution.md](./database_distribution.md), [db/sqlite-target-topology.md](./db/sqlite-target-topology.md).
- Для AI/project rules - `ai-context/rules/` и `ai-context/baseline/ai-rules/`.

## 11.2. Как вносить новые архитектурные изменения

Новая архитектурная задача должна:

- явно понимать, какой контур ownership она меняет;
- не ломать production направление `backend-owned business data`;
- не усиливать legacy SQLite split без отдельного решения;
- обновлять связанную документацию, если меняет contract, topology или runtime behavior.

Если изменение касается:

- DB topology - нужно смотреть и обновлять `database-paths.md`, `database_distribution.md`, `db/sqlite-target-topology.md`;
- bot runtime contract - нужно обновлять `BOT_RUNTIME_CONTRACT.md`;
- production contour - нужно проверять влияние на `target-production-architecture-plan.md` и runbooks;
- UI preferences / UI shell - нужно обновлять этот документ и профильные UI docs при изменении правил.

## 12. Что считать актуальной точкой входа в 2026 году

Если нужен один документ для понимания текущего проекта, это именно этот файл.

Он нужен для:

- архитектурного онбординга;
- проектирования новых feature slices;
- понимания ownership-модели данных;
- понимания UI правил;
- согласованного обновления README и смежной документации.

Если в будущем состояние проекта существенно изменится, этот документ нужно обновлять как current snapshot, а не превращать его в исторический архив.
