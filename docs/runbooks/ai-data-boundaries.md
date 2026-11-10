# Границы данных AI — R315

## Текущий рабочий выпуск — R315 / V91

Рабочий проект находится на машине текущей сессии. Backend из committed
R314/e41252b630e65e7d1cf91883dc51b39f7380f6c7 установлен в panel-web/ops-worker,
image3998c9ea829a, по1 replica/healthy/restart0. R311–R314 и V90/V91 активированы
на уровне кода/схемы. Health/readiness200UP, новых ERROR/FATAL/OOM0.
Боты R295,18 остальных контейнеров и3 child PID/start ticks не менялись.
ENV/mounts/.env/settings.json и principals/grants/AI flags сохранены.

716 targeted tests/15 suites GREEN, failures/errors/skips0: реальные native
PostgreSQL и Spring HTTP/Thymeleaf/CSRF, provider doubles и две crash JVM,
cleanup/CAS/revoke/generation/channel, reviewed rules, delivery и QUEUED recovery.
Пересобраны14 main/11 test Java поверх сверенного R310 cache1459 Java;
2375 unchanged class hashes и1912 packaged payloads подтверждены. Это targeted
compile/acceptance с Maven packaging/image build, не полный repository suite.

Репетиция на реальном V89 backup: restore и ровно V90/V91, fingerprints всех157
прежних таблиц и история V1–V89 сохранены, новые rules/receipts пусты.
Перед рабочим cutover дополнительно сохранён свежий custom backup с SHA256/TOC;
его hash отличается от более ранней восстановленной копии, обе сохранены.
Одноразовый Compose db-migrate затем применил V90/V91 на рабочей БД;
backfill, seeded approvals, изменение grants/flags и очистки не выполнялись.
Рабочая БД не восстанавливалась из backup. Rollback images R310 сохранены,
но откат backend или downgrade V91 не разыгрывались.

10 real Edge gates GREEN: catalog/orphans/rules/workbench GET404 и подтверждённые
erase/clear-history/create-rule/revoke-rule POST404 при прежних выключенных
rollout flags; incidents/inbox200. Положительные AI сценарии проверены
изолированно. Рабочие материалы/история не очищались, правила не создавались,
LLM/provider/auto-reply не включались. Новый код сам по себе не разрешает отправки.

Следующий рабочий AI шаг — controlled включение согласованного режима в
выделенном BUSINESS, явный review одного безопасного ответа и приёмка typed
active-ticket ingress/delivery/revoke/restart на выбранных тестовых получателях.
Готовый QUEUED требует повторных proof; SENDING/UNKNOWN не переочередять.
Непроверенный текст и вложения в LLM не разрешены. Сам LLM не нужен для
согласованного дословного ответа. Live provider и общая S6 остаются отдельными gates.

Актуальные Compose/rollback/evidence — Runtime R315 в
[карточке01-278](../../ai-context/tasks/task-details/01-278.md).
Ниже сохранены source-инструкции и checkpoints прежних сессий; указания о другой
машине относятся к ним. Для текущего стенда сборка R311–R314 и V90/V91 выполнены,
повторять migrator или backfill по старым pending-формулировкам не нужно.

## Восстановление очереди AI после остановки worker — R314

QUEUED означает, что durable provider claim ещё не был принят. Отдельный
WORKER/LEASED проход с интервалом5s выбирает до25 таких записей, даже если UI
cursor уже впереди. Все owner/source/rule/reviewer/channel/history/control/mode/
limit proofs R313 повторяются до transport и на границе provider. Исходный
incoming event должен существовать: без него CANCELLED, без поиска замены по raw ID.
При global pause запись остаётся QUEUED; возвращение режима не отменяет proof.

