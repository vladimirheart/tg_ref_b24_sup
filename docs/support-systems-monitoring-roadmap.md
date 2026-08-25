# Support Systems Monitoring Roadmap

Актуально на `2026-08-25`.

## 1. Назначение

Этот документ фиксирует целевую карту monitoring coverage для Iguana как support control center.

Его задача:

- показать, что уже покрыто в Iguana;
- зафиксировать, каких сигналов ещё не хватает для реального support operations;
- определить первую, вторую и третью волну внедрения;
- договориться, какие метрики, алерты и operator surfaces должны появиться по каждой группе зависимостей.

Документ дополняет:

- [observability-baseline.md](./observability-baseline.md)
- [runbooks/production-launch-checklist.md](./runbooks/production-launch-checklist.md)
- [runbooks/postgresql-production-contour.md](./runbooks/postgresql-production-contour.md)

## 2. Что уже покрыто на `2026-08-25`

### 2.1. Внутренний production contour Iguana

Уже есть:

- `Production readiness` для `PostgreSQL`, `Redis`, `RabbitMQ`, object storage и incident delivery;
- transport monitoring по queue backlog, DLQ, replay/requeue и worker diagnostics;
- incident workbench и signal incidents;
- Actuator + Prometheus baseline для `spring-panel`;
- production launch и cutover runbooks.

### 2.2. Прикладной monitoring внутри Iguana

Уже есть monitoring-контуры по:

- SSL;
- RMS;
- iiko API;
- частично transport/runtime health;
- incident route delivery;
- readiness snapshot.

### 2.3. Что это уже даёт

На текущем этапе Iguana неплохо видит:

- своё ядро;
- собственный transport;
- часть внешних интеграций;
- operator-side recovery surfaces.

Но этого ещё недостаточно, чтобы support-команда видела полный operational perimeter смежных систем.

## 3. Чего не хватает

### 3.1. Классические support-зависимости

Недостающие группы сигналов:

- корпоративная почта и SMTP/IMAP relay;
- VPN / bastion / remote access;
- DNS / доменные записи / internal resolution;
- backup freshness и restore drill freshness;
- внешний webhook ingress и публичные callback endpoints;
- срок жизни и валидность API credentials / tokens / webhook secrets;
- статус SSO / identity providers, если они участвуют в operator access;
- telephony / SIP / contact-center зависимости, если они используются рядом с Iguana;
- внешние SaaS-системы поддержки:
  - Bitrix24;
  - NetBox;
  - Notion;
  - iiko;
  - provider APIs каналов `Telegram`, `VK`, `MAX`.

### 3.2. Главный пробел не в "ещё одной метрике"

Недостающий слой сейчас больше организационный, чем purely technical:

- нет общей карты ownership по внешним зависимостям;
- нет приоритетов, что support обязан видеть в первой волне, а что можно отложить;
- не для каждой системы определены:
  - probe;
  - history/timeline;
  - alert threshold;
  - escalation route;
  - operator action после срабатывания.

## 4. Целевая модель monitoring coverage

Iguana должна покрывать 6 слоёв.

### 4.1. Layer A. Core runtime

Сюда входят:

- `spring-panel`;
- `java-bot`;
- `PostgreSQL`;
- `Redis`;
- `RabbitMQ`;
- object storage;
- incident delivery.

Статус:

- уже в основном покрыт.

### 4.2. Layer B. Delivery channels

Сюда входят:

- `Telegram Bot API`;
- `VK`;
- `MAX`;
- inbound webhook ingress;
- outbound reply delivery.

Ожидаемые сигналы:

- inbound success/failure rate;
- outbound success/failure rate;
- auth/signature failures;
- provider 4xx/5xx classification;
- retry pressure;
- sustained delivery lag;
- channel-specific token/secret validity.

### 4.3. Layer C. Support SaaS and business-adjacent systems

Сюда входят:

- `iiko`;
- `Bitrix24`;
- `NetBox`;
- `Notion`;
- возможные CRM/helpdesk/knowledge-base системы рядом с Iguana.

Ожидаемые сигналы:

- API reachability;
- auth validity;
- latency and error trend;
- quota/rate-limit signals;
- last successful sync / last successful mutation;
- частичная деградация по отдельным tenants/sites/profiles.

### 4.4. Layer D. Corporate infra dependencies

Сюда входят:

- DNS;
- SMTP relay / почта;
- VPN / bastion / remote access;
- reverse proxy / public ingress;
- certificate chain and domain expiry;
- NTP/time drift, если подписи и timestamp checks критичны.

Ожидаемые сигналы:

- service reachable/not reachable;
- auth or handshake failures;
- cert expiry and chain mismatch;
- stale backup;
- DNS misresolution or missing record;
- недоступность operator access path.

### 4.5. Layer E. Security and secret hygiene

Сюда входят:

- token expiry;
- webhook secret rotation state;
- bootstrap/admin credential drift;
- remember-me/internal API shared secret rotation reminders;
- privilege-sensitive integrations with expiring user-scoped access.

