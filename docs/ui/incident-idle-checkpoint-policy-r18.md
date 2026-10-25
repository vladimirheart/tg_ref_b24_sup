# Отсутствие новых данных не является инцидентом — R18

25.10.2026, задача01-281, 🟣. Выполнено и развёрнуто; commit/push не выполнялись.

## Изменённое поведение

Исправлено условие мониторинга, а не только текст или приоритет:

- Старый progress cursor при lag=0 — нормальное отсутствие новых данных.
  Неизвестный источник/позиция не становится подтверждённым сбоем.
- operator-notification-watch.feedbacks не используется действующим watcher.
  Её сохранённая позиция остаётся в диагностике/истории, без проверок источника,
  новых observations и автоматических инцидентов. Cursor не сбрасывался.
- Для действующих UI/chat-history задач нужны новые данные перед неизменной
  позицией в непрерывной последовательности наблюдений дольше допуска:
  10мин для UI,15мин для chat-history. Одной старой позиции недостаточно.
  Продвижение/нулевой или неизвестный lag/разрыв более2snapshot intervals+1мин
  разрывают подтверждение. Поэтому свежие сообщения после долгого idle не дают
  немедленную ложную тревогу. Проверки используют UTC instant из JDBC Timestamp.
- Подтверждённая задержка отдельной задачи автоматически получает high,
  а не critical по возрасту cursor. Ручной priority R15 сохраняется.
  Числовой ID gap не трактуется как количество пропущенных сообщений.
- Queue bridge и sustained pressure отделены от checkpoints. Старые checkpoint
  counters/severity в aggregate history не могут вернуть critical.
  Дублирующий panel-runtime-checkpoints выведен из эксплуатации.
  Системные очереди и зависшая обработка по-прежнему проверяются.
- Отсутствующая текущая строка известной задачи также не оставляет старый
  ложный сигнал активным. Причины снятия не утверждают успешную доставку,
  исправление SQL или доказанное восстановление обработчика.

## Настройки

Инциденты → Мониторинг:

- «Транспорт и фоновые задачи — общий выключатель»;
- «Очередь транспорта интеграций»;
- «Обновление открытых страниц панели»;
- «Обработка новых сообщений для операторов».

Три дочерние проверки независимы; общий выключатель имеет приоритет.
Для каждой доступны включение, пауза на час/неделю/до даты и бессрочное отключение.
Пауза останавливает source/history checks и новые worker observations,
создание/эскалацию/повторные уведомления. Отложенный outbox не теряет attempts.
Рабочие watchers, боты и доставка сообщений продолжаются.

Права прежние: ROLE_ADMIN + PAGE_SETTINGS + CSRF. Настройки общие для web/worker,
атомарные, сохраняются между процессами, пауза истекает без перезаписи файла.
При сохранении родителя UI обновляет зависимые статусы, но не теряет черновики
выбора в соседних строках. Ошибка PATCH не изображается успешным сохранением.

Сам policy-файл при deploy не изменялся. Новые ключи отсутствуют в старом файле
и используют default enabled с учётом родителя. Задержки10/15мин остаются
серверными defaults; эта итерация добавляет режимы отдельных проверок,
а не редактор произвольных thresholds.

## Проверки и применение

- Fresh clean HEAD=origin/main=remote:
  babbce504813de57ffe36745dc9a2749b907fa95, parent91b49ede1.
  Detached sandbox C:/Users/SinicinVV/git_h/iguana-r18-sandbox.
- 267Java tests/56suites, failures/errors/skips=0. Полный targeted regression
  и повтор затронутых8suites после финальных monitor changes.
  Реальные isolated PostgreSQL/JPA проверяют idle/retired/unknown позиции,
  накопление наблюдений, fresh-after-idle, продвижение/разрыв истории,
  сохранность диагностики, снятие6старых critical signals, parent/child pause,
  persistence/expiry, outbox attempts, ручной priority и admin/settings/CSRF.
- Initial positive-backlog test выявил ошибку интерпретации JDBC Timestamp
  в локальном часовом поясе. Исправлена работа с instant; assertion сохранён,
  повторные runs GREEN. Нельзя делать вывод о сбое из отсутствия доказательств.
- 46JS cases +42старых exact-ticket-ID checks GREEN. JS повторены в main.
  160browser theme/viewport/mode combinations,160PNG: light/dark ×
  320/390/768/1440/1920, новые granular settings admin/read-only,
  parent precedence, independent week pause, failed save, sibling draft;
  повторены R11–R17 и130R13 incident states.
- Audit74templates/47pages GREEN. Отдельные CSS/inline themes не добавлялись.
  Четыре Sass artifacts отличались только EOL, normalized content проверен;
  в main они не переносились.
- 21validated source/test/tool/rule paths guarded-applied и content SHA сверены.
  Closeout добавил3paths, итог24. Staged/committed/pushed=false.
  Финальный package обновил resources после последних UI-правок.

Evidence: ignored spring-panel/target/incidents-r18-evidence/
(XML/logs, MVC captures, browser reports и160PNG), а также
spring-panel/target/incidents-r18-image/ (runtime before/after,
JAR validation, deploy log и post-apply JS).

## Рабочая приёмка и runtime checkpoint

Текущая Windows/Docker Desktop сессия этого checkout:

- Штатный монитор25.10.2026 в14:59:50–51UTC перевёл
  INC-221, INC-222, INC-223, INC-224, INC-225 и INC-236 в resolved.
  Исторический severity=critical сохранён в архиве, не является активным сигналом.
  Ручных status PATCH/UPDATE/DELETE и ручного запуска monitor не было.