SENDING/UNKNOWN не являются очередью для повторной отправки. Рестарт, истечение
lease и новый проход никогда не возвращают их в QUEUED. Даже сбой до фактического
сетевого вызова после durable SENDING требует ручной сверки. При UNKNOWN
уведомление прямо просит сначала проверить provider, а не отвечать повторно.
Новая terminal metadata event обновляет карточку; handoff/уведомления проходят
по текущему owner/business/READ и control. Удалённый/перемещённый owner не
перепривязывается к новому бизнесу. AI-disabled не снимается ради уведомления.

На рабочей машине/в runtime-сессии:

1. Сверить фактический release/Compose/schema. Если R313/V90 уже установлен,
   применить migrator V91 и обновить ops-worker новым backend image. V91 — partial
   index idx_ai_text_reply_queued, без data migration/auto-cleanup/seeded approvals.
   Если ещё установлен последний документированный R310/V89, требуется также
   rollout R313 из следующего раздела: web/worker и V90. Старые migrations не менять.
2. Проверить readiness, V91/index и WORKER ownership. Интервал можно задать
   panel.ui-event-outbox.ai-recovery-interval-ms; по умолчанию5000. Не сбрасывать
   UI checkpoint для запуска recovery и не включать AI flags автоматически.
3. В выделенном тестовом контуре подготовить QUEUED, остановить worker до claim
   и запустить: ожидается одна отправка либо CANCELLED при изменившемся proof.
   Для SENDING/UNKNOWN ожидается отсутствие повторной отправки. Приёмка реального
   провайдера и процессного fleet restart остаётся частью общей S6; не провоцировать
   ambiguous outcome на чужих/рабочих получателях.

Изолированная приёмка выполнена:150 tests/5 XML,0 failures/errors/skips.
121 full delivery tests,10 scoped callbacks,12 watcher,7 runtime source contracts;
2 дочерних JVM с Runtime.halt и file-only provider double. QUEUED сохраняется
после crash и подбирается один раз, SENDING не подбирается. Дополнительно batch,
cursor bypass, ingress/recovery race, proof revocation/missing event, terminal
UI/handoff, UNKNOWN notice и V89→V91 без legacy reattribution. Final sources:
1465 Java+5 resources, проверенный baseline cache1464 Java;5 main/3 test compile.
Logs/XML .tmp/r314-*; owned PostgreSQL остановлен. RED v1 — повторный fake-token
fixture, исправлен уникальным значением без изменения бизнес-assertions.

Рабочий runtime здесь не наблюдался и не менялся. Последний документированный
R310/V89 и прежние Compose/rollback pointers ниже сохранены. Это source recovery,
не новый live channel/provider/restore/rebind или полный S6/§7.1 gate.

## Разрешённый дословный автоответ — R313

Правило настраивается в редакторе материала через «Правила автоответа».
Нужны PAGE_KNOWLEDGE_BASE и business ADMIN/ALL_BUSINESSES ADMIN. На странице
показан полный текст APPROVED материала до4000 символов; отдельный checkbox
подтверждает review и разрешение отправки. Вопрос до500 символов сравнивается
точно и с учётом регистра; убираются только обычные внешние пробелы. При новой
версии материала отозвать старое разрешение и создать новое. Действующие
правила выводятся страницами по25 и отзываются по CAS revision. Revoke сохраняет
правило/audit; сам по себе не очищает данные. Автоочистки нет.

Нужны foundation+uiSelector, включённый AI и режим auto_reply. Действуют
max/window/cooldown и ai_disabled/auto_reply_blocked. APPROVED без правила,
assist_only/escalate_only, неполный owner/child, media, чужие business/channel/
generation, изменённый ответ, утраченный reviewer/grant/page, новый диалоговый
контекст или поздний control/work revision не разрешают отправку.

Этот режим отправляет только проверенный текст источника и не обращается к
LLM. Входящий вопрос используется локально для точного сравнения, не для prompt.
Полный LLM payload всё ещё требует отдельного review, если будет нужен LLM.
Ручной MEMORY preview R312 с NULL metadata не даёт права на автоматическую
отправку. Typed link создаётся только для свежего текста active-ticket ingestion;
начальные анкеты/legacy events без link остаются handoff. Backfill не нужен.

