# Конкретные операции и названия задач — R16

24.10.2026, задача01-281, 🟣. Реализовано и развёрнуто; commit/push не выполнялись.

## Что означали примеры владельца

Read-only проверка рабочего экземпляра подтвердила:

- Четыре failed client_message.active_ticket записи менялись24.08.2026,
  12:50:22–12:51:22UTC. Во всех last_error — bad SQL grammar при INSERT
  chat_attachment_metadata. Не удалось записать сведения о вложении при
  обработке сообщения. Это не доказательство потери файла, неисправности
  текущей схемы БД или четырёх новых ошибок сегодня.
- INC-225 относится к ui-event-outbox-watch / UiEventOutboxWatcher.
  Задача в ops-worker читает события об обращениях, сообщениях и оценках,
  передавая изменения в открытый интерфейс панели.
  В сохранённой проверке cursor_lag=0; updated_at позиции —
  22.10.2026T12:44:21Z. Код сохраняет позицию при продвижении к новым
  записям, не при каждом запуске. Поэтому2827минут — возраст позиции,
  а не доказанное время простоя интерфейса.
- Счётчик47 проверок не означает47 новых ошибок или неудачных запусков.

В агрегат входят также отметки operator-notification-watch.chat-history и
operator-notification-watch.feedbacks. У последней сохранилась позиция прежнего
наблюдателя оценок; текущий OperatorNotificationWatcher её не обновляет.
Здесь не удалялись старые checkpoints и не менялась логика мониторинга.

## Что изменено

- У отдельной задачи видны понятное название, назначение, worker_key,
  компонент и источник данных. Длительность показана днями/часами и минутами.
  Нулевой gap отделён от неизвестного; ненулевой gap не превращается
  в точное число пропущенных сообщений/уведомлений.
- Конкретные текущие ошибки/даты стоят перед общим описанием.
  Сохранённые aggregate counters и справочный текст свёрнуты по умолчанию,
  но не потеряны. Без analytics pane и для закрытого инцидента сохранённое
  описание остаётся доступным, без нового fetch или подмены исторического
  snapshot текущими данными.
- Для отдельного worker signal отбирается только его точный worker_key,
  включая случай, когда текущая диагностика уже healthy. Если записи нет,
  показывается «состояние неизвестно», не подставляются другие задачи.
- Навигация фокусирует диагностику именно этой задачи, не сохраняет cursor
  и не запускает replay. Late GET/ошибка refresh не теряют draft;
  последняя успешная выборка при ошибке явно помечена как устаревшая.
- HTML экранируется, длинные имена переносятся общим text-break.
  Новые CSS, endpoints, grants и backend classes не добавлялись.

Ограничение: stale/lag formulas и правила создания/приоритета инцидентов
не менялись. Старый progress cursor по-прежнему может создавать critical
даже при отсутствии новых данных; для исправления самого критерия, включая
устаревшую запись feedbacks, нужен отдельный backend scope. UI не маскирует
эту границу и не объявляет процесс остановленным.

## Проверки

- Fresh clean HEAD=origin/main=remote
  ce25e2d4b4e84c815b702de6c3be05606d5fdd38. Detached sandbox
  C:/Users/SinicinVV/git_h/iguana-r16-sandbox.
  9 source/test/tool/rule paths guarded-applied после GREEN и content-сверены;
  metadata closeout добавил3 paths, итог12. Staging/commit/push=false.
- 38 JS unit cases,42 прежних exact-ID checks.
  4 Java tests /3 suites: реальные MVC captures, policy/user-options
  security и credential UI contract; failures/errors/skips=0.
  Это targeted UI regression, не повтор полного backend suite R15.
- 50 новых browser combinations: aggregate/worker/missing/noanalytics/closed ×
  light/dark ×320/390/768/1440/1920. Проверены exact worker selection,
  healthy/missing current row, human duration, current-before-snapshot,
  collapsed counters, no new grants/fetch, focus, stale GET, drafts, no writes.
  Повторены R11/R12/R14/R15 и130 R13 states. Всего120 PNG.
- Source audit74templates/47pages GREEN. Sass artifacts восстановлены только
  после проверки, что отличались лишь EOL. Новый details потребовал точного
  locator исходной диагностики в R13 harness; прежние assertions сохранены.
