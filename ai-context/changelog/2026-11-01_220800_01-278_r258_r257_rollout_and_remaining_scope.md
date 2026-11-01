# 01-278 R258: выкладка R257 на web/worker и актуальный остаток

Дата: 2026-11-01, Europe/Moscow.
Промпт пользователя:

> давай дальше.
> что остаётся по задаче?

## Выполнено

- Fresh clean7b9fea55abf6568c00891cd69db906025891685a HEAD/origin/main/remote,
  R257,parentda881c6d5/scope11/17 compiled inputs exact.835/15 suites и35 PG —
  прежний source proof, не новый прогон.
- Собран incremental panel JAR88118031 bytes/SHA256
  355549346B76059A3EED22F26B271C1867F2FF26D2C238862A34B4F3659C3015:
  strict ZIP/CRC/independent payload proof,1435 tested classes,795 unrelated
  payloads и3 bot JAR unchanged. Не full web Maven/Sass rebuild.
- Image iguana-panel:account-r257-7b9fea55a-20261101,
  sha256:90cd11900131b1cf3fd5d0904a6d38be931af78455b6b73aabbef6b84926896c,
  OCI7b9fea55a, network-none COPY поверх R255/e02456c19ea4.
- Сохранены rollback-r258-panel-web-20261101-e02456c19ea4 и
  rollback-r258-ops-worker-20261101-e02456c19ea4 (iguana-panel:),
  оба e02456c19ea4/0df04ecf6; прежние rollback pointers сохранены.
- Новый pg_dump custom10120961 bytes/SHA256
  02929BA178E68158225F40CE9FD780F302BADA11BAC0F78D1D06855B69567B5B,
  isolated restore network-none/192MiB подтвердил84/V84/settings455/
  roots53/pending51/ratings36. Same-host, working DB/rollback не восстанавливались.
- Только web/worker по1 replica пересозданы Compose project tg_ref_b24_sup,
  прежние.env/base2files и новый guarded env-placeholder overlay.
  --no-deps/--no-build/--pull never, cold start web → worker.
  ENV62/60/user/mounts exact, HostConfig equivalence fingerprint восстановлен
  bounded Binds order/empty DNS comparator. Старые5 Compose/env inputs unchanged.
  Web4a828963846e/worker35472b2e278f healthy/restart0/readinessUP.
  Все16 нетронутых containers, включая runner783fd187513e и3 child JVM, прежние.
-61 unique live access/SSE/readiness и24 signed child admission checks GREEN;
  реальный ROLE_ADMIN+PAGE_CHANNELS/PAGE_SETTINGS, logout, без изменения grants.
  Full-log startup markers/child queues1 consumer/ready0/unacked0 подтверждены.
- Один штатный CSRF diagnostic POST в сохранённую TG1 group:
  HTTP200/sent1/failed0/provider ok, marker R258-20261101-220314-493a1929.
  R257 fresh persisted snapshot path исполнен на рабочей БД; retry/reroute нет.
  Это не MessagingService/positive feedback/revoke chain или human receipt.
- Исправлены только versioned acceptance operators: recent600 tail не содержал
  старый startup TG1 marker, access report читал старый R256 proof path.
  Все старые reports сохранены; checks повторены read-only, deploy/send не
  повторялись и product guards не ослаблялись.
- V84/settings/accounts/grants/history/root/pending/rating/outbox fingerprints
  unchanged. HealthDOWN по прежнему inbound DLQ1; worker20 TLS/iiko WARN,
  web WARN0, ERROR/fatal0, нового RMS claim-loss нет в окне.
  No migrations/backfill/flags/db-migrate/runner/infra/NetBox/purge/requeue.

## Что осталось

Пять блоков: positive client feedback/revoke/replay; остальные provider/child/
service-chat permissions и GENERAL/questionnaire E2E; manual/global tasks/
Kanban/projects/incidents/system policies; AI privacy/retention/media/LLM/client
delivery/auto-reply; полная one/many/all/GENERAL/save-reload/restart/rollback/fleet
приёмка, operational gates и первый S6. Базовые GENERAL write/SSE/выбор до первого
вопроса, AI card control/MEMORY/knowledge handoff уже есть, не начинать с нуля.
S2B снятR190. Следующий приоритет — контролируемый клиентский feedback-сценарий,
не новая диагностическая отправка в группу. Задача🟡, не закрытие S6.

Изменены только task/S0/BOT_RUNTIME_CONTRACT и этот новый append-only changelog.
Metadata sandbox/replay/exact blob/content/scope guards отдельно от deploy.
Evidence/backup/operators/rollback — .tmp/01-278-r258-rollout/; overlays R256/R253
runner также нужны. Агент staging/commit/push не выполнял.
