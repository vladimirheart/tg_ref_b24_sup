# Доступ общих задач, проектов и Kanban — R302

Решение пользователя R301: общие межбизнесовые работы с отдельными правами и
участниками. R302 подготовил схему, ядро авторизации и control API. Основные
task/project/Kanban экраны и их старые API ещё используют прежние guards и к
этому ядру не подключены. Пакет не является завершённой изоляцией этих страниц.

## Модель

Для доступа нужны одновременно текущий shared can_read и участие в конкретном
объекте. Business membership, business ADMIN, ALL_BUSINESSES, GENERAL и системная
роль администратора автоматически этого допуска не дают. Участие в проекте не
выдаёт доступ ко всем его задачам; связь с обращением не заменяет его READ.

| Роль участника | Чтение | Изменение объекта | Управление составом |
| --- | --- | --- | --- |
| VIEWER | Да | Нет | Нет |
| EDITOR | Да | Да | Нет |
| OWNER | Да | Да | Да |

can_create_task и can_create_project — независимые разрешения создания. Они
требуют can_read, но не расширяют видимость чужих объектов. Администратор
общих допусков должен иметь текущие ROLE_ADMIN и PAGE_SETTINGS; он не получает
READ сущностей автоматически.

Решения привязаны к canonical users.id и точному имени текущей сессии. Состояние
enabled/blocked и неоднозначность username повторно проверяются в SQL. Сходное,
обрезанное или переиспользованное имя не заменяет numeric principal. API отдаёт
BIGINT ID строками и запрещает caching списков прав/участников.

## Схема и создание

Source migration V88 добавляет shared_work_grants, shared_task_members,
shared_project_members и nullable shared_owner_user_id у tasks/projects.
FK/CHECK защищают IDs/roles/hierarchy, unique partial index допускает одного
OWNER. PUBLIC не получает доступ к новым таблицам. Начальных grants/участников
нет; creator/lead старых строк не превращается во владельца.

Trusted creation writer должен в одной транзакции записать numeric owner из
authenticated principal, сохранить и flush root, затем вызвать registerCreated.
Метод требует внешней транзакции и отдельного текущего права создания;
ошибка откатывает root и member вместе. Поиск уже существующего id или
переданный клиентом creator не разрешает присвоить владение. OWNER не передаётся,
не понижается и не удаляется через member API; recovery/transfer ещё не добавлены.

Account/grant/root/member locks удерживаются до commit. Отзыв допуска или
участия ждёт текущую транзакцию; следующий запрос получает отказ. Права создания
берутся из locked grant после ожидания, без повторного чтения старого MVCC snapshot.
Участник без общего READ, отключённый или blocked account доступа не получает.

## Source API

Все mutations проходят CSRF и проверку текущих прав, а не только authorities
из сохранённой сессии.

| Route | Допуск | Результат |
| --- | --- | --- |
| GET /api/shared-work-admin/access | ROLE_ADMIN + PAGE_SETTINGS | Accounts и отдельные shared grants |
| PUT /api/shared-work-admin/access/{userId} | То же | Полная замена canRead/canCreateTask/canCreateProject |
| GET /api/shared-work/{kind}/{id}/members | PAGE_TASKS + current shared READ + участие | Numeric участники |
| PUT /api/shared-work/{kind}/{id}/members/{userId} | PAGE_TASKS + current OWNER | Назначить VIEWER или EDITOR |
| DELETE /api/shared-work/{kind}/{id}/members/{userId} | PAGE_TASKS + current OWNER | Отозвать участие |

kind — TASK или PROJECT. PUT member принимает JSON с полем role. OWNER клиентом
не назначается. Снятие canRead требует canCreateTask=false и canCreateProject=false;
оно не удаляет состав, но сразу закрывает последующие reads/writes через ядро.
Неавторизованный object lookup не раскрывает наличие объекта:404. Некорректные
ID/role/hierarchy дают400; попытка изменить OWNER через состав —409.

## Проверено и осталось

350 targeted checks/5 suites:100 новых access/HTTP/migration cases в final
validation-v5,250 неизменённых регрессий в GREEN XML validation-v2. Ошибок,
пропусков и failures в этих5 suites нет. В v2 полный прогон был RED из-за5 HTTP
fixture cases без EnableWebMvc; финальный HTTP прогон исправлен и полностью GREEN.
Проверены реальные PostgreSQL locks/revoke/creation rollback, роли, exact identity,
CSRF/method security, string BIGINT, чистая схема до V88 и upgrade V87→V88 без
legacy classification. Это не browser/provider/production acceptance.

Следующий пакет должен подключить trusted create и существующие SQL list/count/
pagination/direct card/update/delete/comment paths, project links и Kanban.
Также остаются settings/report/export/search projections, уведомления с текущим
READ получателя, аудит изменений доступа, UI управления и save/reload/revoke
приёмка. Не объявлять их защищёнными только по наличию kernel/control API.

R302 в runtime не активирован. Перед самостоятельным rebuild/deploy сначала
нужно завершить wiring и подготовить согласованную V88 активацию: новые JPA fields
ожидают эти columns. Рабочая БД по-прежнему V87; существующие images/Compose R300,
flags, principals и боты не менялись. Повторять V86/V87/provision не нужно.
Evidence: ../iguana-runtime/tg_ref_b24_sup/releases/r302-shared-access-20261109/.