- В initial runtime comparator обнаружилось LF/CRLF различие sandbox/main.
  Диагноз подтверждён отдельно: normalized content одинаков. Итоговая проверка
  сравнивает HTTP побайтно с packaged stage, затем semantic content с sandbox;
  сами hash guards не отключались. Повторный runtime acceptance GREEN.

Evidence: ignored spring-panel/target/incidents-r16-evidence/
(120PNG, browser reports,4XML, MVC captures, JS/Maven logs, diagnosis.json,
runtime before/after и jar-validation.json).

## Runtime checkpoint

Текущая Windows/Docker Desktop сессия этого checkout:

- Только panel-web: iguana-panel:incidents-r16-20261024,
  image sha256:12a1de538518a88edfe9f3c98317ee7254b2ec129cc0b39d8f10590aabedb5ae,
  healthy; container7feab51421d21ccfbdc3f41b5b697976e9cdbbfcf2b67469c8b3259b4bd4a92c,
  start2026-10-24T12:11:11.247413808Z.
- Runtime JAR SHA256
  17a8af10cb34dd9f79baeb6d796af780960f017630bcddc12134f8ca5402cc03.
  2176 entries; заменены ровно3 UI resources. Все остальные entry hashes,
  Java classes и libraries сохранены, включая backend priority fixes R15.
- Rollback iguana-panel:rollback-incidents-r16-web-20261024 →
  R15 image75b3345638d47932a2145d9e8b5b6a363f97c15a08d106f4426a036815a9144f.
  Наличие проверено; откат не выполнялся. Предыдущие tags сохранены.
- Full web environment и4 mounts до/после равны. HTTP3JS+CSS равны packaged
  resources; protected GET без сессии=302. Все14 остальных project containers
  сохранили id/image/start. Worker остался на R15, bots — на прежнем image;
  четыре bot JVM PID/start ticks не изменились.
- FlywayV81 до/после, web explicit skip migration. Monitoring policy SHA прежний.
  Worker/bots/infra/migrator не перезапускались. Migrations/backfill/flags,
  ручные production incident mutations, replay/requeue и external writes
  не выполнялись. Штатные jobs продолжают работать.

Приёмка владельца: Ctrl+F5; открыть агрегат и INC-225, проверить состав/даты,
название задачи, отдельный snapshot и переход к её диагностике.
Рабочие authenticated PATCH агент не выполнял.

## Точные12 файлов и команды владельцу

Запускать из корня репозитория после просмотра diff. Этот блок агентом не запускался.

```powershell
$r16Files = @(
    'ai-context/changelog/2026-10-24_151319_incident-worker-identity-r16.md',
    'ai-context/rules/06-incident-readable-descriptions.md',
    'ai-context/tasks/task-details/01-281.md',
    'docs/ui/incident-worker-identity-r16.md',
    'spring-panel/src/main/resources/static/js/incident-descriptions.js',
    'spring-panel/src/main/resources/static/js/incidents-workbench.js',
    'spring-panel/src/main/resources/templates/incidents/index.html',
    'spring-panel/src/test/js/fixtures/incident-descriptions-r13.cjs',
    'spring-panel/src/test/js/incident-descriptions-r13.test.js',
    'spring-panel/src/test/js/incident-worker-details-r16.test.js',
    'tools/incident-descriptions-qa.cjs',
    'tools/incident-worker-details-qa.cjs'
)
git status --short
$alreadyStaged = @(git diff --cached --name-only)
if ($alreadyStaged.Count -ne 0) { throw 'Сначала разберите существующий staging.' }
git add -- $r16Files
if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Staged diff check failed' }
$r16Staged = @(git diff --cached --name-only)
git diff --cached --name-only
if ($r16Staged.Count -ne $r16Files.Count -or @(Compare-Object $r16Files $r16Staged).Count -ne 0) { throw 'Staging manifest mismatch' }
git commit -m "fix(incidents): назвать задачи и показать конкретные ошибки первыми #ai"
if ($LASTEXITCODE -ne 0) { throw 'Commit failed' }
git push
```
