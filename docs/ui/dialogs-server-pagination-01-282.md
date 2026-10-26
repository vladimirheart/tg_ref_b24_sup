# 01-282 — серверная пагинация списка диалогов

Статус: 🟣 AI завершил; ручная приёмка пользователя ожидается. Дата: 26.10.2026.

## Результат

`/dialogs` больше не получает полный архив для HTML. GET `/api/dialogs` и read-only POST `/api/dialogs/query` выбирают страницу в PostgreSQL: по умолчанию 20, максимум 100 строк. Режим «Все» удалён; старое сохранённое `all` становится 20. Нечисловые параметры GET отклоняются HTTP 400, числовой размер ограничивается сервером.

Поиск по полному допустимому набору, статус, представление очереди, SLA-окно и сортировка выполняются до LIMIT/OFFSET. `%`, `_`, обратная косая черта — буквальные символы поиска, не SQL wildcard. Номера обращений вычисляются до фильтрации и не начинаются заново на странице. Count и clamped page читаются одним SQL statement/snapshot; при исчезновении последней страницы выбирается последняя существующая, пустая выдача имеет page=1/count=0.

«Мои диалоги» — отдельная ограниченная выборка: до 20 последних обращений в каждой из трёх групп и до 100 явно закреплённых активных записей дополнительно. Полные количества приходят отдельно; UI указывает, сколько показано. Смена страницы/фильтра не пересобирает сводку из видимых строк и не запрашивает её заново. SSE/poll перечитывает текущую страницу и общую сводку, а не весь архив. Это bounded page refresh, не per-row delta protocol.

Поиск имеет debounce; смена фильтра немедленно инвалидирует старый ответ. Abort/generation guards защищают и при поздней доставке отменённого запроса. SSE-события coalesce-ятся; после возвращения к вкладке выполняется свежее чтение. Временные SLA-фильтры и истекающие локальные snooze проверяются ограниченным чтением раз в минуту даже при тихом SSE. Ошибка запроса не выдаётся за пустую очередь и не оставляет активные строки от другого фильтра. Выбор для групповых действий очищается на смене страницы/фильтра. Прямое открытие обращения не требует его присутствия на текущей странице.

Новый POST не изменяет БД, требует PAGE_DIALOGS/CSRF и позволяет передавать точные списки opaque ID без обрезки/разделения по запятой и переполнения URL. Лимиты входа: search 500 символов, status 100, до 1000 snoozed и 100 pinned ID длиной до 255. Существующая auth-граница GET не расширена. GENERAL и raw-ID collisions, включая mixed-scope, исключены до нумерации и пагинации. Grants/membership/feature flags не менялись.

Чтение PostgreSQL timestamp с offset в SLA helper приведено к тому же instant, который использует SQL-фильтр; формулы и пороги SLA не менялись.

## Проверки и границы доказательств

- 178 Java tests / 11 suites: page SQL, реальный read-service → HTTP JSON, базовые и snapshot legacy reads, MVC, PAGE_DIALOGS/CSRF нового POST, номера, профили, timezone/column-width/navigation contracts. Failures/errors/skips: 0.
- 98 Node runner tests: GREEN. Включены 9 новых async pagination tests и остальные актуальные UI-контракты. Старые ожидаемые asset versions в трёх контрактах обновлены до точных текущих cache keys; проверки timezone/ширины колонок/API сохранены.
- 4 Chromium contexts: light/dark × 390/1440; 8 screenshots. Реальный MVC HTML, реальные локальные JS/CSS, синтетический read-only API. Проверены 20/100 DOM rows, смена страницы, поиск записи вне первой страницы, общий count, ответ вне порядка, ошибка/повтор, SSE burst. Browser errors/external requests: 0.
- Source audit: 74 templates / 47 standalone pages, без новых самостоятельных themes/inline stylesheets.
- Изолированный PostgreSQL с Flyway V1–V82 и 10 000 синтетических обращений: первый page=20, последний page=100, My preview=20, полные counts=10 000. Финальный замер этих трёх выборок — 716 мс в данной тестовой среде; это **не** production benchmark/SLA.
- Расширенный Java прогон дополнительно выявил прежние 404 на `/api/dialogs/{id}/reassign`, take/spam/reply путях в 4 сценариях `DialogListIntegrationTest`. Чистый detached baseline `eebffe9…`: те же 4 failures из 7, без изменений этой задачи. Сами тесты/маршруты не ослаблялись и не переписывались.
- Прежний Node `dialog-read-query-requests-r53.test.js` падает на VM-double заметок: «Не удалось загрузить заметки.» вместо «Заметок нет.». Ошибка воспроизведена непосредственно на неизменённом main; этот файл не редактировался и не включён в GREEN 98.

