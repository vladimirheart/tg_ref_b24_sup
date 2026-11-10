# Доступ общих задач, проектов и Kanban — R305

R301: общие межбизнесовые работы с отдельными правами и участниками.
R302: source V88, ядро авторизации и control API. R303: основные CRUD/list/
project links/Kanban/analytics подключены; реализован полный доступ системного
администратора по уточнению пользователя. R304 добавил управление допусками,
участниками и владельцами из панели. R305 включил этот контур в рабочем
экземпляре вместе с V88/V89, аудитом, текущим READ уведомлений/SSE и trusted
background producer. Общая задача01-278 остаётся🟡.

## Работа в панели

Откройте «Задачи» или «Проекты». Системному администратору доступна кнопка
«Доступ к задачам и проектам»: для пользователя включите чтение и нужные права
создания, нажмите «Сохранить». Значения перечитываются с сервера; допуск к модулю
сам по себе не раскрывает чужие объекты.

Откройте карточку существующей задачи/проекта. Раздел «Доступ и участники»
показывает состав. Владелец либо системный администратор может найти пользователя
с допуском к модулю, выбрать чтение/редактирование и добавить или изменить участие.
«Отозвать» закрывает последующие запросы этого участника. «Передать владение»
требует подтверждения; прежний OWNER становится EDITOR и после reload теряет
управление составом. Администратор может назначить владельца unowned объекта.

Для проверки сохранения закройте и заново откройте карточку/раздел допусков.
Реальные права определяет сервер при каждом запросе; отказ очищает UI состава
и блокирует запись. Неопределённый результат записи не повторяется автоматически:
панель перечитывает состояние. Поиск показывает до100 подходящих строк каталога;
при необходимости уточните имя. Labels сохраняют точное имя, выбор передаёт
numeric userId строкой без округления. Интерфейс не требует ввода numeric ID.

Раскройте «Журнал изменений доступа» в карточке либо в разделе общих допусков.
Владелец/системный администратор видит историю объекта, общий журнал доступен
только системному администратору. Запись показывает действие, actor, время,
before/after; «Ещё» загружает следующие50 записей. История сохраняется при удалении
объекта/учётной записи; она не редактируется через панель.

## Действующая модель

Текущий системный ROLE_ADMIN имеет полный доступ к любым задачам, проектам,
доскам и стадиям: создание, чтение, изменение, удаление, управление допусками,
участниками и владельцем. Ему не нужны PAGE_TASKS/PAGE_SETTINGS, shared grants
или участие в объекте. Администратор видит также старые unowned строки и
личные доски других пользователей; может восстановить numeric владельца.

Для остальных нужны одновременно текущие PAGE_TASKS, shared can_read и участие
в конкретном объекте. Business membership, business ADMIN, ALL_BUSINESSES,
GENERAL и ROLE_PORTAL_ADMIN этого допуска автоматически не дают. Участие в
проекте не выдаёт доступ к его задачам; связь с обращением не заменяет его READ.

| Роль участника | Чтение | Изменение объекта | Управление составом/владельцем |
| --- | --- | --- | --- |
| VIEWER | Да | Нет | Нет |
| EDITOR | Да | Да | Нет |
| OWNER | Да | Да | Да |
| Текущий системный ROLE_ADMIN | Все объекты | Все объекты | Все объекты |

can_create_task и can_create_project — отдельные разрешения для обычного
пользователя; требуют can_read, но не расширяют видимость чужих объектов.
Проверяются canonical users.id и точное имя текущей сессии, enabled/blocked,
неоднозначность username, текущие прямые/assigned права. Отозванная роль
в сохранённой сессии доступа не даёт. Username admin не является привилегией.
CSRF, валидность данных и внешние ключи обязательны для всех ролей.

## Создание, владение и блокировки

V88 добавляет shared_work_grants, shared_task_members/shared_project_members и
nullable shared_owner_user_id. Начальных grants/участников нет. Старые creator/
lead не превращаются во владельца автоматически. R305 применил V88 и новую
V89 в рабочем экземпляре. V89 добавляет append-only shared_work_access_audit
и typed recipient/object поля notifications, без переноса старых записей.