Ожидаемые сигналы:

- token expires soon;
- token expired;
- secret still default or not rotated;
- repeated auth failure after previously successful period.

### 4.6. Layer F. Recovery confidence

Сюда входят:

- backup freshness;
- restore drill freshness;
- replay/requeue operability;
- наличие recovery evidence;
- последняя успешная cutover rehearsal дата.

Ожидаемые сигналы:

- last backup too old;
- backup exists but restore not tested;
- no successful rehearsal in agreed time window;
- recovery SLA no longer realistic.

## 5. Правила для каждого monitoring source

Для каждой новой подсистемы Iguana должна фиксировать минимум:

1. `overview status`
2. `history / timeline`
3. `last success`
4. `last failure`
5. `severity`
6. `owner`
7. `operator action`
8. `alert route`

Это соответствует уже существующим локальным UX-правилам:

- detail diagnostics должны показывать timeline;
- overview surfaces должны показывать агрегированную доступность.

## 6. Обязательная первая волна

Первая волна должна закрыть то, без чего support реально слепнет в день инцидента.

### 6.1. External channel/API health

Нужно добавить или усилить:

- unified health summary по `Telegram`, `VK`, `MAX`;
- breakdown на inbound и outbound failures;
- auth/token/secret error counters;
- last successful inbound;
- last successful outbound;
- sustained 4xx/5xx detection.

### 6.2. Backup freshness

Нужно добавить:

- статус последнего backup `PostgreSQL`;
- статус последнего backup object storage;
- статус последнего restore drill;
- alert на устаревший backup;
- alert на отсутствие подтверждённого restore.

### 6.3. DNS / public ingress / TLS chain

Нужно добавить:

- domain resolution health;
- public endpoint HTTP/TLS probe;
- expiry и mismatch по certificate chain;
- webhook callback endpoint availability.

### 6.4. SMTP / operator notification path

Нужно добавить:

- статус SMTP relay или канала доставки incident notifications;
- last successful alert delivery;
- last failed alert delivery;
- breakdown permanent vs transient failure.

### 6.5. Secret and token expiry watch

Нужно добавить:

- токены `Telegram` / `VK` / `MAX` / `iiko` / `Bitrix24` / `Notion` / `NetBox`, если возможно контролировать срок действия;
- internal security secrets rotation reminders;
- отдельный warning horizon:
  - `30 days`;
  - `14 days`;
  - `7 days`;
  - `expired`.

## 7. Вторая волна

Вторая волна нужна для уменьшения MTTR и лучшей диагностики partial degradation.

### 7.1. Per-provider profile health

Нужно видеть:

- iiko profile-by-profile;
- NetBox site-by-site;
- Bitrix24 tenant/user-scoped access;
- Notion integration workspace health.

### 7.2. Operator access path

Нужно добавить:

- VPN access health;
- bastion reachability;
- optional SSO/IdP probe;
- operator login failure trend.

### 7.3. Quota/rate-limit awareness

Нужно добавить:

- near-limit counters;
- remaining quota snapshots, если провайдер это отдаёт;
- rate-limit incident classification.

### 7.4. Notification route observability

Нужно различать:

- incident создан, но не доставлен;
- route существует, но канал сломан;
- alert дошёл не туда;
- alert был подавлен mute/cooldown механизмом.

## 8. Третья волна

Третья волна нужна уже для зрелого control center, а не только для go-live.

### 8.1. Synthetic scenarios

- synthetic inbound message flow;
- synthetic operator reply flow на safe test channel;
- synthetic webhook contract checks;
- synthetic attachment retrieval path.

### 8.2. Correlated dashboards

- one-screen summary по platform + external dependencies + active incidents;
- correlation between channel degradation and provider/API failures;
- correlation between backup freshness and operational risk.

### 8.3. Predictive and trend-based alerts

- failure trend growth even before hard outage;
- token exhaustion or quota burn trend;
- rising latency without full failure;
- repeating partial degradation by specific tenant/profile.

## 9. Coverage map по системам

## 9.1. Telegram / VK / MAX

Нужно видеть:

- auth status;
- inbound availability;
- outbound availability;
- provider 4xx/5xx;
- retry pressure;
- last successful message in/out;
- secret/token validity.

Priority:

- `P0`

## 9.2. iiko

Нужно видеть:

- API availability;
- auth validity;
- latency;
- profile-level failures;
- last successful sync/mutation;
- rate-limit or quota failures.

Priority:

- `P0`

## 9.3. Bitrix24

Нужно видеть:

- webhook/API reachability;
- auth validity;
- checklist/action mutation failures;
- last successful execute;
- user-scoped credential health.

Priority:

- `P1`

## 9.4. NetBox

Нужно видеть:

- API reachability;
- per-site degradation;
- attachment/image fetch failures;
- last successful sync;
- selected sites coverage.

