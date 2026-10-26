# Сохранение абзацев в инцидентах — R20

Дата:26.10.2026 Europe/Moscow; checkpoint 2026-10-26_142401. Задача01-281,🟣.

## Промт пользователя

```text
при раскрытии "Технические подробности" форматирование текста пропало. такое-же поведение и при добавлении комментария в хронологию.
изначальный текст:
0\. скачать актуальную на текущий момент версию пос-а
[https://102922.selcdn.ru/pos_installer/99/Setup.iikoCard5.POS.exe](https://102922.selcdn.ru/pos_installer/99/Setup.iikoCard5.POS.exe)

1\. удалить старую версию.
&#x20;если запросит, то ребутнуться.

2\. поставить актуальную версию по ссылке
если запросит, то ребутнуться.

3\. остановить службу. если служба не стопится, то переходим дальше:

4\. в файле C:\Program Files\iiko\iikoCard5POS\iikoCard5.Pos.Host.exe.config изменить ключ с false на true \<add key="ForceHttpSync" value="true" /> находится примерно на 369-й строке после "\<!--Force HTTP regardless of the server UseHttpSync setting, without RabbitMQ fallback.-->"

5\. запустить службу. если служба не была остановлена в п.3, то ребутнуть машину
```

Приложенные скриншоты показывали слитный текст инструкции в INC-238.

## Фактические изменения

Общий scoped multiline-класс для исходных technical title/summary/description,
авторского summary и event_text: pre-wrap/anywhere, без HTML/Markdown execution.
Однострочный input комментария заменён на textarea с прежним ID/API, шириной
строки и вертикальным resize. Cache versions R20, source/style contracts обновлены.
Никакого автоматического переписывания старых событий.

Read-only рабочая проверка: description727символов/15LF; user event682/0LF.
Описание потеряло переносы только визуально, комментарий — ещё в single-line
input до POST. Серверный код и schema не менялись. Ранее утраченные переносы
в истории автоматически не восстанавливались, исходные данные сохранены.

185Java/45suites,52JS+42exact-ID assertions,40browser contexts/120PNG GREEN;
R20 —10contexts/20roundtrips/30PNG, LF/CRLF, error draft, reload, XSS,
literal XML/Windows path, no overflow/external calls. Audit74/47 GREEN.
PostgreSQL persistence проверен в изолированной схеме через настоящий сервис/JPA;
длинные поля fixture соответствуют существующему V22 TEXT, не укорочены.

Baseline0f97f7d2a4b2962085b26dd2333cfe2721f399f8=origin=remote, main был clean.
Guarded13source paths +2metadata, итог15files. Точечное обновление web:
R19+4UIresources,2180entries/2176unchanged. Backend/dependencies/migrations
и pending main server changes не развёртывались.

Рабочий web healthy на incidents-r20-20261026:
imagecbd27dc80c76dcb35fdb4e02af23d662d853ae1cbd0d6038cae9690189503a1d,
JAR701b17281bc0dfeabdcc9e7447408ca3665a81a098405bd3e89de48fae1e6a8a.
Все17прочих Compose containers,4bot JVM, env/mounts, V82/history, policy и
INC-238description/events fingerprints прежние. Rollback R20web→R19 сохранён,
откат не выполнялся. Без migration/backfill/config/flags/external/manual data writes.
Авторизованная production UI acceptance владельцу послеCtrl+F5.

## Файлы

- `ai-context/changelog/2026-10-26_142401_incident-multiline-r20.md`
- `ai-context/rules/06-incident-readable-descriptions.md`
- `ai-context/tasks/task-details/01-281.md`
- `docs/ui/incident-multiline-r20.md`
- `spring-panel/src/main/resources/scss/app/_incident-workbench.scss`
- `spring-panel/src/main/resources/static/css/app.css`
- `spring-panel/src/main/resources/static/js/incidents-workbench.js`
- `spring-panel/src/main/resources/templates/incidents/index.html`
- `spring-panel/src/test/java/com/example/panel/service/IncidentMultilinePostgresqlTest.java`
- `spring-panel/src/test/java/com/example/panel/ui/NeutralOpsPageFamiliesWebMvcTest.java`
- `spring-panel/src/test/js/fixtures/incident-multiline-r20.cjs`
- `spring-panel/src/test/js/incident-descriptions-r13.test.js`
- `spring-panel/src/test/js/incident-multiline-r20.test.js`
- `tools/incident-multiline-qa.cjs`
- `tools/neutral-ops-source-audit.cjs`

Отчёт и точные staging/commit/push commands: docs/ui/incident-multiline-r20.md.
source_applied=true; deployment=true; staged/committed/pushed=false;
acceptance owner pending,🟣.