Перед рабочей активацией на **машине проекта в другой агентской сессии**:

1. Сверить там реальный source/runtime, собрать новый backend image из этого
   exact source и обновить panel-web/ops-worker. Выпуск требует Flyway V90;
   это additive nullable incoming_history_id и пустые rules/receipts tables,
   без переноса/классификации данных и seeded approvals. Bot modules не менялись.
   Проверить Flyway90, health/readiness, image/JAR и прежние flags; автоматическое
   включение AI/rollout не является частью source apply.
2. В выделенном BUSINESS создать/одобрить безопасный материал, открыть правила,
   проверить полный ответ и разрешить один точный вопрос. Включить согласованный
   тестовый режим. Отправить новый обычный текст через active-ticket ingress;
   проверить history public/channel, incoming_history_id, один дословный ответ,
   SENT receipt и отображение AI в карточке. Повторить на требуемых Telegram/VK/MAX
   каналах без отправок посторонним адресатам.
3. Проверить unmatched текст, вложение, отключение AI/автоответа, assist_only и
   отзыв правила: ответа нет, handoff остаётся. Изменение версии источника не
   должно использовать старое разрешение. Учесть cooldown/лимит при повторе
   одинакового вопроса: новые вопросы имеют разные history IDs.
4. Replay/concurrency и ambiguous-provider сценарии воспроизведены в изолированных
   тестах. В рабочем контуре не сбрасывать checkpoint и не переочередить SENDING/
   UNKNOWN для проверки: один provider attempt может уже быть принят. Требуется
   ручная сверка с провайдером; автоматического retry нет. Очистка истории R311
   сохраняет receipts, чтобы старое событие не отправилось повторно.

254 tests/6 XML GREEN:88 delivery/PG migrations,84 full source/rules HTTP,
60 ingestion,12 watcher,7 incoming callbacks и3 transport-boundary cases.
Включены real ORM ingestion+inbox replay, review/CAS/CSRF/current rights,
source revision, frozen destination, close/reopen/control stamps, режимы/лимиты,
concurrent duplicate и rollback после provider acceptance. Провайдеры — doubles;
живые сообщения/LLM не отправлялись. Cache1460 baseline Java проверен,
final inputs1464 Java+4 resource files; scoped compile, не полный repository build.
Logs/XML .tmp/r313-*; nonce-owned PostgreSQL остановлен. RED v1–v3 сохранены,
исправлены fixture setup/точный CSRF redirect и соединения test harness.

Последний документированный рабочий runtime — R310/V89/image b2dc2f0fb876;
прежние Compose/rollback pointers ниже сохраняются. R313 здесь не deployed;
не означает завершения всех channel/provider/restore/replay или S6/§7.1 gates.

## Режим AI и текстовая история — R312

Решение пользователя R312 от10 ноября2026: целевой режим BUSINESS AI —
«Автоответ клиенту по разрешённым правилам». В LLM разрешён только текст,
который оператор явно проверил перед отправкой, без вложений. Это не разрешение
передавать непроверенное входящее сообщение/всю переписку или media по одному
глобальному enabled flag. Review одного источника не подтверждает остальной
payload обращения. Продуктовый допуск auto-reply больше не PENDING; реализация
правил/контроля проверенного текста, scoped client delivery и рабочая активация
ещё впереди. R312 не включает LLM/auto-reply и не выполняет внешних запросов.

ScopedDialogAiWorkbenchService использует один TEXT_HISTORY фильтр для preview,
вопроса и ответа MEMORY. Кроме composite owner проверяется соответствие явно
заданного history.ticket_public_id current root и history.channel_id каналу root.
Если root channel неизвестен, строка с известным channel не подходит. Фильтр
работает до LIMIT50 и повторно в final INSERT+audit+evidence; stale metadata
после открытия формы даёт409 без создания источника/аудита/evidence.

