# 01-278: точный ticket ID в parity exit criteria (R20)

Дата и время: 2026-10-11 14:07:45, Europe/Moscow.

Промт пользователя: `дальше`

## Фактические изменения

- Сохранена граница небольших пакетов: один production service, один новый
  test file, task details и отдельный append-only changelog.
- JDBC mapper `DialogWorkspaceRolloutParityService` передаёт ticket_id
  непосредственно из ResultSet в существующую общую gap-агрегацию.
  Четыре plain/padded варианта ID и буквальные `null`/` null ` сохраняют
  разные identity; повтор точного ключа дедуплицируется. SQL NULL и blank ID
  не увеличивают tickets, но соответствующие telemetry events не удаляются.
- В этом же запросе nullable experiment predicate получает явный TEXT cast.
  Это устраняет воспроизведённую PostgreSQL ошибку определения типа `$3`
  при отсутствии experiment и при `stringtype=unspecified`, не меняя
  семантику optional filter либо правило trim для имени experiment.
- Readiness/error policy, normalization/filter critical reasons, window
  predicate, events, recordedAt/last_seen_at секции, summary и disabled
  short-circuit сохранены. Ошибка чтения telemetry по-прежнему возвращает hold.
  Canonical root lookup и business authorization в global audit не добавлены.

## Проверки

- После повторного fetch подтверждён свежий baseline
  `HEAD=origin/main=879becdac90d839c53344fdeba15ffed34053f26`;
  parent `69f00855212e1fcb34393df8fe42351c4d0df2ad`. R19 проверен по SHA,
  parent, message и фактическим четырём изменённым файлам. Root исходно чистый.
- Pinned baseline blobs: parity service
  `f651cc4ebb9f82ae4f742bae7c033b6183355542`, task details
  `c2ce113610cd6acade1de80d862799ffee96e5a5`; source anchors уникальны.
- Сначала в detached worktree добавлены 14 tests и выполнен baseline run:
  14 tests, 6 assertion failures, errors/skipped = 0. Воспроизведены
  объединение distinct keys (2 вместо 6), потеря literal null ID и отказ
  optional experiment SQL. После двухстрочного production fix tests не
  изменялись, assertions и JDBC configuration не ослаблялись.
- Repository Maven wrapper: `41/41` tests в 7 классах GREEN,
  failures/errors/skipped = 0. Parity: `14/14`; data service: `17/17`;
  governance: `1/1`; governance config: `2/2`; summary assembler: `1/1`;
  summary service: `2/2`; telemetry WebMvc: `4/4`.
- Новые tests: default/unspecified JDBC string types, padded/plain/literal-null
  ID, SQL NULL/blank ID с сохранением events, optional experiment, критичные
  причины, other event/experiment, old/future window exclusion, section
  last_seen_at, existing empty-window policy, disabled no-query и missing
  audit table fail-closed. Только временные PostgreSQL Testcontainers schemas.
- Constructor/wiring и schema не менялись; полный `PanelApplicationTests`
  в R20 не запускался. Java compile и telemetry WebMvc context GREEN.
- Exact baseline, чистота root и remote main повторно проверяются после
  fetch перед переносом. Переносятся те же deterministic patches только
  после GREEN; exact changed-file set и git diff --check выполняются до/после.
  LF/CRLF-normalized content сверяется с sandbox для каждого файла.

## Границы и следующий шаг

- Это source-only checkpoint S2A; 01-278 остаётся 🟡, не завершена.
- Формат created_at mapper не менялся. Вложенный top_reasons last_seen_at
  требует отдельного небольшого review: mapper передаёт строку, accumulator
  ожидает Instant. R20 подтверждает только last_seen_at/recordedAt секции.
- Global telemetry scope, API contracts, business owner и права не менялись;
  user/channel/display label не становятся authority.
- R12 provenance остаётся `PROPOSED_NOT_APPROVED`; S2B approvals, aliases,
  role matrix и shared-channel decisions остаются PENDING.
- Рабочая/production БД, migration/backfill, feature flags, deployment,
  staging, commit и push агентом не выполнялись.

## Изменённые файлы

- `spring-panel/src/main/java/com/example/panel/service/DialogWorkspaceRolloutParityService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogWorkspaceRolloutParityServiceTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-11_140745_01-278_parity_exact_ticket_counts.md`

