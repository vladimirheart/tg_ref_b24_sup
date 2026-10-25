# Ошибки отдельных записей — диагностика, не инцидент: R17

25.10.2026, задача01-281, 🟣. Выполнено и развёрнуто; commit/push не выполнялись.

## Результат

Ошибки отдельных входящих/исходящих записей больше не создают автоматический
транспортный инцидент и не повышают его приоритет. Это не снижение с critical
на high/warning: сами failed-счётчики полностью исключены из условий инцидента.

- Текущий snapshot для мониторинга отделён от полного диагностического snapshot.
- История для sustained pressure пересчитывается по системным счётчикам:
  старые сохранённые severity/summary не могут вернуть критичность из-за failed.
  Сохранённые строки истории при этом не переписываются.
- Старый сигнал, вызванный только такими ошибками, штатно переводится в resolved,
  когда нет других условий. Причина снятия честно говорит об изменении условий,
  а не об исправлении SQL или успешной повторной доставке.
- Из карточки убран per-message список с UUID, SQL-текстом и датами ошибок.
  Полная существующая диагностика транспорта, журналы, исходные записи и
  явные recovery controls сохранены. Replay/requeue не запускались.
- Системная очередь, зависшая обработка, checkpoints и ручной приоритет
  действительных инцидентов продолжают работать по прежним правилам.

Это отменяет часть UI-требований R14–R16 о показе failed records прямо в карточке.
Исторические инциденты/журнал и редактируемые авторские значения не очищались.

## Важная граница текущего экземпляра

INC-221 и INC-236 остаются investigating/critical, но не из-за четырёх записей:
штатная проверка25.10.2026 в14:19:46UTC уже получила inbound_failed=0 и
outbound_failed=0 в incident projection. Ненулевыми остались stale_checkpoints=3
и lagging_checkpoints=1. В новом bridge payload failed-полей вообще нет.

Диагностический snapshot по-прежнему видит реальные failed records.
Критерий stale progress cursor и старую запись feedbacks здесь не исправляли:
возраст позиции без новых данных по-прежнему не доказывает простой процесса.
Не утверждается, что все транспортные инциденты исчезли или SQL-ошибка исправлена.

## Проверки и применение

- Fresh HEAD=origin/main=remote:
  91b49ede159da36f75009b70a9e8c7f18160d579; main был clean.
  Detached sandbox: C:/Users/SinicinVV/git_h/iguana-r17-sandbox.
- 255 Java tests /56 suites, failures/errors/skips=0. Реальные isolated PostgreSQL
  тесты проверяют legacy critical history, сброс sustained streak, сохранность
  failed/SQL diagnostics и транзакционное снятие старых incident signals.
  Пять наборов failed counts (включая10000) не создают инциденты;
  прежние systemic queue/stale cases и ручной приоритет покрыты regression.
- 42 JS cases и42 прежних exact-ticket-ID checks GREEN.
  Повторный JS/exact-ID запуск после применения в main также GREEN.
- 140 browser theme/viewport/mode combinations, включая130 R13 incident states;
  140PNG. Light/dark ×320/390/768/1440/1920, legacy error-only bridge/sustained,
  отсутствие UUID/SQL в карточке, сохранность всех четырёх записей в отдельной
  диагностике, drafts, history, denied analytics, stale refresh, focus,
  XSS escaping, priority save/error, modal closure и отсутствие лишних writes.
- Audit74templates/47pages GREEN. Новые CSS/inline themes не добавлялись.
  Четыре Sass artifacts в sandbox отличались только EOL — normalized content
  проверен, в main они не переносились.
- 17 source/test/tool/rule paths guarded-applied после GREEN, content SHA сверены.
  Metadata closeout добавил4paths, итог21. Staged/committed/pushed=false.
- В pre-apply wrapper сначала разобран harmless Git EOL stderr, затем один
  network timeout GitHub; запись в main до GREEN не выполнялась.
  Повторный authoritative ls-remote подтвердил тот же baseline.
  Ранняя post-deploy проба поймала health=starting; guard не отключался,
  повторный acceptance выполнен только после завершения compose wait: GREEN.

Evidence: ignored spring-panel/target/incidents-r17-evidence/
(Java XML/logs, MVC captures, browser reports и140PNG), а также
spring-panel/target/incidents-r17-image/ (package validation, before/after,
deploy log и exact-ID/main JS logs).

## Runtime checkpoint

Проверена текущая Windows/Docker Desktop сессия этого checkout:

- Web и worker: iguana-panel:incidents-r17-20261025,
  image sha256:15bfdcd63bd1347840fe0741e1dc64c5a5b269a9006bbac843a4815b789b40d0,
  оба healthy.