NULL public ID/channel допускаются только по прежнему manual curation контракту
с user/raw owner, текущим READ/CONFIGURE и reviewed текстом. Это не доказательство
исторического business/generation каждой строки и не отдельное подтверждение
передачи её в LLM. Автоматический backfill не нужен: старые данные тестовые R190.
Фильтр не читает media/URLs, не обращается к LLM и не отправляет ответ клиенту.

138 targeted tests/5 Surefire XML GREEN, failures/errors/skips=0. Все workbench и dialog HTTP tests плюс выбранные identity positives
двух общих fixtures и principal tests.6 native regressions были RED на R311;
проверены NULL/current/partial positives, чужой question/answer generation/channel,
root без канала,60 посторонних строк до LIMIT и late mutation через HTTP/CSRF.
Cache1460 Java сверены;1 main/5 test compile. Logs/XML .tmp/r312-*.

После нового backend web/worker image на машине проекта (без новых migrations):

1. В выделенном тестовом BUSINESS обращении строки с явным чужим public ID или
   каналом отсутствуют в AI preview. Подставленные ID/fingerprints в MEMORY POST
   дают409; новых source/audit/evidence нет, исходная переписка не изменена.
2. Соответствующие metadata и прежние NULL metadata сохраняют ручной reviewed
   MEMORY roundtrip. Права PAGE_DIALOGS/PAGE_KNOWLEDGE_BASE/CONFIGURE и CSRF прежние.
3. Прежние rollout flags сохраняются: если AI UI выключен, ожидается404. Этот
   выпуск не включает auto-reply/LLM и не требует проверки отправки провайдеру.

Рабочий Docker/проект — другая машина/сессия. R311/R312 здесь не deployed;
последний документированный runtime R310/V89/image b2dc2f0fb876 и его
Compose/rollback/evidence pointers сохранены. Следующая реализация — разрешённые
scoped правила автоответа и client delivery с отдельным review LLM payload.

## Служебная история актуального BUSINESS обращения — R311

В AI-карточке обращения раскрыть «Очистить служебную историю AI», проверить
описание и отметить подтверждение. Нужны PAGE_DIALOGS и текущий business ADMIN
либо ALL_BUSINESSES ADMIN. OPERATOR/MANAGER могут менять прежний control,
но не очищать историю; ROLE_ADMIN без бизнес-допуска недостаточен.
PAGE_KNOWLEDGE_BASE для этой операции не нужен.

Удаляются прежние scoped state/events и их handoff evidence; reason предыдущей
настройки стирается. Control сохраняется с ai_disabled=true и auto_reply_blocked=true,
новой updated_at и numeric/raw текущим автором. Сохраняется одна metadata-отметка
ai_data_cleared/manual_cleanup_ai_disabled. Поэтому удаление данных не включает
AI. Переписка, материалы KNOWLEDGE/MEMORY, их audit/memory evidence и backups
не входят в эту операцию. Автоочистки нет. Это не purge всех поколений/orphan
records; владелец должен быть актуальным каноническим BUSINESS обращением.

POST /business-dialog-ai/{publicId}/clear-history требует businessId,
controlStamp/stateStamp, eventThrough (точный BIGINT), confirmed=true и CSRF.
Один snapshot замораживает версии mutable rows и границу событий. Один SQL
повторно проверяет текущий numeric/raw аккаунт, active business/CONFIGURE,
полную canonical tuple, unique raw ID и snapshot, затем атомарно изменяет
control, удаляет прежние state/events/refs и добавляет audit. Ошибка audit или
неуспешный финальный state CAS откатывает все изменения.

