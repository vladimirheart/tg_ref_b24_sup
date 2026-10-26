# Абзацы в технических подробностях и комментариях — R20

26.10.2026,01-281,🟣. Исправление применено в source и рабочем panel-web.
Git staging/commit/push не выполнялись.

## Что исправлено

- Технические подробности отображают исходные переносы, пустые строки, отступы
  и номера шагов. Тот же общий scoped multiline-класс применён к текстам событий
  хронологии и отображаемому авторскому summary.
- Для нового комментария вместо однострочного input — textarea с прежним
  incidentEventText, прежним API event_text и прежней серверной нормализацией.
  Поле расширено на строку сетки и допускает вертикальное изменение размера.
- Сохранено HTML-экранирование: XML/HTML, Windows paths и URL являются буквальным
  текстом. Markdown/HTML не исполняется. Страница не обращается по ссылке из
  инструкции и не скачивает установщик. Отдельной темы или inline-CSS нет.
- Cache versions app.css/incidents-workbench.js обновлены до R20.

## Причины и старые записи

Read-only проверка INC-238 до исправления:
description727символов/15LF, пользовательский event682символа/0LF.
Описание было сохранено правильно: браузер схлопывал whitespace в обычном dd.
Старый комментарий потерял переносы раньше — single-line input удалял их при
вставке ещё до POST. Сервер сохраняет внутренние LF/CRLF без схлопывания.

После Ctrl+F5 описание будет видно построчно. Новые комментарии сохраняют абзацы.
Исторический уже однострочный комментарий не переписывался: переносов в нём уже
нет, для нового форматированного комментария нужно вставить исходный текст заново.
Порядок/автор/содержимое истории не менялись. Это не backfill и не восстановление
утраченного форматирования из эвристик.

## Проверки

- 185 Java tests /45suites GREEN, failures/errors/skips=0. Новый real PostgreSQL
  test проверяет description и comment через существующий IncidentService,
  Hibernate/Spring Data transaction, reload новым сервисом, LF/CRLF, абзацы,
  отступы, Windows path и буквальный XML. Только изолированная схема.
  Hibernate-only fixture приведена к каноническим V22 TEXT колонкам для длинных
  description/event_text/payload_json; production schema не менялась.
- 52 JS unit tests +42 exact-ID assertions, runner reports54tests включая2wrapper
  suites. Новый R20 —6tests. Повторено в рабочем checkout после source apply.
- R12/R13/R19/R20 browser QA:40viewport/theme contexts,120PNG, light/dark,
  320/390/768/1440/1920. R20 отдельно:10contexts,20comment round trips,30PNG:
  инструкция владельца, visible paragraphs/indentation, literal XML/XSS,
  LF/CRLF, actual textarea→POST value, error preserves draft, reload,
  исторический flat event неизменен, no overflow/no external requests.
  Browser API doubles локальные; реальные business writes — только в test DB.
- Source audit74templates/47pages GREEN, diff check GREEN.
- Ранние RED нового теста были test harness defects: Java wildcard generic,
  Hibernate default varchar255 вместо канонического TEXT, предположение о
  нормализации CRLF в браузере. Исправлен harness, пример не укорачивался,
  исходные exact-ID/экранирование/layout контракты не ослаблялись.

QA: `C:/Users/SinicinVV/git_h/iguana-r20-sandbox/spring-panel/target/incidents-r20-qa/`.
Авторизованная проверка в рабочем браузере остаётся владельцу; учётные записи
и права агент не менял. После Ctrl+F5 раскрыть технические подробности INC-238
и добавить нужный реальный многострочный комментарий.

## Source/runtime checkpoint

HEAD=origin/main=remote `0f97f7d2a4b2962085b26dd2333cfe2721f399f8`, main clean.
R19 parent `3629b7220b68799406d322c3be712121c5ae8206`,24files сверены.
Sandbox от текущего HEAD; после GREEN guarded-applied13paths +2closeout,
итог15files. source_applied=true; staged/committed/pushed=false; task🟣.

Обновлён только panel-web, точечный R19 image +4UI entries:
JS/CSS/SCSS/template.2180entries до/после,2176hashes прежние.
Java classes, зависимости, migrations, API, monitoring и pending server changes
из main не включались и не изменялись.

