# Понятные описания инцидентов — R13

24.10.2026, задача 01-281; 🟣 — ручная приёмка владельца остаётся.

## Результат

Очередь показывает понятное название и короткую причину. Карточка объясняет,
что зафиксировано, на что это может влиять и что проверить. Сохранённые
числа снабжены подписями/единицами. Например: «Фоновый обработчик давно
не подтверждал свою работу», «Уведомления операторов о новых сообщениях
могут появляться позже обычного», «С последней отметки работы: 14 мин».

Покрыты transport bridge/общие и отдельные worker checkpoints/sustained
pressure, шесть состояний ключей доступа, provider delivery, все 16
Alertmanager rules текущего репозитория и известный delivery smoke test.

Это чистое UI-представление. Исходные title/summary/description/payload и
editable values не переписываются. Старые инциденты читаются по-новому без
backfill; ручные тексты сохранены. Технический оригинал доступен по раскрытию.
Пояснение неизвестного alert не угадывается. Различаются прошлая проблема
закрытого инцидента и текущее состояние. Отсутствие метрик не доказывает отказ
сервиса, отметки работы — остановку процесса; cursor lag не выдаётся за
точное количество записей. Primary facts не раскрывают credentials/path/query
из URL проверки. Всё HTML-rendering экранировано. CSS/темы не менялись.

Граница: это чтение на /incidents, не перевод текста исторических
email/webhook/SSE notifications или исходных данных в других API.
Пороговые пояснения известных alert names соответствуют текущим repo rules;
произвольно изменённое внешнее правило с тем же именем нужно сверять по
исходному уведомлению. Неизвестные типы не получают выдуманный диагноз.

## Проверки

- 22 новых Node tests: примеры, immutability, все repo alert names, шесть
  credential statuses, неизвестные/повреждённые данные, отсутствие ложного
  диагноза, exact address sanitization, порядок и cache versions scripts.
- 42 прежних внутренних JS checks: exact ticket IDs transport/create, без ослабления.
- 26 targeted Java tests / 12 suites: MVC и incident/policy/security/PG
  regressions, failures/errors/skips=0. Не весь suite репозитория.
- Реальные MVC HTML + production JS с локальным fixture API:
  13 примеров × light/dark × 320/390/768/1440/1920 =130 состояний, плюс
  historical checks. List без events и detail с events; raw text folding,
  сохранение edit values, escaping/XSS, no unexpected writes/JS errors/overflow.
- Повторены 10 R12 workbench +10 R11 notification browser scenarios:
  закрытие/комментарий/cancel/паузы/search/exact IDs/scroll/focus/notifications.
  Всего 80 PNG. Read-only source audit: 74 templates, 47 pages GREEN.
- Guarded apply: main был clean, fresh HEAD=origin/main=remote
  dff5403764aaf98bac3299740d8608ef909ed494. 7 validated paths content-сверены
  с detached sandbox. Staged/committed/pushed=false.
- Упаковка v1 отклонена из-за отсутствующего .NET type; v2 hash guard
  обнаружил изменение потока пустой directory entry через ZipArchive.Update.
  Эти artifacts не разворачивались. v3 использует Java jar --update;
  ВСЕ остальные ZIP entries, включая пустые директории, content-identical.
  Guard не ослаблялся.

Artifacts: ignored spring-panel/target/incidents-r13-evidence/
(80 PNG, browser reports, MVC captures, 12 XML, JS/Maven logs, runtime JSON).
Sandbox: C:/Users/SinicinVV/AppData/Local/Temp/iguana-incidents-r13-20261024.

Повтор из корня checkout (PLAYWRIGHT_MODULE можно заменить своим Playwright):

```powershell
$env:PLAYWRIGHT_MODULE = 'C:/Users/SinicinVV/AppData/Local/Programs/Python/Python312/Lib/site-packages/playwright/driver/package'
.\spring-panel\mvnw.cmd -q -f spring-panel/pom.xml '-Diguana.ui.capture=C:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/target/neutral-ops-rendered' '-Dtest=NeutralOpsPageFamiliesWebMvcTest,Incident*Test,MonitoringDirectionGuardsTest' test
node --test spring-panel/src/test/js/incident-descriptions-r13.test.js spring-panel/src/test/js/incidents-create-exact-ticket-id-r35.test.js spring-panel/src/test/js/incidents-transport-exact-ticket-id-r34.test.js
node tools/incident-descriptions-qa.cjs
node tools/incident-workbench-qa.cjs
node tools/incident-notifications-qa.cjs
node tools/neutral-ops-source-audit.cjs
```

