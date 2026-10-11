# 01-278: точный ticket ID в telemetry gap breakdown (R19)

Дата и время: 2026-10-11 13:51:41, Europe/Moscow.

Промт пользователя: `дальше`

## Фактические изменения

- Сохранена предыдущая граница небольших пакетов: один production service
  и его существующий test file, плюс task details и новый append-only changelog.
- В `DialogWorkspaceTelemetryDataService.aggregateWorkspaceGapReasons`
  текстовый ticket_id извлекается без String.valueOf и без нормализации.
  SQL NULL, отсутствующее поле и нетекстовый payload не порождают искусственный
  идентификатор. В distinct set сохраняется точный непустой текст без trim.
- `T-EXACT`, ` T-EXACT`, `T-EXACT ` и ` T-EXACT ` различаются; повтор
  точного ключа дедуплицируется. Реальные строки `null` и ` null ` считаются
  разными непрозрачными ID, а SQL NULL, пустая строка и whitespace-only — нет.
- Сохранены число событий, experiment filter, окно [start, end), семь
  gap buckets, нормализация/дедупликация причин и last_seen_at.
  Это raw historical/global audit breakdown: canonical root lookup и
  business filtering не добавлялись; tickets не становятся числом
  авторизованных canonical обращений. События без ID по-прежнему считаются.

## Проверки

- Свежий `HEAD=origin/main=69f00855212e1fcb34393df8fe42351c4d0df2ad`;
  parent `823178c71e29b522e67a96c05cf370bf58a098ba`. R18 проверен по SHA,
  parent, message и фактическим четырём изменённым файлам. Root checkout
  исходно чистый; exact baseline, origin/main и чистота повторно проверяются
  после fetch непосредственно перед переносом validated patches.
- Repository Maven wrapper в detached temporary worktree: `29/29` tests
  в 5 классах, failures/errors/skipped = 0. Data service: `17/17`, summary
  assembler: `1/1`, summary service: `2/2`, analytics: `5/5`, telemetry WebMvc: `4/4`.
- Добавлены 15 случаев в existing data-service test: семь PostgreSQL/JDBC
  gap buckets проверяют exact/padded ID, повтор, настоящий SQL NULL, blank ID,
  experiment exclusion, обе границы окна, events и last_seen_at. Ещё восемь
  случаев покрывают NULL/blank/missing/non-text payload и буквальные null ID.
  Используются только временные PostgreSQL Testcontainers schemas.
- `git diff --check` и exact changed-file set выполняются до переноса.
  Применяются те же deterministic patches; после переноса выполняется
  LF/CRLF-normalized сверка каждого project file с sandbox и повторный
  guard baseline/changed-file set. Staging не выполняется.
- Полный `PanelApplicationTests` в R19 не запускался: constructor/wiring
  и schema не менялись. Java compile, telemetry WebMvc context и targeted
  regression tests GREEN.

## Границы и следующий шаг

- Source-only checkpoint S2A, не завершение 01-278; статус 🟡.
- `DialogWorkspaceRolloutParityService.buildParityExitCriteria` отдельно
  нормализует ticket_id в JDBC mapper до общей агрегации. Этот caller
  намеренно не изменён в небольшом R19; parity exit criteria требуют
  следующего exact-ID review и собственных targeted tests на свежем main.
- API contracts, права, global telemetry scope и business assignment
  не менялись; owner не определяется из user/channel/display label.
- R12 provenance остаётся `PROPOSED_NOT_APPROVED`; S2B approvals, aliases,
  role matrix и shared-channel decisions остаются PENDING.
- Рабочая/production БД, migration/backfill, feature flags, deployment,
  staging, commit и push агентом не выполнялись.

## Изменённые файлы

- `spring-panel/src/main/java/com/example/panel/service/DialogWorkspaceTelemetryDataService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogWorkspaceTelemetryDataServiceTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-11_135141_01-278_telemetry_exact_ticket_counts.md`

