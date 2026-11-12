# Документы и история версий в паспорте объекта — 01-284

- Время: 12 ноября 2026 года, 14:59:46 Europe/Moscow.
- Baseline / HEAD / локальный origin/main: `e240cc0fc3c1dc99846e7250117641f8acdab2af`.
- Статус: 🟣 — ожидается проверка пользователя.

Промт пользователя:

```text
в паспорты объектов добавить возможность загружать и изменять отельно взятые файлы\документы и тому подобное. например документы приёмки оборудования после его настройки с разным пулом инфы. или исторические изменения. поле нужно полусвободным но с превью, например фото или дока. Сделать вероятнее всего нужно отдельной вкладкой.
```

## Изменения

- Добавлена отдельная вкладка «Документы и история»: название, свободный тип, дата события, описание, до 30 дополнительных полей и необязательный файл до 50 МБ. Есть поиск, загрузка по 25 записей, просмотр, редактирование, замена файла, история версий и обратимое архивирование.
- PostgreSQL хранит независимые от JSON паспорта метаданные и снимки версий (миграция V92). MinIO/S3 использует новый namespace `passport_documents` через существующую storage-абстракцию. При редактировании прежние файлы сохраняются; rollback очищает только новый незафиксированный бинарный объект.
- Сохранение проверяет исходную версию под блокировкой строки. Устаревшая запись возвращает 409, интерфейс сохраняет набранные данные. Документ и версия проверяются в контексте паспорта до чтения бинарного файла. Физическое удаление паспорта с документами блокируется FK.
- Превью изображений/PDF определяется по байтам; DOCX отображается безопасным ограниченным текстом без внешних сущностей, распаковка ограничена. TXT/MD/CSV/LOG/JSON имеют текстовое превью. Остальные типы скачиваются. Загружаемая разметка не исполняется.
- Право `passport.documents` / `EDIT_PASSPORT_DOCUMENTS` добавлено в роль и индивидуальные права. Просмотр остаётся за PAGE_OBJECT_PASSPORTS, изменение требует обоих прав. Индивидуальный запрет имеет приоритет.
- Для типизированного document-preview разрешён SAMEORIGIN вместо конфликтующего DENY. Обработка scoped-ответа заменяет заголовок, не добавляя противоречивый второй; обычные ответы и ошибки сохраняют DENY. Версии JS обновлены.
- Обновлены документация хранения, матрица прав, task-list и карточка задачи.

## Изменённые области

- Новые `passports/ObjectPassportDocument{,Repository,Service,Storage}.java`, `passports/api/ObjectPassportDocumentController.java`.
- `security/PanelAccessPolicy.java`, `SecurityConfig.java`, `SecurityHeadersFilter.java`, `controller/AuthManagementApiController.java`, `UserPermissionsController.java`, `storage/AttachmentObjectStorageService.java`.
- Миграция `V92__object_passport_documents.sql`.
- `templates/passports/detail.html`, новый `passports/fragments/detail-documents.html`, новый `static/css/passport-documents.css`, новый `static/js/passport-documents-runtime.js`, `passport-detail-page-runtime.js`, `user-permissions.js`, версии скриптов в шаблонах настроек/пользователей.
- Новые Storage/Persistence/Controller тесты документов, существующие source contract и PanelAccessPolicyTest, `tools/passport-documents-qa.cjs`.
- `docs/IGUANA_DATA_LIFECYCLE_AND_STORAGE_STRATEGY.md`, `docs/settings-access-audit-2026-11-12.md`, `ai-context/tasks/task-list.md`, `task-details/01-284.md`, эта запись.

## Проверки

- Компиляция — exit 0. Итоговые Surefire XML 13 целевых наборов: 335 тестов, 0 failures/errors/skipped. Последний security-прогон 4 наборов/112 тестов — exit 0.
- Реальный одноразовый PostgreSQL 16.16: V92, файлы разных версий, metadata-only update, конфликт/конкуренция, rollback, отдельный паспорт, FK, поиск и пагинация — GREEN.
- Playwright на 320/1440 px: viewer/editor, загрузка, свободные поля, замена, PDF/изображения/текст DOCX, скачивание старого файла с проверкой байтов, 409 без потери формы, архив/возврат — GREEN. PDF дополнительно проверен визуально.
- `git diff --check`, синтаксис JS — GREEN. Генерируемые Sass CSS с изменениями только окончаний строк восстановлены; новый CSS документов сохранён.

## Фактические границы

source_applied=true; staged=false; committed=false; pushed=false; deployment=false. Отдельный source sandbox не создавался. Рабочие данные, роли, провайдеры и NetBox не менялись; V92 на рабочем экземпляре не применялась.

Docker недоступен. Тестовый PostgreSQL создан под игнорируемым target с loopback/nonce owner-check, системной установки/службы нет. Кластер остановлен; скачанный установочный ZIP удалён. Источник тестовых бинарных файлов: [EDB PostgreSQL binaries](https://www.enterprisedb.com/download-postgresql-binaries).

Для применения нужна пересборка панели, штатное применение миграции V92 и назначение права изменения документов обычным редакторам, у которых нет полного edit wildcard. Файлы и метаданные нового namespace нужно включать в резервные копии.