Новые committed изменения до начала SQL дают409. События, поступившие во время
очистки, сохраняются; одновременная смена state даёт полный rollback/409.
Очистка не отменяет уже принятые действия. Повтор старого POST и старое control
save не проходят.400 — параметры/checkbox,403 — page/CONFIGURE,404 — rollout
flags/недоступный owner,409 — stale/forged форма/late revocation. Невалидная
сессия получает прежний login redirect; CSRF denial — /error/403. Controller
no-store; успешный redirect только в AI-карточку того же public ID.

81 targeted tests/2 suites GREEN, failures/errors/skips=0: весь existing dialog HTTP suite плюс новый cleanup PostgreSQL
suite. Реальные security/Thymeleaf/CSRF, roles, owner/CAS/late revoke/rename-reuse,
сохранность чата/знаний/чужого бизнеса, audit rollback и два concurrent writers.
Cache1459 Java sources сверен с R308; baseline5 main/7 test и текущие2 main/2 test
пересобраны. Это targeted проверка, не полный repository suite/image build.
Логи и XML .tmp/r311-validation-*, итог .tmp/r311-green.json.

Приёмка на машине проекта после нового backend web/worker image, без новых migrations:

1. На выделенном тестовом BUSINESS обращении business ADMIN видит форму очистки;
   VIEWER/OPERATOR/MANAGER её не видят. Прежние rollout flags не менять этим шагом.
   При выключенных foundation/uiSelector ожидается404, положительная приёмка
   остаётся изолированной до отдельного включения AI UI.
2. После подтверждённой очистки прежних state/events/refs и control reason нет;
   один audit marker, AI отключён и auto-reply заблокирован. Другой бизнес,
   переписка и curated материалы сохраняются.
3. Старая вкладка clear/control получает409, отсутствие checkbox/CSRF и отзыв
   прав не меняют данные. Новое действие требует обновлённой формы.

Текущая сессия source-only: рабочий проект/Docker на другой машине. Последний
документированный выпуск R310 с image b2dc2f0fb876 и его Compose/rollback pointers
не изменён. R311 здесь не deployed, рабочие AI-данные не очищались.

## MEMORY без исходного обращения — R310

В каталоге AI-материалов нужного бизнеса открыть «Материалы без обращения».
Нужны PAGE_KNOWLEDGE_BASE и текущий business ADMIN либо ALL_BUSINESSES ADMIN;
системный ROLE_ADMIN без бизнес-допуска не даёт этого доступа.
GET /business-ai-sources/orphans?businessId=... показывает только MEMORY,
для которых больше нет точной canonical связи с исходным обращением.
Удаление/смена бизнеса, поколения или owning tuple, GENERAL/UNRESOLVED и коллизия
raw ID не дают читать материал через обычный каталог. Отдельный список показывает
нейтральное «Сохранённое решение №ID», статус, revision и признак очистки;
title/body/origin и сведения о текущем чужом обращении не раскрываются.

Страница содержит25 записей. «Более ранние материалы» использует before=<точный ID>;
business/account/CONFIGURE и проверка origin применяются до LIMIT. BIGINT IDs
передаются строкой без округления. Поиск не читает текст материала.

Чтобы стереть содержимое, отметить подтверждение в строке и нажать
«Очистить содержимое». POST /business-ai-sources/erase-orphan-content требует
businessId/key/sourceId/revision, CSRF и confirmed=true. Kind фиксирован MEMORY.
В одной SQL-команде повторно проверяются текущий аккаунт/права, active business,
неизменный владелец, CAS и отсутствие current origin. Если связь успела
восстановиться, запись не меняется; старую форму нужно закрыть и обновить список.
Title/body заменяются теми же заглушками R308, ARCHIVED,revision+1 и metadata audit
атомарны. Уже очищенная строка остаётся видимой с отметкой и без формы;
повторный POST не создаёт новую revision или запись аудита.