Нельзя считать весь test suite проекта GREEN: выше отдельно названы подтверждённые baseline failures. Авторизованная функциональная приёмка рабочего портала — PENDING; тестовая учётная запись/credentials не создавались и не подбирались.

## Что не входит в этот шаг

История выбранного диалога, workspace-navigation, главная/Home/Dashboard и фоновые маршрутизаторы ещё имеют отдельные full-read paths; эта задача ограничивает именно таблицу `/dialogs` и её вспомогательный preview. COUNT, поиск, нумерация и сортировка всё ещё могут просматривать/сортировать набор в PostgreSQL — утверждения о постоянной стоимости SQL независимо от количества записей нет. Для существенно больших объёмов следующий отдельный шаг — production EXPLAIN/index/cursor review, а не возврат неограниченного HTML/JSON.

## Source и runtime checkpoint

- Baseline / HEAD / origin/main / remote main: `eebffe9bf34a36648e22fab73f14e5b63e594967`.
- Основной checkout изначально clean. 26 исходных файлов перенесены только после sandbox GREEN с exact file/content guards; closeout добавляет 2 файла, итог 28. Source-applied=true, staged=false, committed=false, pushed=false.
- Source transfer-v1 остановился **до записи** из-за UTF-16 логов Windows; v2 читает правильную кодировку, сохраняет те же guards и прошёл. Generated CSS отличается только EOL, в основной checkout не переносился.
- Рабочий Docker в этой сессии подтверждён по R20 web ID/image/JAR, worker и схеме, не выведен только из совпадения пути checkout.
- Проверено побайтное совпадение пяти исходных серверных классов текущего runtime с freshly compiled baseline. Candidate — узкий overlay на R20 JAR: изменены 20 entries, добавлены 8, удалено 0, 2168 entries сохранены. Изменения только в восьми class families и четырёх dialog resources; зависимости и миграции не заменялись. Неразвёрнутые соседние серверные изменения R177/R178 не поставлялись.
- Release: `iguana-panel:dialogs-pagination-20261026` (и `iguana-panel:local`). Image: `sha256:1a8f1c4f2ca630fb15314fcba23123fadc00b2c79bf5cd04db58420679e16733`.
- Web container: `79e03807eb0af94461581fe2089638711962570eedaf4d097ebf7faf53010be8`, healthy; started `2026-10-26T12:38:28.634361Z`.
- Runtime JAR SHA-256: `4fb5c436f236ee46d6fe316332468807756954083d261ac9c0e583625c95ff83`.
- Worker: `5c2054c67d79560c27d0277b5e7551af24963c8c63372eae61401cc12f25b902`, прежний R18, не перезапускался. Все 17 прочих Compose-контейнеров и четыре bot JVM/PID/start ticks сохранены.
- Полный environment SHA-256: `4906c6a36c2dc09159ea57405710b1eca7e8a108c7207b915f47763579651ccb`, четыре bind mounts — без изменений.
- Flyway V82/history SHA-256: `1af5845d5c7215dda2a10eff4e6b3838d1eaf5d14ef16f2b4e1e5797c7299369`; monitoring policy SHA-256: `97d777785d26636b73a3ba798ada6e7976133416fa0da6156db154c2f95aa1d0` — без изменений. No migration/backfill/flags/external-system mutation.
- Rollback tag: `iguana-panel:rollback-dialogs-pagination-web-20261026` → R20 image `sha256:cbd27dc80c76dcb35fdb4e02af23d662d853ae1cbd0d6038cae9690189503a1d`, JAR `701b1728…`. Tag сохранён. Старые rollback tags не удалялись.
- Первая release-v1 попытка ошибочно восприняла native Docker stderr `Recreate` как PowerShell 5.1 exception. Выполнен возврат к R20; его healthy состояние подтверждено. Release-v2 отделяет progress/stderr от exit code, заново проверяет image/env/binds/schema и успешно выпущен. Это реальный промежуточный restart/rollback, не «первая попытка без изменений».
- После выпуска: полный runtime evidence GREEN; три изменённых JS отдаются HTTP 200 с SHA-256, совпадающими с source. Анонимный `/dialogs` — redirect 302, auth не обходился.

## Артефакты проверки

Локальная песочница: `C:/Users/SinicinVV/git_h/iguana-dialogs-01-282-sandbox`; чистая baseline-проверка: `C:/Users/SinicinVV/git_h/iguana-dialogs-01-282-baseline`.

