# 01-298: исправление фотопросмотра и вложения заметок

Время: 2026-11-24 09:54:51 Europe/Moscow.
Baseline HEAD/origin/main/remote main: `1b462fe304cb04a82aed157daf2eacc010746498`.
Статус задачи: 🟣, требуется ручная проверка пользователя.

## Фактические изменения

- Исправлена причина регрессии одиночного фото: при скрытых стрелках CSS Grid
  автоматически помещал stage в первую колонку шириной 44px. Stage теперь явно
  занимает центральную колонку, а при одном фото — всю ширину viewport.
- Вместо flex sizing изображение размещается на canvas с явными размерами,
  рассчитанными из naturalWidth/naturalHeight и масштаба. В режиме «Уместить» оно
  центрируется; увеличение и размер 100% сохраняют доступ ко всем краям через
  прокрутку/перетаскивание. ResizeObserver пересчитывает размер при изменении окна.
- Добавлены кнопки −/+, ввод процентов до 800%, 100%, «Уместить», масштабирование
  колесом и двумя пальцами, перемещение мышью/пальцем и отдельная кнопка «Скачать».
  Сохранены группировка по заметке, стрелки/клавиши, свайпы и возврат фокуса.
- Редактор принимает несколько вложений через «Добавить файлы» и вставку из буфера.
  Raster-фото по-прежнему используют `![Фото](path)`, документы/архивы — обычные
  `[имя файла](path)`. Документы и архивы показываются ссылками для скачивания,
  не попадают в галерею и не встраиваются как активное содержимое.
- Новый POST `/api/attachments/knowledge-base/note-files` сохраняет назначение
  в существующей knowledge_article_files. Старый note-photos совместим. Лимиты:
  фото PNG/JPEG/GIF/WebP до 10 МБ, остальные файлы до 50 МБ, общий multipart limit
  может дополнительно задаваться конфигурацией приложения. Миграций нет.
- Чтение требует живую привязанную учётную запись и PAGE_KNOWLEDGE_BASE; ACL
  статей всегда имеет приоритет. Exact-reference fallback понимает сохранённые
  фото и обычные ссылки на файлы. Upload остаётся под CSRF; attachment disposition
  при скачивании не меняется, внешние URLs/HTML не исполняются.
- Обновлены shared SCSS/app.css, cache versions, Node/Java contracts и QA tools.
  Точный список 23 файлов и ручные команды фиксации — в task-details/01-298.md.

## Проверки и границы

- Detached sandbox от указанного baseline. Repository Maven wrapper: полный
  main/test compile и 44 targeted Java tests / 7 suites GREEN, failures/errors/skips=0.
- PostgreSQL — отдельный одноразовый loopback cluster с nonce guard, не рабочая БД.
  Реальные upload/download HTTP, точные байты ZIP/PDF/DOCX, сохранение/пересоздание
  сервисов, ACL статей, blocked/missing-page actors, traversal, oversize, CSRF.
- Node: 13 tests GREEN; syntax production/QA JS GREEN. Source audit: 79 templates /
  50 pages GREEN; git diff --check GREEN. Sass скомпилирован Maven.
- Edge/Chromium CDP: 8 сценариев 320/390/1024/1440 × light/dark GREEN. Проверены
  геометрия одного фото, landscape/portrait, 100%/200%, +/−, wheel, pan, pinch,
  swipe, смешанная загрузка, ссылки документов, gallery grouping, Escape/Tab,
  Bootstrap/native dialog, ошибка GET и caption XSS. 24 downloads с проверкой байтов,
  newWindows=0, pageerror=0. DOM/HTTP endpoints в browser QA — loopback fixtures;
  backend отдельно проверен реальными PostgreSQL HTTP tests.
- Предыдущие browser RED разобраны: ожидание двух RAF для ResizeObserver и
  изоляция touch state в отдельных CDP targets. Проверки геометрии/жестов не ослаблены.
- Известный baseline RED в DialogKnowledgeNotesSourceContractTest: устаревшая
  ожидаемая версия dialogs-details-runtime.js. Assertion не менялся, suite не включён
  в 44 targeted tests. Обновлена только версия общего knowledge-note-content.js.
- После Maven менялись только QA harness и тексты upload status/comment; повторный
  Node/source audit GREEN. Full Maven/browser rerun для этих текстов не выполнялся.

Для использования нужен rebuild и пересоздание panel-web. Commit/push/deploy,
рабочая БД, секреты и файлы пользователей в этой работе не изменялись.

## Промт пользователя (дословно)

> что-то всё поплыло...
> и если жму "исходный размер" происходит ещё хуже.
> да, и нет возможности скачать или поиграть с масштабом картинки, ведь я просил полноценный просмотр фото.
> и ещё: у заметки может быть не только фото, но и, например, архив или документ

Пользователь приложил два скриншота: фото сжато слева, режим исходного размера
показывает только узкую полосу изображения.