400 — неверные параметры/нет подтверждения;403 — page/business отказ,
невалидная сессия может получить штатный login redirect; CSRF использует штатный
/error/403 redirect.409 — stale/подменённая форма, восстановленный origin,
уже очищенный материал либо отзыв прав перед final SQL.404 — выключенные
foundation/uiSelector flags. Ответы no-store, успешный redirect только
в список того же бизнеса. Title/body — единственное удаляемое содержимое:
ID/key/origin, chat_history, scoped state/events/control, evidence и backups
сохраняются. Это ручная очистка материала, не удаление всех данных обращения.

395 tests/6 suites GREEN, включая37 новых service/HTTP cases, failures/errors/skips0.
Реальные native PostgreSQL и Spring HTTP/Thymeleaf/CSRF проверяют доступ,
пагинацию, BIGINT>2^53, потерю/восстановление origin, CAS, повторный POST,
отзыв/блокировку/rename-reuse перед SQL, audit rollback и сохранность чужих данных.
Targeted2 main/2 test compile поверх сверенного R309 cache, упаковка и image build
GREEN; новая полная Java/Sass сборка и полный repository suite не заявляются.

Рабочие panel-web/ops-worker обновлены R310 через Compose --no-deps, по1 replica,
image b2dc2f0fb876, база09718fa26 плюс незакоммиченный R310 patch. V89 и flags
прежние, health/readiness UP, restart0, новых ERROR/FATAL/OOM0. Боты/инфраструктура
не пересоздавались.5 Edge gates подтверждают AI catalog/orphan GET/POST404
и incidents/inbox200. Положительная очистка — только изолированная HTTP-проверка;
рабочие AI-данные не удалялись. AI UI, LLM/provider и auto-reply не включались.
Актуальный Compose/rollback/evidence — Runtime R310 в
[карточке01-278](../../ai-context/tasks/task-details/01-278.md).

После R310 оставалась очистка scoped state/events/control; R311 реализовал
операцию для актуального BUSINESS обращения выше. Остаются media/LLM context и
scoped client delivery, отдельный допуск auto-reply, активация R311 и итоговая S6. Поиск/очистка orphan
MEMORY и согласованный срок хранения больше не являются незакрытыми пунктами.

## Хранение и ручная очистка — R308

Согласовано пользователем: «Всё хранить до ручного удаления, без автоочистки».
AI-данные не получают автоматический TTL или расписание удаления. Архивирование
материала само по себе сохраняет текст. Нынешний шаг реализует очистку заголовка
и текста KNOWLEDGE/MEMORY; общий purge scoped state/events/control остаётся
отдельной работой. Поиск orphan MEMORY добавлен R310 выше. Политика не меняет срок хранения
инфраструктурных backup и истории monitoring.

В редакторе существующего материала раскрыть «Очистить содержимое», отметить
подтверждение и отправить форму. Заголовок становится «Материал очищен», тело —
«Содержимое очищено.», статус ARCHIVED и revision+1. Предыдущий текст из панели
не восстанавливается. ID/key/business/origin, история metadata, fingerprints,
chat_history и резервные копии этим действием не удаляются. Для нового материала
или читателя без CONFIGURE форма отсутствует.

POST /business-ai-sources/erase-content требует PAGE_KNOWLEDGE_BASE, текущий
READ+business ADMIN либо ALL_BUSINESSES ADMIN, CSRF, confirmed=true, точные
business/kind/key/sourceId/revision. ROLE_ADMIN без бизнес-прав не подходит.
400 — нет подтверждения/некорректные параметры,403 — отказ page/business,
CSRF отказ использует существующий redirect /error/403 без записи данных,
409 — старая/подменённая форма или отзыв доступа после precheck,404 — rollout
foundation/uiSelector выключен. Ответы controller no-store; redirect в каталог
того же бизнеса. Numeric ID и точное имя/состояние аккаунта повторно проверяются
в SQL, как и права/active business/CAS; audit failure откатывает всё изменение.