- `spring-panel/target/dialogs-pagination-tests-v4.log`, `surefire-reports/`.
- `spring-panel/target/dialogs-pagination-js-green.log`, `dialogs-pagination-qa/browser-report.json` и PNG.
- `spring-panel/target/dialogs-pagination-image/{jar-validation,before,after}.json`, `deploy-v1.log`, `deploy-v2.log`, `prepare-image-v1.ps1`, `release-v1.ps1`, `release-v2.ps1`.
- `spring-panel/target/guarded-transfer-v1.cjs`, `guarded-transfer-v2.cjs`; source operator-v2 SHA-256: `3e1c22581cfa7a2cb2e64248112eb3c6a02aed3d76800a18efc9f801dfbf5ef8`.

Артефакты `target` не входят в commit. Release скрипты привязаны к старым exact runtime guards; повторно вслепую не запускать.

## Ручная приёмка

Открыть `/dialogs`, при необходимости Ctrl+F5. Проверить переход страниц и размеры 20/100, поиск известного старого обращения по номеру, статус и SLA-фильтры. Открыть прямую ссылку на обращение вне текущей страницы; проверить его заметки/историю/ответ. Убедиться, что число «Мои диалоги» не уменьшается до размера страницы, закрепления сохраняются, групповой выбор не переносится на следующую страницу. Принятие задачи и переход в 🟢 выполняет пользователь.

## Точный manifest и пользовательский commit/push

Агент staging/commit/push не выполнял. Блок проверяет только эту задачу; при несовпадении остановиться и посмотреть пользовательские изменения.

```powershell
Set-Location C:\Users\SinicinVV\git_h\tg_ref_b24_sup
$expected = @(
  'ai-context/changelog/2026-10-26_154121_dialogs-server-pagination-01-282.md'
  'ai-context/rules/07-dialog-list-pagination.md'
  'ai-context/tasks/task-details/01-282.md'
  'ai-context/tasks/task-list.md'
  'docs/ui/dialogs-server-pagination-01-282.md'
  'spring-panel/src/main/java/com/example/panel/controller/DialogListController.java'
  'spring-panel/src/main/java/com/example/panel/controller/DialogsController.java'
  'spring-panel/src/main/java/com/example/panel/model/dialog/DialogListPage.java'
  'spring-panel/src/main/java/com/example/panel/model/dialog/DialogListQuery.java'
  'spring-panel/src/main/java/com/example/panel/service/DialogListPageSql.java'
  'spring-panel/src/main/java/com/example/panel/service/DialogListReadService.java'
  'spring-panel/src/main/java/com/example/panel/service/DialogLookupReadService.java'
  'spring-panel/src/main/java/com/example/panel/service/DialogSlaRuntimeService.java'
  'spring-panel/src/main/resources/static/js/dialogs-list-runtime.js'
  'spring-panel/src/main/resources/static/js/dialogs-my-dialogs-runtime.js'
  'spring-panel/src/main/resources/static/js/dialogs.js'
  'spring-panel/src/main/resources/templates/dialogs/index.html'
  'spring-panel/src/test/java/com/example/panel/controller/DialogApiControllerWebMvcTest.java'
  'spring-panel/src/test/java/com/example/panel/controller/DialogListControllerWebMvcTest.java'
  'spring-panel/src/test/java/com/example/panel/controller/DialogsControllerWebMvcTest.java'
  'spring-panel/src/test/java/com/example/panel/runtime/DialogListTimeZoneRenderingSourceContractTest.java'
  'spring-panel/src/test/java/com/example/panel/runtime/DialogsColumnWidthsUiSourceContractTest.java'
  'spring-panel/src/test/java/com/example/panel/service/DialogListPaginationPostgresqlTest.java'
  'spring-panel/src/test/java/com/example/panel/service/DialogListReadServiceTest.java'
  'spring-panel/src/test/java/com/example/panel/service/DialogSlaRuntimeServiceTest.java'
  'spring-panel/src/test/js/dialog-ai-query-requests-r54.test.js'
  'spring-panel/src/test/js/dialogs-pagination.test.js'
  'tools/dialogs-pagination-qa.cjs'
)
git status --short
if (@(git diff --cached --name-only).Count -ne 0) { throw 'В index уже есть изменения' }
$actual = @(@(git diff --name-only) + @(git ls-files --others --exclude-standard) | Sort-Object -Unique)
if (@(Compare-Object ($expected | Sort-Object) $actual).Count -ne 0) { throw 'Manifest изменился' }
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'cached diff check failed' }
git diff --cached --name-only
$staged = @(git diff --cached --name-only | Sort-Object)
if (@(Compare-Object ($expected | Sort-Object) $staged).Count -ne 0) { throw 'Index не совпадает с manifest' }
# После проверки staged списка выполнить вручную:
git commit -m 'perf(dialogs): перенести пагинацию и фильтры на сервер #ai'
if ($LASTEXITCODE -ne 0) { throw 'commit failed' }
git push
```