Основные task/project writers сохраняют root, numeric owner из authenticated
principal и OWNER в одной транзакции; ошибка регистрации откатывает всё.
Клиентские creator/author не определяют автора. Отсутствующий update ID даёт404,
не создаёт новую запись. task_seq выдаётся атомарно.

Обычный member API принимает VIEWER/EDITOR и не удаляет/не понижает OWNER.
Отдельный owner route переносит владение текущему eligible numeric пользователю:
старый владелец становится EDITOR, новый — OWNER, root FK меняется атомарно.
Текущий OWNER может передать свой объект, администратор — любой, включая unowned.

Account/grant/member locks удерживаются до commit; writes также блокируют root.
Cross-object writes проходят единый transaction advisory gate, board projections —
отдельный scope lock. Отзыв ждёт текущую транзакцию; следующий запрос проверяет
новый допуск. Отключённый/blocked или не имеющий текущих прав account доступа
не получает. Чтение не блокирует все видимые root rows.

## Source API

| Route | Допуск |
| --- | --- |
| GET /api/shared-work-admin/history?before= | Текущий ROLE_ADMIN; общий аудит |
| GET /api/shared-work/{kind}/{id}/history?before= | OWNER либо текущий ROLE_ADMIN; аудит объекта |
| GET /api/shared-work/context | Текущий module reader либо ROLE_ADMIN; capabilities |
| GET /api/shared-work/{kind}/{id}/access | READ объекта либо ROLE_ADMIN; текущие write/manage и состав |
| GET /api/shared-work/{kind}/{id}/people?q= | OWNER либо ROLE_ADMIN; текущие eligible candidates |
| GET /api/shared-work-admin/access | Текущий ROLE_ADMIN |
| PUT /api/shared-work-admin/access/{userId} | Текущий ROLE_ADMIN + CSRF |
| GET /api/shared-work/{kind}/{id}/members | Текущий ROLE_ADMIN либо обычный READ объекта |
| PUT /api/shared-work/{kind}/{id}/members/{userId} | Текущий ROLE_ADMIN либо OWNER + CSRF |
| DELETE /api/shared-work/{kind}/{id}/members/{userId} | Текущий ROLE_ADMIN либо OWNER + CSRF |
| PUT /api/shared-work/{kind}/{id}/owner/{userId} | Текущий ROLE_ADMIN либо OWNER + CSRF |

kind — TASK/PROJECT. Member PUT принимает JSON role=VIEWER/EDITOR; owner route
выбирает numeric пользователя из URL. Grants PUT полностью заменяет canRead/
canCreateTask/canCreateProject; создание без canRead запрещено. Отзыв canRead
не удаляет состав, но закрывает следующие операции обычного пользователя.
Неавторизованный object lookup возвращает404, не раскрывая существование.
Неверные ID/role/hierarchy дают400, изменение OWNER через member API —409.
Control API возвращает BIGINT IDs строками, read responses — no-store.
History возвращает до50 событий в порядке убывания id. before — exclusive
string BIGINT cursor последней строки; ID нельзя округлять через JavaScript Number.
Аудит атомарен с mutation; повтор без изменения grants/member не добавляет событие.
Trigger запрещает UPDATE/DELETE; это не защита от владельца БД/TRUNCATE.

## Подключённые пути и остатки

Подключены task/project create/read/update/delete, комментарии, SQL lists/count/
pagination до LIMIT, server-rendered tasks, project options в settings, project
links, Kanban columns/cards и task analytics/CSV. Доступ к проекту и задаче
проверяется независимо; скрытые связи/placements не удаляются при постороннем
изменении. Personal board имеет numeric scope PERSONAL_USER:<users.id>;
старый текстовый personal scope доступен только системному администратору.
Manager reports, dialog task history и incident task picker применяют текущую
видимость до counts/LIMIT. Source R306 разрешает incident summaries любому
текущему читателю задачи: инциденты общие для всех действующих panel accounts.

Уведомления сначала разворачивают настроенную аудиторию, затем проверяют READ
каждого numeric получателя. Typed kind/object/recipient неизменяемы в записи;
списки, unread count, read mutations и поздний SSE повторно проверяют текущие
права и identity. Отзыв module/member/account/role закрывает уже сохранённую
запись. Старые unowned task/project notices не становятся обходом.
SSE клиенту отправляет только reason/emittedAt, внутренний scope удаляется.

