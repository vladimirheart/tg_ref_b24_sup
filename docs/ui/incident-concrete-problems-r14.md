# Конкретные причины за счётчиками — R14

24.10.2026, задача 01-281, 🟣.

## Результат и границы

Пример владельца больше не ограничивается переводом «events/checkpoints».
Описание объясняет, что входящие данные — это, например, сообщения от ботов
или создание обращения; служебные задачи обновляют уведомления о сообщениях,
оценках и открытые страницы панели. Десять проверок подряд — не десять новых
ошибок: одна старая failed запись может сохраняться в каждом результате.

У двух aggregate transport signals есть отдельная текущая подробная выборка:
тип операции, известная причина понятным текстом, связанные обращения/записи,
даты, конкретные задачи. Источник — существующий read-only
/api/analytics/integration-transport, который страница уже загружала.
Новых endpoint/probes/backend прав/изменений схемы нет.

Текущая выборка явно отделена от сохранённой проверки, имеет время загрузки,
даты записей и оговорку limit. Она не выдаётся за исторический состав закрытого
инцидента или другого worker signal. Отсутствие analytics pane не вызывает
fetch. При ошибке refresh старые данные сохраняются с предупреждением.
Поздняя загрузка не пересоздаёт editable fields и не теряет draft.
Навигация открывает существующий tab/фокус, не вызывает replay/requeue.

Read-only SQL в рабочем экземпляре показал четыре failed записи
client_message.active_ticket с updated_at от24.08.2026. Во всех last_error —
bad SQL grammar на INSERT INTO chat_attachment_metadata.
Поэтому UI может конкретно объяснить неудачную запись сведений о вложении.
Это не означает доказанную потерю файла или неисправность текущей схемы.
Данные сообщений/вложений не извлекались; изучалась metadata ошибок.
Старые записи не исправлялись/не удалялись/не повторялись.

## Проверки

- Fresh clean HEAD=origin/main=remote4218db92cc5321291f867981bfdfb31cb070fc4f.
  9 source/test/tool/rule paths сначала validated в detached sandbox,
  затем content-сверены после guarded apply; git diff --check GREEN.
- 6 новых unit cases +22 R13 unit cases +42 legacy exact-ID checks.
  Два wording expectations R13 fixtures и cache-version contract обновлены
  по новому запросу; assertions immutability/exact IDs/unknown signals/XSS
  не ослаблялись.
- 3 Java MVC/security tests, 2 suites, failures/errors/skips=0. Это targeted
  UI/security regression, не полный backend suite; Java classes не менялись.
- 30 новых browser combinations: active/noanalytics/closed ×
  light/dark ×320/390/768/1440/1920. Delayed GET/draft, даты старых ошибок,
  понятная причина SQL-write failure, три задачи с двумя idle примерами,
  partial sample, отсутствие analytics fetch, historical boundary,
  read-only tab/focus, failed refresh/stale data, escaping/XSS, no writes.
- Повторены R13:130 incident states в10 theme/viewport combinations,
  R12:10 workbench и R11:10 notifications scenarios. Всего90 PNG.
  Source audit74templates/47pages GREEN; CSS/темы не менялись.
- Fixture API локальный; рабочие authenticated mutations агентом не выполнялись.
  Старые incident payload не содержат полного состава ошибок: новая current
  выборка не восстанавливает задним числом недостающую историческую информацию.
  Неизвестные причины остаются честным fallback, не предположением.

Artifacts: ignored spring-panel/target/incidents-r14-evidence/ (90PNG,
browser reports, MVC captures, 2XML, JS/Maven logs, runtime-acceptance.json).
Sandbox: C:/Users/SinicinVV/AppData/Local/Temp/iguana-incidents-r14-20261024.

### Воспроизведение

```powershell
$env:PLAYWRIGHT_MODULE = 'C:/Users/SinicinVV/AppData/Local/Programs/Python/Python312/Lib/site-packages/playwright/driver/package'
.\spring-panel\mvnw.cmd -q -f spring-panel/pom.xml '-Diguana.ui.capture=C:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/target/neutral-ops-rendered' '-Dtest=NeutralOpsPageFamiliesWebMvcTest,IncidentPolicyAndUserOptionsWebMvcTest' test
node --test spring-panel/src/test/js/incident-concrete-problems-r14.test.js spring-panel/src/test/js/incident-descriptions-r13.test.js spring-panel/src/test/js/incidents-create-exact-ticket-id-r35.test.js spring-panel/src/test/js/incidents-transport-exact-ticket-id-r34.test.js
node tools/incident-concrete-problems-qa.cjs
node tools/incident-descriptions-qa.cjs
node tools/incident-workbench-qa.cjs
node tools/incident-notifications-qa.cjs
node tools/neutral-ops-source-audit.cjs
```