Priority:

- `P1`

## 9.5. Notion

Нужно видеть:

- API reachability;
- auth validity;
- last successful import;
- import failure classification.

Priority:

- `P2`

## 9.6. SMTP / notification delivery

Нужно видеть:

- relay availability;
- auth failure;
- queue/deferred failures;
- last successful delivery;
- failed incident route notifications.

Priority:

- `P0`

## 9.7. DNS / TLS / public ingress

Нужно видеть:

- DNS resolution;
- public endpoint reachability;
- TLS chain health;
- expiry timeline;
- webhook callback availability.

Priority:

- `P0`

## 9.8. Backup / restore readiness

Нужно видеть:

- last backup timestamp;
- backup age;
- last restore drill timestamp;
- restore result;
- alert on stale or missing evidence.

Priority:

- `P0`

## 9.9. VPN / bastion / operator access

Нужно видеть:

- remote access path availability;
- auth/login availability;
- operator inability to reach panel as a first-class incident.

Priority:

- `P1`

## 10. Alert routing model

Минимально нужны три канала реакции:

- `Critical`:
  - production contour or customer delivery broken now;
- `High`:
  - degradation already affects support workflow or threatens it today;
- `Warning`:
  - expiry / stale / trend risk without current outage.

Recommended mapping:

- `Critical` -> instant incident route + operator-visible incident;
- `High` -> incident route + dashboard visibility + on-duty review;
- `Warning` -> dashboard + daily review queue + scheduled rotation work.

## 11. Operator surfaces, которые должны появиться

Минимальный target UI:

- overview card by monitoring domain;
- aggregate availability counts;
- detail timeline per node/profile/source;
- owner and last action info;
- link from incident to source diagnostics;
- replay/retry/retest action, если это безопасно.

Важно:

- monitoring pages не должны ограничиваться raw up/down;
- каждый источник должен показывать историю изменений состояния;
- overview должен показывать картину контура, а не только список строк.

## 12. Roadmap внедрения

### Wave 1

- unified channel/provider health for `Telegram` / `VK` / `MAX`;
- backup freshness and restore freshness;
- DNS/TLS/public ingress probes;
- SMTP/alert delivery health;
- token/secret expiry watch.

### Wave 2

- Bitrix24 / NetBox / Notion health;
- per-profile/per-site degradation surfaces;
- VPN/bastion/operator access monitoring;
- quota and rate-limit awareness.

### Wave 3

- synthetic end-to-end probes;
- correlation dashboards;
- predictive trend alerting;
- richer business-facing monitoring overlays.

## 13. Backlog по внедрению

Ниже backlog именно как implementation map, а не как новый task-list репозитория.

### B1. Unified external provider health

- сделать единый monitoring source catalog для `Telegram` / `VK` / `MAX`;
- ввести per-channel inbound/outbound counters;
- ввести classification `auth`, `provider_4xx`, `provider_5xx`, `timeout`, `rate_limit`.

### B2. Backup readiness monitoring

- хранить `last_successful_backup_at`;
- хранить `last_restore_drill_at`;
- добавить alert на stale backup / stale restore evidence;
- вывести backup health в readiness-adjacent surface.

### B3. DNS/TLS/public endpoint monitoring

- добавить DNS record probes;
- добавить HTTPS/public callback probes;
- переиспользовать timeline and overview UX rules;
- завести alert thresholds на expiry и unavailable endpoint.

### B4. SMTP and alert route health

- добавить probe для SMTP relay;
- связать result с incident route delivery health;
- различать "incident created" и "incident notification delivered".

### B5. Secret/token expiry registry

- завести registry expiring credentials;
- добавлять warning horizons;
- выводить rotation-needed surfaces без раскрытия самих секретов.

### B6. Support SaaS health

- Bitrix24 API and execute health;
- NetBox sync and site-level degradation;
- Notion import health;
- iiko profile and quota health.

### B7. Operator access monitoring

- VPN/bastion/IdP probes;
- login path health;
- operator-reported access degradation как отдельный incident class.

## 14. Что считать успехом для 01-197

Задача считается закрытой на уровне planning/architecture, если:

- есть единая карта monitoring coverage;
- понятно, что уже покрыто и что ещё нет;
- есть первая волна обязательных источников;
- есть вторая и третья волна без смешивания приоритетов;
- для каждой группы систем понятны expected signals, alerts и operator actions.

## 15. Следующий practical implementation order

Если идти строго по value/risk, следующий рабочий порядок такой:

1. `backup freshness + restore freshness`
2. `DNS/TLS/public ingress probes`
3. `SMTP / incident notification delivery health`
4. `unified Telegram/VK/MAX provider health`
5. `secret/token expiry watch`
6. `Bitrix24 / NetBox / Notion health`
7. `VPN / operator access path`

Именно этот порядок лучше всего снижает риск production blind spots для support-команды при текущем масштабе `~3000` обращений в день и `~30` одновременных операторов.
