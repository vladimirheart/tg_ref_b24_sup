# 01-307 — Превью медиа, удаление файлов и редактирование текста

24.11.2026, 14:49 Europe/Moscow.

Промт пользователя:

> отлично.
> но отсутствует возможность удалить сохранённый файл в задаче.
> плюс в задаче, как в теле, так и в комментарии, если есть медиа, то нет превью, а при клике по нему - сразу скачивается файл, хотя хочется видеть просмотр как и в диалогах\паспортах обектов\профилях клиентов.
> а, да: нужно понимать откуда вложение - из чата или из самого тела задачи.
> плюс нужна возможность редактирования текста: в теле задачи сделать как и в базе знаний, а в комментариях как в диалогах.
> и ещё: для сжатия интерфейса, иконки вложения в комментарии переместить в поле ввода комментария

Значимые уточнения:

> Форматирование и изменение отправленных комментариев

Пользователь также попросил продолжить остановившуюся работу после появления токенов. Текущие изменения продолжены в существующем worktree.

Добавлены удаление сохраненного вложения из конкретного body/comment источника и подписи происхождения, отдельное скачивание, миниатюры и просмотр медиа. Фото открываются в общей галерее заметок с масштабом/перелистыванием/фокусом/Escape; поддержанные audio/video — в player. MIME определяется по содержимому, GET поддерживает Range, HTML не выдается inline. Удаление не теряет несохраненное описание и при ошибке оставляет состояние; общая ссылка удерживает бинарник, окончательная очистка запускается после commit.

Описание использует HTML-режим существующего редактора базы знаний, комментарий — компактную панель с форматированием выделения и блоками. Скрепка/Aa/отправка размещены внутри поля. Есть правка своего отправленного комментария с сохранением исходных медиа, отменой, retry и optimistic expectedHtml guard; сервер проверяет WRITE/task ownership/автора/CSRF, очищает измененный текст от активного HTML. Формат данных и БД не менялись. Для разбора HTML добавлен [jsoup 1.24.1](https://jsoup.org/download).

Области: TaskApiController/TaskAttachmentController, TaskRepository lock query, TaskAttachmentService, новые TaskAttachmentRemovalService/TaskCommentEditService, Maven dependency; task attachments/runtime/template/SCSS/generated CSS; расширение knowledge-note-content viewer API и markdown-visual-editor HTML initialization; storage/HTTP/content Java tests, оба task browser QA, task metadata. Cache markers tasks — r3. Старые avatar assertions сохраняют ожидания, но дожидаются асинхронного fallback. При повторном открытии pane scroll сбрасывается после появления окна.

Проверки: compile main/test и SCSS GREEN, 31 targeted Java tests / 6 suites GREEN с последующим targeted replay измененных HTTP/content suites. 5 Node checks GREEN. 20 browser scenarios GREEN: task layout/files/editor 6 + прежние tasks/profiles/dialogs 6 + общая note gallery 8. Реальные production assets и Thymeleaf markup, fixture API без рабочих mutations. Превью/zoom/источник, upload/drop/paste, удаления и ошибки без потери черновика, сохранение и reopening, форматирование/block menu, собственные edit/cancel/retry с медиа и без дублей, read-only, fixed composer, title scroll, отсутствие JS errors/missing assets/overflow. Галерея заметок сохранила pan/pinch/swipe, mixed upload и 24 скачивания без новых окон. Syntax/diff GREEN. PostgreSQL/MinIO integration не запускалась; storage bytes — actual local_fs, controllers/access — MockMvc/mocks.

Baseline/HEAD/origin_main: 14e05a97a4f23d2b2c34a5ff5a25eb013e8bde06. Sandbox: tg_ref_b24_sup-task-card-01307-r3. Exact source scope/hash проверены при переносе в основной checkout; staged/committed/pushed/deployment/migration/backfill: false. Статус 🟣, ручная проверка после rebuild/recreate panel-web. Completion alert предназначен для macOS и на Windows не запускался.