Другой установленный Playwright допустим через PLAYWRIGHT_MODULE.
Generated CSS EOL-only noise не включать в commit.

## Runtime checkpoint

Обновлён только panel-web UI-only overlay поверх проверенного R13.

- Image iguana-panel:incidents-r14-20261024,
  sha256:15e6544eab1f7a997ab2d11f97078032d264989f74eae68450f944e05313ee72;
  healthy, start2026-10-24T09:12:08.993388583Z.
- JAR355779d7a9a6766b0375365a01ae88dd4b7ea52ba1a5de82211d3ba8d2115db6,
  runtime SHA совпал. 2176→2176 entries, ровно3 changed resources:
  BOOT-INF/classes/static/js/incident-descriptions.js,
  BOOT-INF/classes/static/js/incidents-workbench.js,
  BOOT-INF/classes/templates/incidents/index.html.
  ВСЕ остальные entry hashes, включая classes/libraries/directories, прежние.
- Rollback iguana-panel:rollback-incidents-r14-web-20261024 →
  sha256:483445cbfaecd011cf36ef21e94a9fb2173aacab3c5e1b03bcfb83761e0be39d.
  Наличие проверено, откат не выполнялся; прежние release/rollback tags сохранены.
- Local build без pull; guarded compose --no-deps --no-build --pull never
  только panel-web. Full environment до/после и4 bind mounts совпали.
  Два изменённых JS-ресурса и прежний CSS=200/SHA verified; anonymous /incidents,
  transport analytics API и monitoring API=302.
- Worker6d2047bd… imagef34f1adb…, start2026-10-23T12:41:48.789182218Z;
  bot-runner899628fb… image97ccb6fc…, start2026-10-22T12:43:42.881307558Z.
  Container identity/start и4 bot JVM PID/start неизменны.
- FlywayV81 до/после. Миграций/repair/backfill/flags/policy/ручных DB/external
  business writes нет. Боты/worker/infra/db-migrate не перезапускались,
  failed records не удалялись, replay/requeue не запускались.
  Штатные background jobs продолжаются.
- Helpers/hash report: ignored spring-panel/target/incidents-r14-image/.
  Owner authenticated acceptance ещё требуется.

Для приёмки: Ctrl+F5 на /incidents, открыть aggregate transport incident;
сравнить сохранённую проверку и датированную текущую выборку, открыть
ошибочные записи/задачи кнопкой. Убедиться, что просмотр не запускает повторы.
Само исправление старых ошибок и качества monitoring thresholds не входит в R14.

## Точный Git scope —12 файлов

Staging/commit/push не выполнялись. JAR/target/PNG/logs не добавлять.

```powershell
$files = @(
  'ai-context/changelog/2026-10-24_121300_incident-concrete-problems-r14.md'
  'ai-context/rules/06-incident-readable-descriptions.md'
  'ai-context/tasks/task-details/01-281.md'
  'docs/ui/incident-concrete-problems-r14.md'
  'spring-panel/src/main/resources/static/js/incident-descriptions.js'
  'spring-panel/src/main/resources/static/js/incidents-workbench.js'
  'spring-panel/src/main/resources/templates/incidents/index.html'
  'spring-panel/src/test/js/fixtures/incident-concrete-problems-r14.cjs'
  'spring-panel/src/test/js/fixtures/incident-descriptions-r13.cjs'
  'spring-panel/src/test/js/incident-concrete-problems-r14.test.js'
  'spring-panel/src/test/js/incident-descriptions-r13.test.js'
  'tools/incident-concrete-problems-qa.cjs'
)
if (@(git diff --cached --name-only).Count -ne 0) { throw 'Сначала проверьте уже staged изменения' }
git status --short
git add -- $files
git diff --cached --check
git diff --cached --name-only
git commit -m 'fix(incidents): показать конкретные операции за счётчиками #ai'
git push origin main
```

Статус01-281 остаётся 🟣; 🟢 выставляет владелец после своей проверки.