Auto-close использует отдельный trusted writer без имитации входа пользователя:
eligible numeric owner, source ticket READ, canonical source tuple, project
READ/WRITE при наличии ссылки; root/OWNER/sequence/link/system audit атомарны.
Небезопасный GENERAL/unresolved legacy вызов без подходящего scope пропускается.
Старые unauthenticated create overloads закрыты.

UI, аудит, notification/SSE, task projections и auto-close R305 реализованы
и активированы. Общие incidents/system notifications согласованы и реализованы
в source R306; см. [отдельный контракт](common-system-access.md). Рабочая
активация R306, AI/privacy и S6 остаются в общей задаче. Historical S2B снятR190.

## Проверка и рабочая активация

R305:454 Java tests/19 suites,15 Node flows и22 real-browser checks GREEN.
Проверены две реальные сессии, UI grant save/reload, создание проекта/задачи,
VIEWER, передача owner и потеря управления, member/module revoke без logout,
настоящее уведомление/SSE и скрытие сохранённого уведомления после revoke.
Аудит просмотрен в фактической панели. Временные users/roles/объекты удалены,
audit22 сохранён; исходные tasks5/projects2/notifications14150 прежние.

Web image0ccec24e7fd5, workerfe7a2c56160b; baseline bd0d76982 +uncommitted R305
patch, не отдельный committed SHA. Web-only дополнение исправляет PostgreSQL
registration_date binding. По1 healthy replica/restart0, health/readiness200UP.
18 других containers и3 bot children прежние, bot-runner остаётся R295.
V88/V89 применены без backfill/seeds; flags/.env/principals прежние.
Актуальный Compose для web/worker:
../iguana-runtime/tg_ref_b24_sup/releases/r305-shared-lifecycle-20261109/private/registration-v2/compose.final.json.
Не подставлять старый R300 image при последующих Compose операциях.

До миграций сохранён и изолированно восстановлен DB backup; rollback images
rollback-r305-r299-20261109 и rollback-r305-web-core-20261110 сохранены.
Первый pre-migration отказ действительно вернул backend на R299/V87.
После успешной V89 downgrade рабочей БД не проверялся; image rollback
не отменяет migrations. Это локальная приёмка shared works, не общий S6.
Полные fingerprints/proofs: Runtime R305 в карточке01-278 и release evidence
../iguana-runtime/tg_ref_b24_sup/releases/r305-shared-lifecycle-20261109/.

Далее — исторические source proofs до активации R305.

R304:374 Java tests/11 suites +13 Node cases GREEN: validation-v1 —372,
ui-template-validation-v2 —2 Thymeleaf/navigation +13 UI. Проверены новые current
capabilities, owner-only candidate lookup, raw BIGINT, выдача/перечитывание grants,
member/owner/revoke HTTP round trip, реальные HTML fragments и UI действия.
Это source/test проверка; рабочий browser/provider rollout ещё не выполнен.
Evidence: ../iguana-runtime/tg_ref_b24_sup/releases/r304-work-access-ui-20261109/.

Ниже — прежний технический proof R303:

validation-v7:369 tests/10 suites одним GREEN run, failures/errors/skips0:
246 GENERAL,98 ACL,15 actual HTTP/JPA/security,2 Flyway clean/upgrade,
8 project/board/analytics regression cases. Проверены полный admin без pages/
grants, exact numeric/raw identity, отзыв роли, CSRF, owner transfer/recovery,
скрытые tasks/count/cards/links, concurrent project/task mutation и rollback.
JDK17 скомпилировал20 изменённых sources поверх полного compiled baseline v2;
остальной bytecode проверен по hash. Новый полный image build не выполнялся.

Disposable PostgreSQL128MiB с owner/nonce guard, рабочая БД не использована.
Все20 existing container snapshots неизменны. Рабочая V87/backend R299/bots R295,
Compose R300, flags/principals прежние; source V88 применён только в test DB.
Самостоятельный rebuild до завершения wiring и подготовки рабочей V88 не
выполнялся. Повторять V86/V87/provision не нужно. Browser/provider/S6 приёмка
этими тестами не заменяется.
Evidence: ../iguana-runtime/tg_ref_b24_sup/releases/r303-work-wiring-20261109/,
validation-v7/proof.json, source-inputs.json, final-suites.json.
