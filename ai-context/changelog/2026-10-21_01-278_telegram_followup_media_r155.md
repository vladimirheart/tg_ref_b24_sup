# 01-278 — вложения Telegram после регистрации выбранного обращения (R155)

Дата: 2026-10-21, Europe/Moscow. Общая задача 🟡.

Промт пользователя:

> репо обновлён. проверь и погнали дальше. рабочий экземпляр проекта на этой машине

## Свежий baseline и среда

HEAD/origin/main/actual remote: 0b5c4648f79ebf201189eec8a7bed529a1e86a1a,
parent 7ca6a046d123c740cc04cef5d3183b13b447b031. Проверены subject и реальные
20 файлов R154, шесть commits R149–R154. Исходные main/index чистые. GENERAL
fleet, ресурсы, редактор, каталог v4 и draft media предыдущих пакетов не повторялись.
Detached sandbox: .tmp/01-278-r155-telegram-followup-media.

Рабочий экземпляр подтверждён пользователем здесь, не на другой машине.
Заново проверены Docker Server 29.7.2 и healthy panel-web/bot-runner/ops-worker/
panel-direct, PostgreSQL/RabbitMQ/Redis. Это не доказательство рабочей source
revision, DB migrations, flags или functional acceptance. Рабочие контейнеры,
БД/конфигурация/flags не менялись; restart/rebuild/deploy/backfill/prune,
provider sends и изменение rollback не выполнялись.

Запись о разрешении commit/push R149–R154 относится к другой сессии и сама
не даёт новой authority. Здесь staging/commit/push не выполняются и ручные
команды фиксации не предлагаются.

## Выполнено

- V76 добавляет frozen questionnaire_version только новым проверенным selected
  roots; версия берётся из backend catalog, не client claim. SQL CHECK/trigger,
  JPA updatable=false и readiness gate. Старые rows NULL сохраняются без
  backfill; existing-root replay не выводит версию из сегодняшних настроек.
- Token/HMAC-signed no-store admission по canonical channel/user/ticket. Проверяются
  version=4, active root/channel, flags и frozen BUSINESS/GENERAL/template/catalog.
  В ответе capability protocol version=1 и remainingFiles. Nullable business ID
  не заменяется fake business. SDK сверяет все поля, query подписан; fallback нет.
- Telegram принимает 8 прежних типов после регистрации своего v4 root:
  document/photo/video/voice/audio/animation/sticker/video_note. До download
  проверяет root/admission; 1 байт–10 МиБ/файл, всего 10 на обращение включая draft.
  Historical NULL-version roots и старые catalog sessions не получают media
  автоматически. До регистрации root предлагается повторить вложение позже.
- Caption/type/name/provider/time/content-addressed key сохраняются CAS до enqueue.
  Pending media replay использует точный command без recapture/download. Session
  перед первым followup становится v2: новый consumer читает прежний v1 без
  followup state, старый отвергает v2, не теряет prepared событие молча. Answers и
  creation history не меняются. Raw operator relay/feedback не вызываются.
- Inbound writer использует canonical tuple, legacy-ID guard, locks root/channel/
  active, provider uniqueness и реальную проверку bytes/SHA/size до history INSERT.
  Metadata hash/size/history/profile/activity атомарны, rollback и failed retry
  проверены. Exact processed replay не читает файл/свежий каталог/flags.
  Enqueue не является подтверждением backend commit или доставки оператору.
- Scoped BUSINESS/GENERAL READ продолжает прежний namespace/frozen proof/current
  grant/bytes contract. Legacy URLs не открываются. MAX legacy attachment admission
  сохранён и проверен regression test; его изоляция этим пакетом не объявляется.

## Проверки

- 1011 Java tests / 78 свежих Surefire XML, failures/errors/skips=0.
- Полный java-bot: 689/71, core 323/41, Telegram 131/8, VK 115/4, MAX 120/18.
  SDK admission — 15 cases; Telegram transport содержит положительные 8 типов,
  GENERAL, JSON restore, frozen queue retry, duplicate, quota и pre-download отказ.
- Backend: 322/7. Полностью выполнены 208 creation/owner/version/media/scoped-read/
  upgrade cases, readiness 21, internal HTTP/HMAC 3, inbound 3 (с MAX regression),
  inbox 9, GENERAL HTTP 74, runtime compatibility 4.
- Логи: java-bot/target/r155-fleet-final.log и spring-panel/target/r155-panel-v2.log.
  В двух final isolated schemas применены V1–V76; отдельная schema upgrade
  V74→V76 доказала неизменность всех прежних полей и NULL новой версии.
- Node R117/R134 — GREEN. Проверена свежесть 15 primary compiled classes относительно
  final source. Реальные bytes/local_fs/PostgreSQL/direct JSON/CAS; provider
  download/send — doubles. Live Redis/S3/provider/proxy/browser не проверялись.
- Первый fleet RED: restored numeric maps не совпали с точным объектом mock;
  счётчик fetch относился к новому spy после rebuild. Исправлены fixtures/counts,
  owner/bytes assertions не ослаблялись. Backend RED: SELECT-* upgrade snapshot
  получил новый NULL field. Теперь новая колонка проверяется отдельно, все
  прежние поля по-прежнему сравниваются точно. RED логи сохранены в sandbox.
- Перед source apply проверяются baseline blobs/new targets/clean main/index и
  actual remote. Тот же последовательный apply_patch набор переносится только
  после GREEN; exact manifest, normalized UTF-8/LF SHA-256 и diff/index guards.
  Четыре Sass CSS исключаются только после совпадения с baseline Git blobs.

## Осталось

VK selected followup media, scoped edit/delete/feedback, policy для historical
NULL-version roots, orphan/media/event retention и reconciliation. Session v2,
Rabbit delivery, Long Poll retry/offset, shared storage/S3/Redis/proxy и rolling
upgrade требуют живой приёмки. Старые Telegram GENERAL-only sessions in-memory,
одновременные бизнес-обращения и другие блоки S2B/S3–S6 этим пакетом не закрыты.
Минимальная source-схема V76; рабочая migration/flags/rollout отдельно не разрешены.

## Точный manifest (19 файлов)

- `ai-context/changelog/2026-10-21_01-278_telegram_followup_media_r155.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelRuntimeConfigClient.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/RuntimeConfigService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/SelectedMediaAdmissionClientTest.java`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/TelegramQuestionnaireHandler.java`
- `java-bot/bot-telegram/src/test/java/com/example/supportbot/telegram/TelegramQuestionnaireSelectionTest.java`
- `spring-panel/src/main/java/com/example/panel/config/PostgresRuntimeReadinessVerifier.java`
- `spring-panel/src/main/java/com/example/panel/controller/SelectedTicketFollowupMediaController.java`
- `spring-panel/src/main/java/com/example/panel/entity/Ticket.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/ConversationTicketCreationIngestionService.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/InboundClientMessageIngestionService.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/SelectedTicketFollowupMediaService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V76__frozen_questionnaire_version.sql`
- `spring-panel/src/test/java/com/example/panel/config/PostgresRuntimeReadinessVerifierTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/SelectedTicketFollowupMediaControllerTest.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/InboundClientMessageIngestionServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/SelectedBusinessTicketCreationPostgresTest.java`