MEMORY сохраняет неизменного владельца. Ранее открытая форма позволяет очистить
её текст после смены/удаления origin без чтения другого бизнеса; обычный editor/
catalog/retrieval по-прежнему скрывает stale origin. R310 добавил отдельный UI
поиска orphan MEMORY без чтения её текста. Очистка увеличивает revision и снимает APPROVED, поэтому сохранённый
кандидат и handoff evidence больше не допускаются. Новое последующее одобрение
другого текста не оживляет старую frozen evidence revision.

Проверка source R308:312 tests/6 suites GREEN, failures/errors/skips=0 в source sandbox. Проверены реальные native PostgreSQL и полный
Spring Security/HTTP/Thymeleaf, включая CSRF из отрендеренной формы, оба kind,
матрицу прав, revocation/rename-reuse перед SQL, CAS, audit rollback, origin loss
и handoff invalidation.2 main/3 test sources пересобраны поверх проверенного
R307 cache1459 sources; HTML включён в hash manifest входов валидации. Полный
repository suite/image/runtime браузерный smoke не выполнялся. Известные8
ChannelApiControllerWebMvcTest failures R306 вне scope и не повторялись.

Приёмка на машине проекта после нового backend выпуска:

1. Business ADMIN открывает выделенный тестовый материал, читатель видит только
   чтение; у нового материала очистки нет. Простое «В архив» сохраняет текст.
2. Подтверждённая очистка меняет title/body,ARCHIVED,revision+1 и добавляет одну
   metadata-запись. Другой бизнес с тем же ключом сохраняется; audit не хранит текст.
3. Старая вкладка erase/revise получает409. После отзыва CONFIGURE/блокировки
   очистка не проходит. Отсутствующий CSRF/confirmation не меняет материал.
4. Старый approved кандидат/handoff не выдаёт очищенный текст. Chat history и
   evidence остаются на месте. Живой provider/LLM для этой проверки не требуется.

## Изолированные пути — R307

BUSINESS AI использует canonical owner tuple/public ID/business, текущий READ
и отдельные scoped records/sources. Поиск approved KNOWLEDGE/MEMORY ограничен
бизнесом обращения; curated MEMORY создаётся вручную с подтверждением очистки
PII/secrets. Incoming записывает handoff и references для оператора; scoped LLM,
скачивание media и автоматический ответ клиенту пока не включены.

Старая цепочка работала с global memory/intent/context и raw ticket ID. R307
закрывает попадание новых бизнес-данных в неё через facade и diagnostic API.

| Состояние | Legacy ticket-bound AI |
| --- | --- |
| foundation.enabled=true | Отклонён для любого ticket ID |
| strictReadScope=true | Отклонён для любого ticket ID |
| Оба флага false | Только одна точная строка с business_id NULL и is_general FALSE |
| BUSINESS/GENERAL/unknown marker/коллизия/нет policy/schema/SQL failure | Отклонён |

Один guard действует на incoming, operator reply/learning, suggestions, control,
review, processing state, ticket-filtered events и три diagnostics: decision trace,
reclassify, retrieval debug. Оба HTTP варианта /api/dialogs/{id}/... и
/api/dialog-ticket/... проходят через тот же service guard. При отказе сохраняется
existing response shape: пустые коллекции/maps, false/updated=false. Page rights
и CSRF по-прежнему проверяет внешний HTTP слой. Разрешение page gate не снимает
ограничение scope. Нет попытки подменить raw ID trimmed sibling или выбрать одну
из коллизий. Guard повторяет текущие flags/SQL на каждом обращении; разрешение
не кэшируется. Общие compatibility guards остальных модулей прежние.

При обоих выключенных rollout flags сохраняется только прежний UNRESOLVED
контур. Его admission — проверка на момент вызова; это не блокировка строки
на время сетевых операций. Для многобизнесового контура foundation/strict
закрывают raw вход полностью. Прямые низкоуровневые legacy primitives/offline
evaluation не объявляются универсальным public scoped API этим изменением.

