# 01-278 / R277 — MAX-вложение без подписи, имени и URL

Промт пользователя:

> продолжай по задаче

Дата:2026-11-03, Europe/Moscow +03.

MAX больше не подтверждает как пустое сообщение объявленное вложение без
подписи, имени и URL. Прямые и пересланные attachments/body.attachments/media/files
доходят до существующего v4 admission по сохранённой session либо canonical root.
Missing/mismatched root по-прежнему возвращает retryable503 без ACK. Известный
legacy root или отсутствие active/session получают отдельное явное предупреждение
о нераспознанном файле, без фиктивного текстового followup/нового questionnaire.
Warning false/exception освобождает claim; повтор того же события разрешён.
После подтверждённого warning событие помечается обработанным, следующий replay
идемпотентен. Обычный текст и действительно пустой payload сохраняют поведение.
Scoped MAX byte writer этим не реализован; файлы/metadata не сохраняются.

424 tests/25 suites GREEN:386 MAX/22 (все336 прежних +50 новых),38 core/3.
Frozen RED/GREEN:50 cases,45 baseline assertion failures/5 controls,errors/skips0;
new test source/class/case names exact после product edit. Real handler, Jackson
session snapshots и production local CAS/delivery guard в direct mode; repositories,
canonical API и provider mocked. Проверены BUSINESS/GENERAL/absent session,
8 прямых/пересланных layouts, unknown root/retry, warning false/exception/replay,
legacy NULL/v1–v3, отсутствие active и сохранность raw session. Это не real Redis
eviction/rebind, live MAX/provider/customer acceptance или S6. Первый новый fixture
не прошёл существующий saved catalog/resources contract; исправлен только новый
fixture до product edit, failed logs/source/report/class сохранены отдельно.

Fresh HEAD/origin/main/actual remote14c00a87d (R276), parent1b7c51ee2;13 prior
paths/content verified, checkout clean. Detached source worktree, pinned R276
cache/2676 classes и frozen baseline; Maven wrapper/JDK17, bot-max -am. Product
scope:только MaxWebhookController +1 new test, task/S0/BOT/new append-only changelog.
Existing tests/POM/resources/schema/flags/session JSON не меняются.

Source/test machine:C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий Docker/проект
на другой машине и в другой сессии; здесь они не измерялись и не изменялись.
PostgreSQL/Redis/Rabbit/Docker/provider здесь не запускались. Последний известный
runtime остаётся R275/R274/R272 на исходной машине, не новое observation этого
checkout. Исторические checkpoint, backup/rollback и private DLQ archive сохраняются.
R276 уже committed/pushed владельцем; R277 source-only, stage/commit/push/deploy
не выполняются. Для будущей выкладки нужен MAX child в bot-runner вместе с
backend context R276; panel-web-only не активирует fix. Новых migrations/flags нет,
V83/V84/V85 не повторять. S2B снятR190, DLQ gate закрытR275; задача остаётся🟡.

Изменённые файлы:

- `ai-context/changelog/2026-11-03_01-278_max_opaque_attachment_r277.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxOpaqueAttachmentAdmissionTest.java`
