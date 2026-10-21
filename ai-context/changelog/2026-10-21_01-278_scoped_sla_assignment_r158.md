# 01-278 — scoped SLA-назначение и атомарный аудит (R158)

Дата: 2026-10-21, 22:55:15 Europe/Moscow.

Промт пользователя:

> продолжай

## Выполнено и проверено

- Baseline HEAD/origin/main/actual remote main:
  c99db15c48eb0090116f2e521d4c557aadb6a643, parent
  2793c5fe70007e834110a7bcddc9881d9ae699a0. Проверены subject и реальные
  13 файлов R157; main/index исходно чистые. Detached sandbox
  .tmp/01-278-r158-scoped-sla-assignment; R157 не повторялся.
- Фоновый SLA читает frozen user/raw/public/business/kind/channel, revision и
  точный stored responsible тем же SQL, что и routing attributes. Нет позднего
  owner lookup по raw ID. Ambiguous root и message user/channel mismatch
  исключаются; daily request number/enrichment и старые read APIs сохранены.
- BUSINESS candidate pool использует current OPERATE R157 до всех стратегий,
  skills/queues/max-open. Least-loaded считает только свой business, RR разделён
  по business/channel/route/pool и использует полный long. Legacy business label
  остаётся условием правила, не источником authority. Нет global workload fallback.
- Scoped writer в отдельной транзакции повторяет exact owner/generation/channel,
  current OPERATE, active/open/non-GENERAL/unique root и CAS по exact responsible
  плюс revision; legacy-ID advisory guard и root/channel locks. Конкурирующие
  обработчики дают одну запись. Отказ не запускает raw writer или подбор следующего.
- Source migration V77: metadata responsibility_revision=0 для старых roots,
  trigger на assignment insert/change/delete, защита revision от reset и
  append-only scoped_sla_assignment_events с frozen provenance. A→B→A и
  delete/insert отклоняют старое решение, last_read_at отдельно не меняет revision.
  Назначение/revision/provenance commit вместе; audit failure откатывает всё.
  Старые поля/назначения не переписываются, owner/backfill не выполняется.
- Notifier использует только typed source и scoped writer; raw success audit
  удалён, success count растёт только после committed write. GENERAL исключён;
  typed UNRESOLVED совместим только до strict после exact proof. Frozen context
  исключён из сериализации decision. Старые pure routing preview APIs read-only.
- Readiness требует новую колонку и scoped audit V77; рабочая migration не запускалась.
- Validation: 698 Java tests / 15 свежих Surefire XML, failures/errors/skips=0,
  плюс 2 Node checks R117/R134. Новые SLA SQL cases 26 и routing/background cases 19;
  включены полный SelectedBusinessTicketCreationPostgresTest (238), GENERAL HTTP,
  R157 candidate/routing, R125 final assignment, frozen outbox и notification scopes,
  прежние SLA/readiness/read tests. Проверены V1→V77, V76→V77 и V74→latest:
  каждый старый root/assignment field сохранён, новые metadata значения проверены.
- Финальный log: spring-panel/target/r158-regression-final.log. Свежесть XML и
  compiled classes относительно изменённых sources проверена. RED writer-v1:
  фикстуры пытались изменить immutable public ID и сослаться на несуществующий
  channel; заменены реальным delete/recreate и действующим чужим каналом.
  RED regression-v1: у нового skills/queues правила отсутствовал match predicate,
  поэтому оно правильно игнорировалось. Исправлена фикстура; негативный single
  case также получил valid match. Assertions не ослаблялись, RED logs сохранены.
  Java-bot не менялся, fleet/provider/browser/live acceptance не повторялись.
- Source apply: тот же apply_patch набор после GREEN sandbox; pinned Git blobs/
  absent new targets, clean main/index/remote, точный manifest 14 файлов,
  normalized UTF-8/LF SHA-256 и postapply guards. Четыре Sass CSS вне manifest
  только после равенства baseline Git blobs. Существующие migrations не менялись.
- Рабочий экземпляр на этой машине, Docker Server 29.7.2 проверен для isolated
  Testcontainers. Health рабочего stack — свидетельство R155, не новая проверка
  R158; рабочие revision/DB/flags/functional acceptance не подтверждаются.
  Нет restart/rebuild/deploy/рабочих migrations/backfill/config/flags/provider
  sends/prune/staging/commit/push или ручных команд фиксации.

## Осталось

- Остальные escalation/manual/raw assignment writers, producer context до INSERT,
  полная historical business/generation attribution child rows. Legacy writers
  ещё не имеют общего scoped API/порядка locks; их конфликт может дать rollback.
- Политика внешних SLA webhook destinations/recipient READ и остальных служебных
  чатов не закрыта этим пакетом. Scoped owner — не право внешнего получателя.
- Scoped UI/read API и retention нового audit; revision CAS не заменяет их.
  Current grant проверяется на момент SQL записи, не lock на весь workflow;
  max-open остаётся snapshot policy, не резервированием нагрузки.
- GENERAL automation, scoped edit/feedback, AI privacy/media/LLM/delivery,
  global/system/task policy, owner-approved S2B и прочие S3–S6 gates.
- Отдельные migration/fleet/rolling-upgrade/live Redis/provider/proxy и rollback
  acceptance по approval. Общая 01-278 остаётся 🟡, rollout не выполнен.

## Точный manifest (14 файлов)

- `ai-context/changelog/2026-10-21_01-278_scoped_sla_assignment_r158.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `spring-panel/src/main/java/com/example/panel/config/PostgresRuntimeReadinessVerifier.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogLookupReadService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogResponsibilityService.java`
- `spring-panel/src/main/java/com/example/panel/service/ScopedSlaAssignmentService.java`
- `spring-panel/src/main/java/com/example/panel/service/SlaEscalationAutoAssignService.java`
- `spring-panel/src/main/java/com/example/panel/service/SlaEscalationWebhookNotifier.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V77__scoped_sla_assignment_revision.sql`
- `spring-panel/src/test/java/com/example/panel/config/PostgresRuntimeReadinessVerifierTest.java`
- `spring-panel/src/test/java/com/example/panel/service/ScopedSlaAssignmentPostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/ScopedSlaRoutingServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/SelectedBusinessTicketCreationPostgresTest.java`