- Web+worker: iguana-panel:incidents-r18-20261025,
  image sha256:08ef0f54d43a02686180d42add337561efece4383badcaf7c664b868d1de3be0,
  оба healthy.
- Web container6b45c4bfb626a2ffe9263d13dbbee4194e624b95a6ef79841682043be4bf58e8,
  start2026-10-25T14:59:58.6479931Z.
- Worker container5c2054c67d79560c27d0277b5e7551af24963c8c63372eae61401cc12f25b902,
  start2026-10-25T14:58:55.297217234Z.
- JAR SHA256ca6b7de3875230c78aaf12ce9da59ec9759f54c52599848ba2e44b6b6535fac6.
  2176entries/1385Java17-compatible classes. Изменены только6backend class
  families (16class entries) и3UI resources, остальные entry/library hashes
  прежние. Image собран offline поверх pinned R17 без внешнего base pull.
- Rollback-incidents-r18-web-20261025 и rollback-incidents-r18-worker-20261025 →
  R17 image15bfdcd63bd1347840fe0741e1dc64c5a5b269a9006bbac843a4815b789b40d0.
  Точные tags проверены, откат не выполнялся, предыдущие tags сохранены.
  Важно: если владелец запишет новые дочерние policy keys, R17 их не понимает.
  Старый image не гарантирует совместимость нового policy-файла; перед откатом
  нужен отдельный согласованный план сохранения/восстановления настроек.
- Полные env отдельно для каждой роли и4web/3worker mounts до/после равны.
  HTTP5resources равны packaged bytes; anonymous protected routes=302.
  Остальные13project containers и4bot JVM PID/start ticks не менялись.
- Позиции и updated_at всех трёх обсуждаемых checkpoints сохранились:
  fingerprint5b49b9ad67f06aa7b96833a204bba72c0d7394b36121e25a3254ce319c96e887.
  Их не «освежали» искусственно и не удаляли. Четыре исходные failed records
  также сохранены: fingerprintfc49912a…c88b до/после одинаков.
- FlywayV81, startup обеих ролей явно skip migration.
  Policy SHA97d777785d26636b73a3ba798ada6e7976133416fa0da6156db154c2f95aa1d0
  прежний. Migrations/backfill/flags, ручные business writes, replay/requeue,
  изменения ботов и external source-of-truth не выполнялись.
  Обычные jobs продолжаются и штатно меняют свои данные.

Приёмка владельца: Ctrl+F5; активные указанные сигналы отсутствуют,
в истории видны resolved и причина. В «Мониторинг» доступны три отдельные строки;
проверить нужный режим. Рабочий policy PATCH агентом не выполнялся.
Общий статус01-281 остаётся 🟣 до пользовательской приёмки.

## Точные24 файлов и команды владельцу

Запускать из корня после просмотра diff. Этот блок агентом не запускался.

```powershell
$r18Files = @(
    'ai-context/changelog/2026-10-25_180156_incident-idle-checkpoint-policy-r18.md',
    'ai-context/rules/05-monitoring-policy-and-employee-pickers.md',
    'ai-context/rules/06-incident-readable-descriptions.md',
    'ai-context/tasks/task-details/01-281.md',
    'docs/ui/incident-idle-checkpoint-policy-r18.md',
    'spring-panel/src/main/java/com/example/panel/service/IncidentMonitoringPolicyService.java',
    'spring-panel/src/main/java/com/example/panel/service/IncidentRouteDeliveryOutboxService.java',
    'spring-panel/src/main/java/com/example/panel/service/IncidentService.java',
    'spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportHealthSnapshotScheduler.java',
    'spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitor.java',
    'spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportOpsService.java',
    'spring-panel/src/main/resources/static/js/incident-descriptions.js',
    'spring-panel/src/main/resources/static/js/incident-workbench-ui.js',
    'spring-panel/src/main/resources/templates/incidents/index.html',
    'spring-panel/src/test/java/com/example/panel/controller/IncidentPolicyAndUserOptionsWebMvcTest.java',
    'spring-panel/src/test/java/com/example/panel/service/IncidentMonitoringPolicyServiceTest.java',
    'spring-panel/src/test/java/com/example/panel/service/IncidentRouteDeliveryOutboxServiceTest.java',
    'spring-panel/src/test/java/com/example/panel/service/MonitoringDirectionGuardsTest.java',
    'spring-panel/src/test/java/com/example/panel/service/TransportIncidentNoisePostgresqlTest.java',
    'spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitorTest.java',
    'spring-panel/src/test/java/com/example/panel/service/integration/IntegrationTransportOpsServiceTest.java',
    'spring-panel/src/test/js/incident-descriptions-r13.test.js',
    'spring-panel/src/test/js/incident-runtime-monitors-r18.test.js',
    'tools/incident-runtime-monitors-qa.cjs'
)

git status --short
$alreadyStaged = @(git diff --cached --name-only)
if ($alreadyStaged.Count -ne 0) { throw 'Сначала разберите существующий staging.' }

git add -- $r18Files
if ($LASTEXITCODE -ne 0) { throw 'git add failed' }

git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Staged diff check failed' }
$r18Staged = @(git diff --cached --name-only)
git diff --cached --name-only
if ($r18Staged.Count -ne $r18Files.Count -or @(Compare-Object $r18Files $r18Staged).Count -ne 0) { throw 'Staging manifest mismatch' }

git commit -m "fix(incidents): исключить простой курсоров и разделить проверки #ai"
if ($LASTEXITCODE -ne 0) { throw 'Commit failed' }

git push
```
