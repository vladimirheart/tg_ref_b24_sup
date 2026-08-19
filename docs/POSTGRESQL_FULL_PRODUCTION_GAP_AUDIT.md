# PostgreSQL Full Production Gap Audit

Документ фиксирует фактический gap между текущим состоянием репозитория после `01-181`/`01-182` и целевым production contour, где система живёт на canonical `PostgreSQL` с backend-owned transport boundary.

Актуально на `2026-08-19`.

## 1. Что уже закрыто

- fresh `PostgreSQL-first` запуск проекта подготовлен;
- основные active runtime-path `Telegram` / `VK` / `MAX` уже смещены к backend-owned boundary;
- bot-side task/follow-up fallback больше не живёт как production-like bean-layer в `rabbitmq`-контуре;
- `java-bot` не должен владеть production schema в external PostgreSQL path;
- RabbitMQ уже начал использоваться как реальный transport boundary для части live flows.
- `java-bot` в `rabbitmq` contour больше не должен молча откатываться в local `JPA/SQLite` business-path, если internal panel API для ticket/channel/feedback/blacklist операций не настроен.
- отдельные `settings.db`, `bot_runtime.db`, `clients.db`, `knowledge_base.db`, `objects.db`, `panel_identity.db` и `monitoring.db` больше не висят как production-like Spring datasource graph в external PostgreSQL runtime;
- legacy `bot-<channelId>.db` выведен из live topology и переведён в backend-owned consolidation/import path;
- canonical incident domain реализован на backend-owned storage:
  - `incidents`;
  - `incident_relations`;
  - `incident_events`;
  - `incident_watchers`;
  - `incident_routes`;
- dialogs/tasks/object passports уже умеют читать incident summaries из canonical backend contour.
- Redis lease coordination и object-storage readiness уже входят в canonical PostgreSQL runtime contract;
- transport contour получил operator-facing ops слой:
  - analytics/API surface для inbox/outbox/checkpoints/incidents;
  - ручные replay/requeue действия;
  - automatic signal-incident monitoring для transport degradation.
- incident routes больше не являются только metadata:
  - появился durable `incident_route_delivery_outbox`;
  - delivery работает через leased dispatcher и retry semantics;
  - incident details возвращают latest route delivery snapshot для operability/replay.
- финальный audit background/live coordination выполнен:
  - instance-local round-robin cursors в assignment/auto-assign больше не определяют live routing decisions;
  - SLA escalation webhook cooldown переведён в shared coordination layer;
  - локальные UI/diagnostic loops и explicit local-control invariants вынесены в отдельный production runbook.
- operator-facing runbook под фактический contour теперь зафиксирован в `docs/runbooks/postgresql-production-contour.md`.

## 2. Что ещё не даёт считать проект PostgreSQL-only production system

### 2.1. SQLite compatibility perimeter всё ещё существует

Проект уже не живёт на multi-SQLite production wiring, но explicit compatibility perimeter всё ещё остаётся:

- `application-sqlite.yml` и `APP_DB_MODE=sqlite`;
- lazy bootstrap/helpers вокруг local SQLite path;
- legacy import/consolidation flows для старых `*.db`;
- SQLite-specific migrations/tests/dev runbooks.

Это допустимо как transitional/dev/import perimeter, но не является финальным production contour.

### 2.2. Runtime infra contour в базовом виде уже собран, но operational maturity ещё не финализирована

Основной незакрытый gap уже не в datasource split и не в отсутствии infra-компонентов, а в последних residual invariants:

- Redis lease coordination и shared counter/cooldown coordination уже внедрены для shared schedulers/watchers/live routing decisions;
- MinIO/S3-compatible object storage уже стал обязательной readiness boundary для PostgreSQL contour, но compatibility/local perimeter ещё остаётся для dev/import режимов;
- RabbitMQ-first transport model уже materially жёстче, включая producer outbox, consumer scaling, delivery ledger и panel-side replay/requeue ops;
- главный remaining live-flow gap сместился в bot-side ingress question-flow/session state, который пока остаётся process-local и требует singleton/sticky deployment policy на один канал.

### 2.3. Bootstrap всё ещё сохраняет SQLite compatibility mode

После стартового среза `01-183` first-run bootstrap больше не должен молча откатываться в SQLite при недоступном Docker, но SQLite compatibility mode всё ещё существует как явный override.

Это уже лучше, чем normal fallback-path, но всё ещё не финальная цель, где живой проект полностью мыслится через canonical PostgreSQL contour и SQLite остаётся только в test/import/legacy compatibility ролях.
Дополнительно one-time import/recovery из legacy SQLite уже переведён в explicit opt-in и больше не должен автоматически стартовать в каждом PostgreSQL runtime только из-за присутствия старых `*.db` файлов рядом с репозиторием.

### 2.4. Documentation и compatibility perimeter всё ещё несут transitional topology

Код уже ушёл дальше старой multi-SQLite/local-disk модели, и основной production runbook для фактического contour теперь добавлен. Remaining documentation debt уже уже и в основном связан с постепенной синхронизацией старых reference-доков:

- `docs/database_distribution.md`;
- `docs/database-paths.md`;
- `docs/environment_variables.md`;
- SQLite/local compatibility runbooks и migration references.

Ключевой operator-facing closeout теперь вынесен в `docs/runbooks/postgresql-production-contour.md`, но часть older reference docs всё ещё несёт более transitional narrative, чем реальный live contour.

### 2.5. Incident module есть в backend contour, но ещё не закрывает весь operator/ops слой

Canonical incident domain на backend-owned storage уже появился, operator-facing workbench тоже реализован. Remaining scope по incident-теме теперь уже заметно уже:

- richer signal ingestion / automatic incident creation beyond the current transport monitor and linked domain reads;
- более глубокая incident reporting / analytics поверх уже собранного workbench и route delivery contour.

## 3. Что должно стать next production scope

### 3.1. Canonical PostgreSQL consolidation

- удерживать уже достигнутую консолидацию как invariant;
- продолжать вычищать только те SQLite helper-paths, которые ещё могут быть ошибочно приняты за live contour;
- закрыть final schema ownership через Flyway/PostgreSQL для новых production-domain расширений.

### 3.2. Final transport/backend ownership

- довести active worker flows до queue/API boundary;
- исключить прямой доступ integration workers к business DB;
- оставить local runtime state только как technical/per-channel contour, если он реально нужен.

### 3.3. Infra contour

- удержать Redis/RabbitMQ/object-storage contract как invariant production contour;
- удерживать уже закрытый multi-instance audit как invariant и не возвращать instance-local shared decision state;
- довести alerting/reporting/observability слой до end-to-end production maturity там, где это ещё нужно.

### 3.4. Incident module

- signal -> incident model и richer automation;
- production analytics/reporting для incident operations.

## 4. Практический вывод

После `01-181` и `01-182` проект находится в состоянии:

- `PostgreSQL-first readiness`: да;
- `backend-owned main runtime path`: в основном да;
- `canonical incident backend domain`: да, operator-facing слой тоже реализован;
- `full PostgreSQL-only production contour`: ещё нет.

Следующий корректный scope — уже не chase за очередной SQLite-точкой и не базовый incident UI, а оставшийся ingress/session-state hardening и richer reporting/automation поверх уже собранного contour.
