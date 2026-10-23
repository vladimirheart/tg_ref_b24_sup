# Neutral Ops R10 — главная, кнопки диалогов и заметки с фото

23.10.2026 · задача 01-281 · `🟣`, ожидается ручная проверка новых изменений.

## Что изменено

`/` теперь отдельная главная «Пульс панели», а не второй список диалогов.
`/dialogs` и точные query/ticket routes сохранены. В сайдбар добавлена главная,
ссылка «Диалоги» ведёт на `/dialogs`.

На главной:

- Обращения за сегодняшний день, активная очередь, активные инциденты,
  число запущенных по runtime-снимкам ботов и включённых каналов.
- Почасовой график текущего дня, полосы нагрузки по каналам и incident summary.
- Карточки-переходы в разделы, с теми же permission flags, что у сайдбара.
- Обновление раз в минуту, при возвращении во вкладку и кнопкой; перерисовка
  при смене темы. Ошибка обновления показывает «нет свежих данных», не нули.

Период — текущий день **в серверном часовом поясе**, который явно указан в
шапке (в проверенном runtime UTC). Считаются **обращения**, а не сообщения
переписки. Инциденты показывают активное состояние сейчас и отдельно итоги
за последние 24 часа. «Подключения» означают включённые каналы, не TCP/SSE
сессии. Статусы ботов — последние сохранённые runtime-сигналы, не новая
активная проверка процессов; неизвестные выделены отдельно от остановленных.

Новый read-only endpoint `GET /api/home/pulse` проверяет page authorities
до обращения к каждому источнику. Dialog stats используют тот же
operator-aware read facade и существующую аналитику, что отчёты.
Incident stats требуют прежних PAGE_DIALOGS/TASKS/OBJECT_PASSPORTS;
runtime counts — PAGE_CHANNELS либо PAGE_ANALYTICS. Нет новых raw SQL,
публичных клиентских данных или выдачи credentials. Endpoint не вызывает
channel discovery, Telegram refresh или lifecycle-команды. Общий sidebar
runtime остаётся прежним, включая его существующие запросы.

Кнопка открытия расположена выше меню действий: 24×28 px против 24×18 px.
На coarse pointer обе области остаются 44×44 px.

## Заметки и фото

Блок «Заметки» закрыт по умолчанию и снова сворачивается при переходе к другому
диалогу. Поиск — по заголовку и **всему** тексту, не только первым 800 символам;
без учёта регистра и ё/е. Сохранён порядок локационных заметок. Запоздавшие
ответы прежнего диалога не подменяют текущий список.

Фотографии добавляются **в редакторе заметки**: кнопка «Добавить фото»,
несколько файлов либо вставка изображения из буфера. После загрузки есть
предпросмотр; нужно нажать «Сохранить». Затем фото видны и в заметке диалога.
Открыть редактор можно существующей ссылкой на заголовке заметки.

Используется существующий защищённый KB upload/download API с CSRF.
Фото сохраняется в прежнем object storage, ссылка — в существующем `body`
как `![Фото](/api/attachments/knowledge-base/...png)`.
Новая схема/таблица/миграция не требуется. Поддержаны PNG/JPEG/GIF/WebP,
до 10 МБ на файл в UI. SVG, произвольный HTML и внешние image URLs не
интерпретируются. Текст создаётся через textContent, local paths валидируются;
в одной отрисовке показано до восьми фото. Картинка доступна только с
PAGE_KNOWLEDGE_BASE. Простые старые заметки остаются простым текстом.

Ссылки в body являются обычным текстом: убрать фотографию из заметки можно,
удалив её строку и сохранив. Это не удаляет общий объект из хранилища —
автоматической очистки чужих/переиспользуемых вложений нет. Незакреплённые
загрузки следуют прежнему жизненному циклу KB attachments.

На главной и в note editor подключён существующий Bootstrap bundle для
служебных модалок сайдбара; открытие смены пароля проверено без её отправки.

## Проверка

Исходный чистый baseline `HEAD=origin/main`:
`d9c2565efb22668e7ede619d6ac690a3aa265c44`.
Изолированный worktree:
`C:/Users/SinicinVV/AppData/Local/Temp/iguana-ui-r10-20261023`.

- 191 выбранный Java test, package и Sass generation — успешны,
  0 failures/errors/skipped. После добавления Bootstrap повторены 9
  непосредственно затронутых MVC/pulse/photo cases — успешны.