## Проверка R307 (исторический source checkpoint)

282 tests/15 suites GREEN, failures/errors/skips0. Среди них33 facade guard cases,
28 real persistence round trips, legacy exact-ID и scoped incoming regression.
Четыре новые PG/standalone-MVC cases выполняют24 HTTP requests по обоим URL
вариантам с реальными guard/facade/runtime и разрешённым внешним page gate:
нет context/media/LLM calls, данных trace или записей legacy delegates.
Полный authentication/permission/CSRF transport отдельно покрыт existing MVC suites.
BUSINESS и GENERAL сначала воспроизведены RED на R306; финальные assertions
не ослаблялись. Test compile RED из-за имени foundation setter исправлен на
существующий nested setter; логи/XML сохранены в .tmp/r307-*.

1459 Java sources cache сверены с validated R306 с нормализацией LF/CRLF;
3 main/4 test изменённых sources пересобраны. Полная новая image сборка здесь
не выполнялась; полный repository suite не объявляется GREEN. Известные8
baseline channel-test failures R306 не повторялись и не исправлялись этим scope.

## Исторический рабочий выпуск R309

R307/R308 включены в новые backend images на машине текущей сессии:
web ee124b5d137e, worker9201cfbf6d5a; V89, flags и настройки прежние.
Полная сборка, повторные native PostgreSQL/Spring HTTP наборы и приёмка
common incident обычной сессией пройдены. Рабочий AI catalog возвращает404
при прежних rollout flags: live очистка пользовательских материалов,
внешний LLM/provider и auto-reply не запускались. Рабочие AI данные не удалялись.
Очистка KNOWLEDGE/MEMORY, CAS, business isolation, origin loss и invalidation
проверены в отдельной PostgreSQL/HTTP. Это не включение скрытого UI или S6.
Сводка528 Java/21 suites включает3 новых registration/block cases;
новый R309 patch исправляет incident deep links и PostgreSQL boolean binding.

После R309 оставались ручная очистка scoped state/events, поиск orphan материалов,
media/LLM context и scoped client delivery, явное разрешение auto-reply и итоговая S6.
R310 закрыл поиск/очистку orphan MEMORY; текущий остаток приведён в начале документа.
Согласованное хранение до ручного удаления без автоочистки не является PENDING.
Исторические Compose/rollback/evidence — Runtime R309 в карточке01-278.

## Историческая инструкция активации source R307/R308

Рабочие Docker/проект находятся на другой машине и в другой сессии. Здесь
только source worktree и изолированная nonce-owned native PostgreSQL. Рабочие
БД/Redis/Rabbit/provider/configuration не использовались; новые migrations,
backfill, retention deletion, grants/flags/principals не выполнялись.

Для активации нужен новый backend image panel-web/ops-worker; простой restart
старого image код не обновит. R306–R308 можно включить в один согласованный
backend выпуск. Сохранить текущий Compose/ENV/mounts/replica counts и rollback
pointer на машине проекта; не возвращать старые R300 images. Bot children и
инфраструктура не требуют изменения этим source scope. Последний
документированный runtime — R305/V89, а не наблюдение этой source-сессии.

После выпуска при foundation/strict проверить legacy suggestions/control/debug:
пустой результат, отсутствие старых AI state/events/learning/context/LLM effects;
новый scoped BUSINESS handoff должен сохраняться. Затем обычная сессия R306 по
[общему контракту](common-system-access.md). Runtime/browser checks этого шага
здесь не выполнялись.

Срок согласован R308: хранение до ручного удаления, без автоочистки. На этом
source-checkpoint оставались ручная очистка scoped state/events и поиск orphan материалов, обработка media/LLM
контекста, фактическая scoped доставка и решение о допуске auto-reply. Право
внешней отправки и auto-reply не подставлять вместо решения пользователя. Старые
данные тестовые, heuristic migration/backfill не нужен; общая задача01-278🟡.