## Runtime checkpoint

Обновлён только panel-web, UI-only overlay на свежем подтверждённом R12.
Новый полный backend main не собирался/не подменялся.

- Image iguana-panel:incidents-r13-20261024,
  sha256:483445cbfaecd011cf36ef21e94a9fb2173aacab3c5e1b03bcfb83761e0be39d.
  Healthy; start 2026-10-24T08:37:25.674046385Z.
- JAR SHA256: 4b6025fb1df9f68c8eb4040a213bd38d26c718b088c1bb04a66e38384b3fa81f,
  runtime совпал. 2175→2176 entries, ровно три изменены/добавлены:
  BOOT-INF/classes/static/js/incident-descriptions.js,
  BOOT-INF/classes/static/js/incidents-workbench.js,
  BOOT-INF/classes/templates/incidents/index.html.
  Остальные classes/libraries/resources и directory entries content-identical.
- Rollback iguana-panel:rollback-incidents-r13-web-20261024 →
  sha256:f34f1adbd276a4cee326d5b9a0b6a573b6ee736d8aee52eccd62fd7594185831.
  Наличие подтверждено, откат не выполнялся; прежние rollback tags сохранены.
- Local build без pull, guarded compose --no-deps --no-build --pull never
  только panel-web. Full env до/после и четыре bind mounts сверены.
  Два изменённых JS-ресурса и неизменённый CSS=200, SHA сверены;
  anonymous /incidents, monitoring API и user options API=302.
- ops-worker прежний container6d2047bd…, image f34f1adb…,
  start2026-10-23T12:41:48.789182218Z. bot-runner прежний container899628fb…,
  image97ccb6fc…, start2026-10-22T12:43:42.881307558Z.
  Четыре Java PID/start совпали с fresh pre-deploy evidence.
- Flyway V81 до/после. Миграций, repair/backfill, смены flags, policy/status
  или ручных external business writes нет. Worker/bots/infra/db-migrate
  не перезапускались. Штатная работа background jobs продолжается.
- Prepare/deploy helpers и hash report: ignored
  spring-panel/target/incidents-r13-image/. Authenticated production incident
  writes не выполнялись; fixtures не заменяют рабочую приёмку владельца.

Приёмка: Ctrl+F5 на /incidents. Открыть transport, credential и Alertmanager
инциденты; проверить понятные тексты, сохранённый технический оригинал,
ручное описание и historical disclaimer закрытого инцидента.

## Точный Git scope — 10 файлов

Staging/commit/push не выполнялись. Не добавлять JAR/target/PNG/logs.

```powershell
$files = @(
  'ai-context/changelog/2026-10-24_114000_incident-descriptions-r13.md'
  'ai-context/rules/06-incident-readable-descriptions.md'
  'ai-context/tasks/task-details/01-281.md'
  'docs/ui/incident-descriptions-r13.md'
  'spring-panel/src/main/resources/static/js/incident-descriptions.js'
  'spring-panel/src/main/resources/static/js/incidents-workbench.js'
  'spring-panel/src/main/resources/templates/incidents/index.html'
  'spring-panel/src/test/js/fixtures/incident-descriptions-r13.cjs'
  'spring-panel/src/test/js/incident-descriptions-r13.test.js'
  'tools/incident-descriptions-qa.cjs'
)
if (@(git diff --cached --name-only).Count -ne 0) { throw 'Сначала проверьте уже staged изменения' }
git status --short
git add -- $files
git diff --cached --check
git diff --cached --name-only
git commit -m 'fix(incidents): сделать описания проблем понятными #ai'
git push origin main
```

Общий статус 01-281 остаётся 🟣; 🟢 выставляет владелец после своей проверки.