- 17 JS checks — успешны, включая прежние точные query contracts и новые
  проверки поиска, local image references и почасовой серии.
- Реальная изолированная PostgreSQL через Testcontainers + временный media
  каталог: save/reload заметки, location link, photo reference и байты/MIME/size
  сохранены после пересоздания сервисов; upload/download без KB права запрещены.
  Рабочие БД и object storage для этого теста не использовались.
- 470 снимков 47 MVC-rendered страниц, light/dark ×
  320/390/768/1440/1920 px; 24 modal checks. Нет найденных overflow,
  missing tokens, low-contrast headline/KPI или отдельных visual overrides.
- 72 дополнительных palette/theme/touch состояния, 63 modal focus checks.
- 10 финальных заполненных home/photo/notes сценариев, 30 PNG:
  счётчики и график, ошибка API без ложных нулей, настоящий multipart upload,
  целостность фото, paste, отказ SVG/403 без порчи текста, submit/reload,
  поиск дальше preview limit, сворачивание и race при смене ticket.
  В каждом — открытие/закрытие модалки аккаунта на главной и в note editor.
- 10 повторных заполненных scenarios R9, 70 PNG: новые 28/18 px и touch44,
  внешние подписи bars, переписка и дерево локаций не регрессировали.
- Source audit: 74 templates, 47 pages, 27 fragments. Только три затронутые
  страницы используют app.css R10; остальные сохраняют R9, поскольку их
  component styles не изменялись. Раздельные inline-темы/JS stylesheet injection
  не добавлялись. `git diff --check` успешен.
- Все 26 source/test/tool paths сверены с validated sandbox с нормализацией LF;
  app.css штатно сгенерирован повторно в основном checkout. Нормализация Java
  для существующих source contracts проводилась только в sandbox, массовые
  EOL-only changes не переносились.

Найденные RED разобраны, тесты не отключались: исправлены null-safe conditions
home fixture, устаревшие cache-version assertions; старый test об обязательном
прозрачном фоне ссылки заменён проверкой принятой общей neutral-поверхности
при сохранении SLA assertions. В browser harness отдельно учтены штатная
resize-ручка, CRLF form submission и offscreen мобильный sidebar.

Все browser read/write endpoints — локальные fixtures; внешний трафик
заблокирован. Это не authenticated acceptance рабочей БД/MinIO и не проверка
реальной отправки сообщений или смены пароля.

Снимки:
[главная](../../spring-panel/target/neutral-ops-r10-acceptance/home-dark-1440.png),
[заметка с фото](../../spring-panel/target/neutral-ops-r10-acceptance/note-photo-dark-1440.png),
[поиск в диалоге](../../spring-panel/target/neutral-ops-r10-acceptance/dialog-notes-dark-1440.png).

Артефакты в `spring-panel/target/neutral-ops-r10-{acceptance,qa,states,feedback,style-audit}/`
не коммитятся. Воспроизведение после MVC capture:

```powershell
node tools/neutral-ops-source-audit.cjs
node tools/neutral-ops-home-notes-qa.cjs
node tools/neutral-ops-feedback-qa.cjs
node tools/neutral-ops-qa.cjs
node tools/neutral-ops-qa.cjs --states
```

Нужен Playwright либо PLAYWRIGHT_MODULE; Java — repository Maven wrapper.
Ключевые test classes: HomeControllerWebMvcTest, HomePulseTest,
KnowledgeNotePhotoPersistencePostgresqlTest, NeutralOpsPageFamiliesWebMvcTest.

## Runtime checkpoint этой сессии

Обновлён только локальный `panel-web`, состояние healthy:

- final image `iguana-panel:neutral-ops-r10-20261023-v2`;
- image ID `sha256:c36c7f2b8667ca6a8caa9619710f6a151158fdbcab61986e90c82b4aee1cc43d`;
- JAR SHA-256 `e27bce20bce2b67560786a03a8c51e0737a239590a7375edf9350477d5c798f0`;
- rollback `iguana-panel:rollback-neutral-ops-r10-20261023` — прежний R9:
  `sha256:445d26e30d138b6e6132f7697654b9a9b77afffbc9b23a6058d6201aa46127c3`.