- Tag: `iguana-panel:incidents-r20-20261026` (также `iguana-panel:local`).
- Image: `sha256:cbd27dc80c76dcb35fdb4e02af23d662d853ae1cbd0d6038cae9690189503a1d`.
- JAR: `701b17281bc0dfeabdcc9e7447408ca3665a81a098405bd3e89de48fae1e6a8a`.
- Container: `e67e16632b500da26fa0eea9c40d8aef8f9ef0eef9362290b472681421412685`.
- Started: `2026-10-26T11:20:33.997810469Z`, healthy.
- Rollback: `iguana-panel:rollback-incidents-r20-web-20261026` →
  `sha256:6b1884e0734188bc59488e5f9eba2d078f18dcfc2a4662168cd34b23d9dbc8c6`,
  JAR `3b2d5197a1dda652dc1cc66d111b33398512d9a920f8c7c40a1544c3c2a135ca`.
  Tag проверен, rollback не выполнялся и не объявляется испытанным.

После update сверены все18Compose containers: заменён лишь web, остальные17
сохранили IDs/images/start/env/mounts,4bot JVM PID/startTicks прежние.
Worker остаётся на R18, не перезапускался. Full web Env и4bind mounts прежние.
JS/CSS HTTP200 и hashes совпадают, анонимный /incidents требует вход302.
Миграции/backfill/config/flags/NetBox/ручные incident writes не выполнялись.

До/после совпали:
- Flyway V82/all history SHA `1af5845d5c7215dda2a10eff4e6b3838d1eaf5d14ef16f2b4e1e5797c7299369`;
- policy SHA `97d777785d26636b73a3ba798ada6e7976133416fa0da6156db154c2f95aa1d0`;
- INC-238 description+events SHA `4c44bbbdbeba18cee31ed19452388ad52d542fd6cab76884d469f949ad86720b`.

Ignored artifacts: `C:/Users/SinicinVV/git_h/iguana-r20-sandbox/spring-panel/target/incidents-r20-image/`:
before/after.json, jar-validation.json, release.ps1, prepare-image.ps1,
runtime-evidence.cjs, deploy.log, runtime-base.jar/app.jar. Секреты не сохранялись.
Предыдущие backup/rollback R19 не удалены.

## Точный manifest15files и команды владельцу

Блок агентом не запускался. Выполнить из корня после просмотра diff.

```powershell
$r20Files = @(
    'ai-context/changelog/2026-10-26_142401_incident-multiline-r20.md',
    'ai-context/rules/06-incident-readable-descriptions.md',
    'ai-context/tasks/task-details/01-281.md',
    'docs/ui/incident-multiline-r20.md',
    'spring-panel/src/main/resources/scss/app/_incident-workbench.scss',
    'spring-panel/src/main/resources/static/css/app.css',
    'spring-panel/src/main/resources/static/js/incidents-workbench.js',
    'spring-panel/src/main/resources/templates/incidents/index.html',
    'spring-panel/src/test/java/com/example/panel/service/IncidentMultilinePostgresqlTest.java',
    'spring-panel/src/test/java/com/example/panel/ui/NeutralOpsPageFamiliesWebMvcTest.java',
    'spring-panel/src/test/js/fixtures/incident-multiline-r20.cjs',
    'spring-panel/src/test/js/incident-descriptions-r13.test.js',
    'spring-panel/src/test/js/incident-multiline-r20.test.js',
    'tools/incident-multiline-qa.cjs',
    'tools/neutral-ops-source-audit.cjs'
)

git status --short
if (@(git diff --cached --name-only).Count -ne 0) { throw 'Сначала разберите существующий staging.' }
git add -- $r20Files
if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Staged diff check failed' }
$r20Staged = @(git diff --cached --name-only)
git diff --cached --name-only
if ($r20Staged.Count -ne $r20Files.Count -or @(Compare-Object $r20Files $r20Staged -CaseSensitive).Count -ne 0) { throw 'Staging manifest mismatch' }
git commit -m "fix(incidents): сохранить форматирование описаний и комментариев #ai"
if ($LASTEXITCODE -ne 0) { throw 'Commit failed' }
git push
if ($LASTEXITCODE -ne 0) { throw 'Push failed' }
```
