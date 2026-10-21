# 01-278 — бизнес-пул назначения и отделение уведомлений (R157)

Дата: 2026-10-21, 20:44:58 Europe/Moscow.

Промт пользователя:

> продолжай

## Выполнено и проверено

- Baseline HEAD/origin/main/actual remote main:
  2793c5fe70007e834110a7bcddc9881d9ae699a0, parent
  3df88d3f786bf49abab8289c41acb6fb6aa5ff78. Проверены subject и 23 файла R156;
  main/index исходно чистые. Detached sandbox .tmp/01-278-r157-scoped-assignment-pool.
- Scoped ticket_created передаёт frozen scope в candidate pool до стратегии.
  Один SQL проверяет canonical user/raw/public/business/kind, unique raw ID,
  non-GENERAL, canonical active channel. BUSINESS требует foundation flag.
  Current enabled/unblocked unique account с business OPERATOR/MANAGER/ADMIN
  membership или ALL_BUSINESSES допускается по внутреннему OPERATE contract R125.
  VIEWER, чужой business, ROLE_ADMIN без OPERATE и ai_agent не допускаются.
- Least-loaded считает только open assigned roots того же current business,
  исключая GENERAL/UNRESOLVED/foreign/duplicate raw IDs и closed/resolved;
  NULL status сохраняет прежнюю open семантику. Это не historical child attribution.
  Round-robin counter разделён по business/channel/route/pool; shared long cursor
  не обрезается до int. Hash и department modes работают с отфильтрованным пулом.
- Scoped metadata directory читает canonical migrated users/roles только для
  допущенных usernames; пустой набор не означает all. Нет legacy users.role/
  cross-schema optional-column metadata fallback. Global UI catalog не менялся.
- AlertQueue использует resolveRecipients: прежние READ recipients без вычисления
  assignee, global workload и assignment counter side effects. Notification READ
  не заменён OPERATE. Legacy direct assignment остаётся на прежнем API; typed
  UNRESOLVED допускает legacy стратегию только после exact proof до strict.
  GENERAL, invalid/missing owner/schema и пустой BUSINESS pool не дают raw fallback.
- Финальный conditional INSERT R125 сохраняет current owner/OPERATE proof;
  pool snapshot не является lock или authority. Отзыв прав, смена owner и duplicate
  identity между capture/write отвергаются. Existing responsible не переназначается;
  late failure не запускает автоматический подбор следующего.
- Validation: 474 Java tests / 14 свежих Surefire XML, failures/errors/skips=0,
  плюс 2 Node checks R117/R134. Новые candidate cases 43 и routing/callback/
  notification cases 23; прежние scoped assignment 108, frozen outbox delivery 77,
  participant snapshot 73, GENERAL HTTP 74, scope READ/SSE/Redis doubles 36,
  notification scopes 8, GENERAL boundary 9, alert queue 5 и прочие unit cases.
  Проверен capture→canonical directory→routing→conditional INSERT на полной V1–V76.
- Финальный log: spring-panel/target/r157-panel-final.log. V1 RED: неполный users/
  roles fixture; V2 RED: общий metadata inspector увидел legacy users.role в другой
  schema и сломал canonical scoped directory. Исправлен новый scoped SQL, старые
  assertions не ослаблялись; устаревшие mock entry points заменены resolveScoped/
  resolveRecipients с сохранением owner/race/READ assertions. RED logs сохранены.
  Java-bot source не менялся, fleet tests в R157 не повторялись.
- Source apply: тот же apply_patch набор после GREEN sandbox; pinned blobs/new
  targets, clean main/index/remote, exact manifest 13 файлов, normalized UTF-8/LF
  hashes и postapply guards. Четыре Sass CSS — вне manifest только после равенства
  baseline Git blobs. Новая migration не добавлялась, source minimum V76.
- Рабочий экземпляр на этой машине; заново проверен Docker Server 29.7.2 только
  для isolated Testcontainers. Рабочие revision/DB/flags/live acceptance не
  подтверждаются этим пакетом. Нет restart/rebuild/deploy/рабочих migrations/
  backfill/config/flags/provider sends/prune/staging/commit/push и ручных команд.
- Остались SLA/escalation/manual/raw assignment writers, полная child business/
  generation attribution, producer context до INSERT и права внешних recipients;
  остальные S2B/S3–S6 gates, scoped edit/feedback, privacy и live Redis/fleet/
  provider acceptance. Этот пакет не закрывает всю изоляцию или rollout.

## Точный manifest (13 файлов)

- `spring-panel/src/main/java/com/example/panel/service/ScopedAssignmentCandidateService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogParticipantService.java`
- `spring-panel/src/main/java/com/example/panel/service/ChannelAssignmentRoutingService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogRealtimeEventService.java`
- `spring-panel/src/test/java/com/example/panel/service/ScopedAssignmentCandidatePostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/ScopedAssignmentRoutingServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/UiEventOutboxFrozenDeliveryPostgresTest.java`
- `spring-panel/src/main/java/com/example/panel/service/AlertQueueService.java`
- `spring-panel/src/test/java/com/example/panel/service/AlertQueueServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/GeneralNotificationBoundaryPostgresIntegrationTest.java`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-21_01-278_scoped_assignment_pool_r157.md`