Это точечный overlay: 9 UI resources и 2 controller classes, 2022 JAR entries;
hash каждого прочего entry сохранён. Исходник DialogsController до изменения
совпадал с прежним runtime baseline; runtime templates сверены с d9c baseline.
Новые несвязанные backend commits мультибизнеса/уведомлений не поставлялись.
Промежуточный R10 image также запускался; v2 добавляет Bootstrap dependencies.
Это не полная сборка текущего HEAD.

Environment и четыре bind mounts сверены; alias `iguana-panel:local`
указывает на final image. Rollback tags сохранены, откат не выполнялся.
Workers остались на `sha256:97ccb6fc7a34ae77af2eb7f264c49d8665393571060ad0fa46664ea528a9ce62`
со временем запуска 22.10.2026 12:43:42 UTC; инфраструктура/db-migrate не
перезапускались. Flyway до/после — 81. Migrations/backfill/рабочие media uploads/
внешние business writes не выполнялись. HTTP asset SHA и runtime JAR SHA
проверены; анонимный доступ к главной и pulse API перенаправляется на login.

Пакетирование/deploy scripts — `spring-panel/target/neutral-ops-r10-image/`.
Требуется ручная проверка владельцем после Ctrl+F5, включая реальные доступы
и загрузку фото в своём рабочем контуре. Staging/commit/push/приёмка не выполнялись.

## Файлы и фиксация

Точный scope — 29 файлов. Не использовать `git add .`.
Перед staging проверьте, что index не содержит чужих изменений.

```powershell
$uiFiles = @(
  'ai-context/changelog/2026-10-23_104736_home-pulse-notes-r10.md'
  'ai-context/tasks/task-details/01-281.md'
  'docs/ui/neutral-ops-home-notes-r10.md'
  'spring-panel/src/main/java/com/example/panel/controller/DialogsController.java'
  'spring-panel/src/main/java/com/example/panel/controller/HomeController.java'
  'spring-panel/src/main/resources/scss/app.scss'
  'spring-panel/src/main/resources/scss/app/_home.scss'
  'spring-panel/src/main/resources/static/css/app.css'
  'spring-panel/src/main/resources/static/js/dialogs-details-runtime.js'
  'spring-panel/src/main/resources/static/js/home-pulse.js'
  'spring-panel/src/main/resources/static/js/knowledge-note-content.js'
  'spring-panel/src/main/resources/static/js/knowledge-notes.js'
  'spring-panel/src/main/resources/templates/dialogs/index.html'
  'spring-panel/src/main/resources/templates/fragments/navbar.html'
  'spring-panel/src/main/resources/templates/home/index.html'
  'spring-panel/src/main/resources/templates/knowledge/note-editor.html'
  'spring-panel/src/test/java/com/example/panel/controller/HomeControllerWebMvcTest.java'
  'spring-panel/src/test/java/com/example/panel/controller/HomePulseTest.java'
  'spring-panel/src/test/java/com/example/panel/runtime/DialogKnowledgeNotesSourceContractTest.java'
  'spring-panel/src/test/java/com/example/panel/runtime/DialogsColumnWidthsUiSourceContractTest.java'
  'spring-panel/src/test/java/com/example/panel/runtime/UiNavigationDialogsSourceContractTest.java'
  'spring-panel/src/test/java/com/example/panel/storage/KnowledgeNotePhotoPersistencePostgresqlTest.java'
  'spring-panel/src/test/java/com/example/panel/ui/NeutralOpsPageFamiliesWebMvcTest.java'
  'spring-panel/src/test/js/dialog-details-query-api-r50.test.js'
  'spring-panel/src/test/js/knowledge-note-content.test.cjs'
  'tools/neutral-ops-feedback-qa.cjs'
  'tools/neutral-ops-home-notes-qa.cjs'
  'tools/neutral-ops-qa.cjs'
  'tools/neutral-ops-source-audit.cjs'
)
git status --short
if (@(git diff --cached --name-only).Count -ne 0) { throw 'Сначала проверьте существующий index' }
git add -- $uiFiles
if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'staged diff check failed' }
git diff --cached --name-only
$actual = @(git -c core.quotePath=false diff --cached --name-only)
if ($actual.Count -ne $uiFiles.Count -or @(Compare-Object $actual $uiFiles).Count -ne 0) {
    throw 'Staged paths отличаются от ожидаемого scope'
}
git commit -m 'feat(ui): добавить пульс панели и фотографии в заметках #ai'
# Только после успешного commit:
git push
```