- Web container a1e4114efa65d14b98725cb9cbd465c2959c4b0da54cefd8d649a52a4d5af3d6,
  start2026-10-25T14:19:57.422097488Z.
- Worker container0043af968d032cb94655762d1bce8be5a6795f242d2ec433727df502d8a85a3a,
  start2026-10-25T14:19:10.131192104Z.
- JAR SHA2560f6e8f81c0679a0da49afdf7e7f9357466da6ebf9b86b140c268bdd6dd2e666d.
  Полный Maven package:2176entries,1385Java17-compatible application classes.
  По сравнению с работающим R16 изменены только две backend class families
  (9class entries) и3UI resources. Все libraries/остальные hashes прежние.
  Image собран offline поверх pinned R16, без pull внешних base images.
- Rollback-incidents-r17-web-20261025 → image12a1de538518a88edfe9f3c98317ee7254b2ec129cc0b39d8f10590aabedb5ae (R16).
  Rollback-incidents-r17-worker-20261025 → image75b3345638d47932a2145d9e8b5b6a363f97c15a08d106f4426a036815a9144f (R15).
  Оба точных rollback tags проверены; откат не выполнялся, прежние tags сохранены.
- Полные env до/после совпали отдельно для каждой роли;4web/3worker bind mounts
  те же. HTTP resources побайтно равны packaged resources; anonymous protected
  routes=302. Остальные13containers сохранили id/image/start,4bot JVM —
  PID/start ticks.
- Четыре указанные failed записи сохранили status/last_error/updated_at:
  fingerprint fc49912aad5b460a670b0c9233b9d84de0102bac1e71ac5625586bdfe557c88b
  до/после одинаков. Ничего не удалялось и не переигрывалось.
- FlywayV81 до/после; startup logs обеих ролей явно подтверждают skip migration.
  Policy SHA97d777785d26636b73a3ba798ada6e7976133416fa0da6156db154c2f95aa1d0
  прежний. DB migrations/backfill, feature flags, monitoring config, bots,
  ручные incident mutations, replay/requeue и external business writes
  не выполнялись. Обычные jobs продолжают работу и штатно обновляют свои данные.

Приёмка владельца: Ctrl+F5, открыть карточку транспортного инцидента —
там нет списка четырёх SQL-ошибок; при необходимости записи остаются в
«Восстановление интеграций». Authenticated рабочие PATCH агентом не выполнялись.
Общий статус01-281 остаётся 🟣 до приёмки.

## Точные21 файлов и команды владельцу

Запускать из корня после просмотра diff. Этот блок агентом не запускался.

```powershell
$r17Files = @(
    'ai-context/changelog/2026-10-25_172215_incident-diagnostic-noise-r17.md',
    'ai-context/rules/05-monitoring-policy-and-employee-pickers.md',
    'ai-context/rules/06-incident-readable-descriptions.md',
    'ai-context/tasks/task-details/01-281.md',
    'docs/ui/incident-diagnostic-noise-r17.md',
    'spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitor.java',
    'spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportOpsService.java',
    'spring-panel/src/main/resources/static/js/incident-descriptions.js',
    'spring-panel/src/main/resources/static/js/incidents-workbench.js',
    'spring-panel/src/main/resources/templates/incidents/index.html',
    'spring-panel/src/test/java/com/example/panel/service/TransportIncidentNoisePostgresqlTest.java',
    'spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitorTest.java',
    'spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportOpsServiceTest.java',
    'spring-panel/src/test/js/fixtures/incident-descriptions-r13.cjs',
    'spring-panel/src/test/js/incident-concrete-problems-r14.test.js',
    'spring-panel/src/test/js/incident-descriptions-r13.test.js',
    'spring-panel/src/test/js/incident-noise-policy-r17.test.js',
    'spring-panel/src/test/js/incident-worker-details-r16.test.js',
    'tools/incident-concrete-problems-qa.cjs',
    'tools/incident-noise-policy-qa.cjs',
    'tools/incident-worker-details-qa.cjs'
)

git status --short
$alreadyStaged = @(git diff --cached --name-only)
if ($alreadyStaged.Count -ne 0) { throw 'Сначала разберите существующий staging.' }

git add -- $r17Files
if ($LASTEXITCODE -ne 0) { throw 'git add failed' }

git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Staged diff check failed' }
$r17Staged = @(git diff --cached --name-only)
git diff --cached --name-only
if ($r17Staged.Count -ne $r17Files.Count -or @(Compare-Object $r17Files $r17Staged).Count -ne 0) { throw 'Staging manifest mismatch' }

git commit -m "fix(incidents): исключить ошибки отдельных записей из инцидентов #ai"
if ($LASTEXITCODE -ne 0) { throw 'Commit failed' }

git push
```
