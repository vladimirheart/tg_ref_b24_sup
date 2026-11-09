# Доступ общих задач, проектов и Kanban — R303

R301: общие межбизнесовые работы с отдельными правами и участниками.
R302: source V88, ядро авторизации и control API. R303: основные CRUD/list/
project links/Kanban/analytics подключены; реализован полный доступ системного
администратора по уточнению пользователя. Общая задача01-278 остаётся🟡.

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
lead не превращаются во владельца автоматически. Новых migrations в R303 нет.

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

## Подключённые пути и остатки

Подключены task/project create/read/update/delete, комментарии, SQL lists/count/
pagination до LIMIT, server-rendered tasks, project options в settings, project
links, Kanban columns/cards и task analytics/CSV. Доступ к проекту и задаче
проверяется независимо; скрытые связи/placements не удаляются при постороннем
изменении. Personal board имеет numeric scope PERSONAL_USER:<users.id>;
старый текстовый personal scope доступен только системному администратору.
Уведомления фильтруют кандидатов по текущему READ до записи.

Остаются UI управления grants/участниками/owner и save/reload/revoke приёмка,
аудит изменений доступа, повторный READ при поздней доставке/SSE, остальные
search/report/related-object projections и trusted background/auto-close
producers без authenticated principal. Incident/system notification policy
и оставшиеся системные полномочия требуют отдельной проработки. Нельзя объявлять
все проекции защищёнными по наличию основных guards. AI/privacy и S6 остаются
в общей задаче; historical S2B снятR190.

## Проверка и рабочая активация

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
