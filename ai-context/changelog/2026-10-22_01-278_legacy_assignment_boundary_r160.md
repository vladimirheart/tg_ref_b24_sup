# 01-278 — R160: legacy assignment boundary

Дата: 2026-10-22. Общий статус задачи: 🟡.

## Исходный пользовательский промт

> репо обновлён другими изменениями в рамках задачи. изучи и продолжай. возьми более широкий пакет. напоминаю, что рабочий экземпляр проекта на другой машине.
> коммит и пуш разрешаю сразу после внечения правок без моего подтверждения - разрешение только в течении этих суток

## Baseline и среда

Fresh main/origin/main/actual remote: b1ab370d132a253de9317adc50fce70e0e152a6f,
parent 7974ee520ff6e660de14a4e821ec7520c1433c07, subject
«feat(multibusiness): ручное бизнес-назначение с проверкой прав и атомарным аудитом #ai».
Проверены реальные 17 файлов R159, main/index чистые. Изучены R155–R159: media
Telegram/VK, candidate pool, SLA и manual BUSINESS workflow; эти изменения не повторены.
Detached sandbox .tmp/01-278-r160 создан от точного baseline.

Рабочие Docker/проект находятся на другой машине, в другой сессии. Runtime
наблюдения прежних сессий сохранены как история; здесь нет рабочего health probe,
deploy/restart/migration/backfill/config/flags или provider sends. Новый grant
commit/push действует только 2026-10-22 Europe/Moscow до 2026-10-23 00:00 +03.
Source apply и Git publisher — разные операторы с exact content/file guards и
повторной проверкой срока перед Git mutation. Новая SQL migration отсутствует, V78.

## Изменение

R160: bare-ID take/reassign и implicit assignment допускаются только для одного
текущего UNRESOLVED/non-GENERAL root до strictReadScope. BUSINESS использует
scoped R159, GENERAL — собственный workflow. Capability не является authority:
writer повторяет owner/public/channel/revision/namespace/open proof под общим
advisory и root lock; zero-row/SQL failure не запускает success cleanup/AI/
уведомления. Callback выполняется под root lock и общим panel transaction manager,
совместимым с JPA-уведомлениями. Прежний V61 scoped callback берёт тот же advisory
lock перед root/child, сохраняя final owner/current OPERATE SQL. Raw take/reassign
audit не пишется в BUSINESS/GENERAL или при strict. Reply/edit/delete/reopen/bot
relay больше не назначают scoped root через общий raw writer; сами legacy
delivery/lifecycle/participant/read APIs и historical attribution этим не закрыты.
Закрытый legacy root может вернуть существующего ответственного без новой записи;
redirect запрещён. Прежний bot relay contract сохранён.
Новая SQL migration не добавлена; source minimum V78.

Raw take/reassign authorization теперь не принимает PAGE_DIALOGS/ROLE_ADMIN как
разрешение BUSINESS/GENERAL assignment. Ранний capability gate не заменяет финальную
транзакционную проверку. Финальный writer удерживает raw-ID advisory и root lock,
проверяет exact owner/public/channel/revision/namespace/status, а callback cleanup/
AI/notification/success выполняется только после подтверждённой записи. Отказ и
no-op не запускают прежний success workflow. Ошибка callback откатывает SQL.
Managed panel использует общий JPA-compatible transactionManager, минимальные
JDBC fixtures — DataSourceTransactionManager. V61 callback использует тот же порядок
advisory → root → responsibility; его current OPERATE SQL и frozen owner сохранены.

Raw take/reassign audit ограничен UNRESOLVED/non-GENERAL до strict, включая поздний
отказ после смены namespace. Другие audit actions и read marks не переписаны.
Opaque IDs не trim-ятся; старые username/actor правила, redirect AI quirk и wording
проверены прежними golden assertions. BUSINESS audit V78/outbox R159 не заменены.

## Проверки и RED

Совокупная targeted validation: 956 Java / 20 свежих XML, failures/errors/skips=0.
13 классов GREEN из broad .tmp/r160-panel-v3-tests.log; семь затронутых классов
перепроверены в .tmp/r160-panel-v4-tests.log: 320 tests, BUILD SUCCESS; compiled classes и source/report freshness
проверены. LegacyAssignmentBoundaryPostgresTest — 32, BusinessTicketAssignmentHttpPostgresTest — 51.
Включены прежние responsibility/snapshot/opaque/race suites, V61 scoped assignment,
SLA/CAS/pool/routing, GENERAL HTTP, frozen outbox, bot runtime, reply, authorization,
audit и readiness. Новый конкурентный PostgreSQL test наблюдает реальный advisory
wait до доступа scoped writer к root/child. Root lock удерживается во время callback,
callback failure откатывает assignment/audit, поздние owner/generation/channel/revision/
strict изменения и отсутствующая schema запрещают назначение. HTTP покрывает path/query
take/reassign для BUSINESS/GENERAL/strict с PAGE_DIALOGS+ROLE_ADMIN и positive legacy take.
Reply/edit/delete/reopen/relay подтверждают именно отсутствие implicit assignment.

Первый широкий run сохранён RED: новый callback с отдельным JDBC transaction manager
конфликтовал с JPA NotificationService (HTTP 500); исправлен общий runtime manager.
BotRuntimeTicketWriteServiceTest имел старую минимальную схему без scope outbox/root
полей; fixture приведена к текущему контракту без ослабления assertions. Старые
XML сохранены в .tmp/r160-initial-reports. Initial entrypoint operator остановился
до записи на indentation-sensitive constructor anchor; v2 использует semantic anchor. Transaction-fix v2 также остановился до записи на неоднозначном
outbox anchor; v3 использует уникальный rating/created_at anchor. Промежуточный
прогон v2 остановлен до тестов для сохранения no-JDBC-on-invalid-scope constructor
контракта; выбор compatibility manager теперь отложен, прежние assertions сохранены.
Broad v3 обнаружил ещё один прежний bot relay contract: существующий owner закрытого
UNRESOLVED root нужен без новой assignment записи. Исправлен этот read-only return,
redirect/новая запись закрыты, старые golden assertions не ослаблены. Два новых
closed cases явно разделяют read existing и write denial; всего boundary tests остаётся 32.
Validator v3 остановлен на node --check: локальное имя module конфликтовало с CommonJS.
В v4 локальное имя изменено, внешний report.module сохранён. Корневой checkout
до итогового GREEN не менялся.

Точный scope guarded apply — 15 файлов (5 production, 7 test, 3 документа). Четыре
CSS, сгенерированные Maven Sass, исключены по exact baseline blob/content proof.
Git operations отдельно проверяют baseline, фактический remote, file/content set,
parent/subject и дату. Java-bot/fleet/browser/live/provider проверок в R160 не было.

## Осталось

Producer context, оставшиеся escalation/writers, legacy reply/lifecycle/participant/
read APIs, полная historical child attribution, внешние recipients/webhook policy,
S2B согласование, GENERAL media/feedback, AI и сквозная приёмка/rollout остаются.
Legacy notification/SSE best-effort не становятся атомарной scoped outbox delivery.
Успешные Testcontainers/MockMvc не означают приёмку рабочего экземпляра.
