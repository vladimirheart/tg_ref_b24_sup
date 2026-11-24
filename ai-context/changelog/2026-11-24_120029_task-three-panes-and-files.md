# 01-307 — Три панели задачи, компактный чат и файлы

24.11.2026, 12:00 Europe/Moscow.

Промт пользователя:

> уже лучше. но нужно подправить
>
> 1. разделить окно задачи на три части:
>    - название задачи и его описание, а так-же все вложения отображать в правой части окна задачи
>    - посередине чат задачи. но поле вводе комментария должно быть всегда внизу.
>    - справа оставить как есть, но перенести сюда историю
> 2. комментарии подсжать - слишком много места занимает и шумно
> 3. куда-то пропала возможность прикладывать вложения - ни из файла ни drug&drop. работает только вставка скринов из буфера

Уточнение пользователя на вопрос о порядке колонок:

> Да: описание слева, чат в центре, параметры справа

Карточка разделена на описание/вложения слева, чат посередине и параметры/историю справа. Независимая прокрутка и закрепленный composer; на узких экранах переключение разделов. Комментарии уплотнены, медиа представлены компактными ссылками. Общая галерея включает вложения описания и комментариев, поддерживает скачивание и просмотр изображений.

Восстановлены chooser, drag&drop и clipboard для файлов задачи и комментария. Очередь сохраняется при ошибке, возможны файл без текста и создание задачи с файлами. До 10 файлов, 20 МБ каждый, 40 МБ на отправку. Backend использует штатный object storage и HTML-ссылки в существующих task/comment строках; без схемных миграций. Добавлены READ/WRITE checks, проверка типов изображений по сигнатуре, безопасная выдача остальных типов, очистка частичных uploads/rollback.

Области: TaskApiController и новый TaskAttachmentController/TaskAttachmentService, task_attachments domain в AttachmentObjectStorageService, tasks template/runtime и новый task-attachments runtime, task-card/image-viewer SCSS и generated app.css, Java HTTP/storage tests, constructor fixture SharedWorkMainHttpPostgresTest, старый и новый browser QA, task metadata. Cache markers обновлены.

Проверки: full main/test compile и SCSS GREEN; 17 Java tests / 5 suites GREEN. 12 browser scenarios GREEN (2 набора × light/dark × 320/768/1440), real rendered Thymeleaf + production assets + fixture APIs. Проверены chooser/drop/paste, retry, bytes download и reopening, file-only comment/new task, независимый scroll и pinned composer, history/read-only, прежние edit/save/people/profile/dialog regressions. Ошибки JS, missing assets и horizontal overflow: 0. Syntax/diff checks GREEN. PostgreSQL/Docker/S3 integration и работающий сервер не проверялись; storage unit tests используют local_fs и настоящий roundtrip байтов.

Baseline/HEAD/origin_main: a07a85f7e19eceffe93e39971e6505f151906c7f. Detached worktree: tg_ref_b24_sup-task-card-01307-r2. После GREEN перенос exact source scope в основной checkout с hash-проверкой. Staged/committed/pushed/deployment/migration/backfill: false. Статус 🟣, ожидается ручная проверка после rebuild/recreate panel-web. Completion alert предназначен для macOS и на Windows не запускался.
