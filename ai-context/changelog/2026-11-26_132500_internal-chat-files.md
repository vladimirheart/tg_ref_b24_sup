# 01-324 — Файлы во внутренней переписке

Дата: 26.11.2026, 13:25 МСК.

Промт пользователя:

> всё классо, но нужно добавить возможность отправку файлов - так-же как и в обычном диалоге с клиентом.

После остановки работы пользователь попросил продолжить с места остановки.

Изменено:

- Скрепка, несколько файлов, paste/drop, preview и удаление до отправки во
  внутреннем composer. Отправка с текстом и без него, общий способ Enter/Ctrl+Enter.
- Отдельные файлы/draft/UUID; ошибка сохраняет очередь, replay сверяет текст,
  имя/MIME/размер/SHA-256 и не создаёт дубли. До 10 файлов, суммарно до 50 МБ.
- V108: append-only attachment metadata, атомарная запись с internal message,
  отдельный storage domain internal_dialogs. Клиентская история/transport/AI
  внутренних файлов не получают.
- Загрузка требует current OPERATE, скачивание и preview/range — current READ
  полного owner. MIME определяется по данным; небезопасные/неизвестные форматы
  скачиваются как octet-stream. Ответы no-store/nosniff.
- Известные ошибки очищают новые бинарники; неизвестный исход commit сохраняет
  их приватными до сверки. Polling не пересоздаёт media DOM; read marker
  учитывает clipping scroll ancestors.

Области: collaboration service/controller, новый attachment service/controller,
object storage, V108, JS/template/SCSS/app.css, PostgreSQL/MVC/storage tests,
actual-MVC browser harness, правило истории, task details/list.

Проверки: 67 Java tests, 26 Node cases, 6 Edge/Chromium сценариев, V1-V108 chain
на отдельной PostgreSQL; integrity JAR/resources/classes GREEN. Полный lifecycle
uploads/replay/rollback/скачивание проверен на fixtures и изолированной БД;
тестовые отправки клиентам и uploads в production не выполнялись.

Установлен только panel-web R324, schema V108 применена migration owner до
запуска web. Healthy/readiness UP; 17 остальных контейнеров, роли/аккаунты/grants,
questionnaire bindings, исходный owner/анкета и settings.json сохранены.
Rollback R323 доступен, не запускался; backfill не выполнялся.
Артефакты и backup: C:/1C/backup/deploy-feedback-4ca73f33b-20261119/
internal-files-581002b-r324-20261126-125200/.

Fresh baseline/head/origin_main: 581002b2fe2a2934cddc697e1a22617dad8bee74.
Detached sandbox и те же patches в main, content/exact-file guards GREEN.
Stage/commit/push не выполнялись; задача 🟣, ожидается ручная проверка.
