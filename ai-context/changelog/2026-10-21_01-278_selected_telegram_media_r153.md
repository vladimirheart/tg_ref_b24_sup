# 01-278 — вложения Telegram выбранных вопросников (R153)

Дата: 2026-10-21, Europe/Moscow. Общий статус задачи 🟡.

Промт пользователя:

> продолжай

Сохраняются ранее заданные размер пакета и граница среды:

> давай дальше большим пакетом

> продолжай. возьми более широкий пакет. напоминаю, что рабочий экземпляр проекта на другой машине.
> коммит и пуш разрешаю сразу после внечения правок без моего подтверждения - разрешение только в течении этих суток

Действует разрешение commit/push на 2026-10-21 Europe/Moscow, до
2026-10-22 00:00 +03:00. Оно заменяет default manual git flow только на эти сутки;
не разрешает rollout. Здесь source/tests, рабочий экземпляр — на другой машине.

- Telegram v4 выбранных BUSINESS/GENERAL вопросников принимает document, photo,
  video, voice, audio, animation, sticker и video_note как свободный ответ до
  регистрации. Caption становится ответом, при его отсутствии используется
  явное описание вложения. Preset/select требуют текстового выбора; media не
  подменяет вариант. До выбора владельца вложение не скачивается/не сохраняется:
  клиенту предлагается отправить его повторно после выбора. Старые v1–v3 остаются
  text-only; их сессии не переводятся в v4.
- До скачивания повторно проверяются текущие selection/catalog и поддержка media
  writer. V4 forStart требует прежний exact ACK=4 и отдельный exact header
  X-Iguana-Questionnaire-Media-Version=1; choices и selection выставляют его только
  после authorization/feature gates. Отсутствие/другая версия закрывает start или
  revalidation без downgrade. Поэтому новые SDK/боты требуют backend R153+;
  одной совместимости с v4 R152 недостаточно. Старые v4 consumers игнорируют
  дополнительный header; payload/catalog schema не менялись.
- Бинарные данные: 1 байт–10 МиБ на файл, до 10 файлов в истории опроса.
  Фактический stream ограничен независимо от Telegram file_size. Ключ
  questionnaires/telegram/{channel}/{business-ID|general}/{user}/{catalogSHA}/
  {providerMessageId}/{bytesSHA}.{extension} связывает файл с canonical owner,
  клиентом и снимком; filename не определяет storage path. Original name очищается
  от пути/control chars и ограничен 255 символами. Выбирается одна rendition photo.
  Повторы/CAS используют тот же content-addressed key; локальный файл публикуется
  после полной записи, S3 использует общий attachments domain/prefix.
- Media history, answers, accepted IDs и frozen publication key сохраняются до
  prompt/provider send. JSON restore проверяет owner/channel/catalog/provider key
  и предел файлов. Duplicate update, CAS retry, prompt/send failure и queue retry
  не меняют выбранного владельца и не добавляют второй history item. Pending
  publication повторяет точный сохранённый command без нового скачивания и
  recapture; /start остаётся явным новым выбором.
- На panel перед первым новым root/child INSERT проверяются v4 version, Telegram,
  canonical channel/business-or-GENERAL/user/catalog/provider identity, допустимый
  тип/имя, уникальность и лимит файлов. Читаются реальные shared storage bytes;
  missing/empty/oversize, чужой path/URL/traversal, изменённый SHA или повтор
  отклоняются. В одной ingestion-транзакции сохраняются chat_history с точным
  ticket_owner_user_id/channel и metadata size/content_hash/storage_key.
  Прежний metadata SQL/opaque ticket-ID contract сохранён. Existing-root frozen
  replay не перечитывает текущий каталог или файлы и не создаёт children повторно.
- BUSINESS/GENERAL scoped attachment APIs теперь читают новый namespace по
  canonical public ticket/history locator, текущему READ и frozen root proof.
  Сверяются channel/user/business-or-GENERAL/catalog/provider и metadata hash/size;
  текущие bindings/каталог не определяют владельца старого обращения. При download
  байты повторно сверяются с SHA/size, затем повторно проверяются READ/reference.
  Старые direct/key/path endpoints закрыты для нового namespace, включая Windows
  slash/case aliases. HTML/SVG и остальные документы выдаются как attachment с
  application/octet-stream, no-store и nosniff через scoped APIs.
- Дополнительные PostgreSQL/read/controller checks покрывают отзыв READ,
  повреждение provider/channel/hash/size metadata, неоднозначные metadata,
  изменённые bytes и попытку обхода через legacy URLs. Старые ticket-directory
  readers и opaque ticket IDs сохранены и проверены отдельно.
- Validation: 845 Java tests / 84 XML, failures/errors/skips=0;
  backend 233/15, полный fleet 612/69. Проверены все 8 типов,
  BUSINESS/GENERAL, restore, duplicate, CAS, failed prompt/download/storage/queue,
  actual-byte limit, signed capability, PostgreSQL owner/metadata/rollback/replay.
  Дополнительно сохранён контракт legacy exact ticket-ID metadata и HTTP READ.
  Начальные 278 backend checks (135 writer cases) прошли; финальный batch
  повторяет 33 новых media write/read cases, остальные 116 writer cases не
  повторялись. Начальный XML сохранён отдельно, в итоговый счётчик не добавляется. Тесты используют
  isolated PostgreSQL, local_fs и direct JSON/CAS; provider downloads/sends mocked.
  Live S3/Redis/provider/browser/proxy acceptance не выполнялась. JavaScript source
  не менялся, Node suites отдельно не запускались.
- Detached sandbox .tmp/01-278-r153; exact 26 files/content guards перед source
  apply. Baseline 554ea0288fbe65fd2e7e8cf9cdf004699c4b4acf, parent
  51e89ff2e19df855f397448cc9569fe7a37a27c7; фактические 27 файлов R152 проверены.
  Generated Sass CSS исключён, source-схема остаётся V75, новой migration нет.
- Read operator v1 остановлен JS parser до transformation; v2 остановлен exact
  anchor guard до изменения reader files. Артефакты сохранены; v3 использует
  проверенные semantic anchors. Это не RED тестов и не source apply в main;
  emitted Java/read SQL проходят полный compile и targeted checks перед apply.
- Первый read batch: 79 checks, одна ошибка прежнего BUSINESS read test с
  минимальной схемой без ticket.channel_id. Новый proof SELECT оказался связан
  с legacy projection. V4 исправления отделяет proof-only SQL, сохраняет общий
  BUSINESS policy predicate и прежний projection для legacy; GENERAL proof
  вынесен в отдельный guarded lookup. Недоступная proof schema не разрешает
  новый namespace. Существующие тесты не ослаблялись; повторяются read batch,
  33 media write/read cases и прежний HTTP READ suite. RED log сохранён отдельно.
- Рабочий Docker/проект находятся на другой машине и в другой сессии агента.
  Рабочие revision/DB/flags неизвестны; deploy/restart, рабочие migrations/backfill,
  flags/config/provider sends здесь не выполнялись. Перед выпуском нужны совместимые
  backend/SDK/боты и один shared storage domain/bucket/prefix либо общий volume.
- Общая задача 🟡. Дальше: VK selected media, Telegram followup media, scoped
  edit/feedback, media deletion/retention/reconciliation (включая orphan objects
  отменённых/конфликтных черновиков), live fleet/provider/Redis/S3/proxy acceptance,
  прочие S3–S5/S2B/S6 gates. Одновременные бизнес-обращения не добавлялись.

Commit/push выполняются отдельным оператором после GREEN, exact file/content
guards, actual remote и проверки срока разрешения. Исторические changelog не менялись.
