# 01-278 / R302 — независимые grants, numeric участники и ACL control API

Дата: 2026-11-09, Europe/Moscow. Общая задача🟡; основные task/project/Kanban
пути ещё не подключены к ядру. Это source foundation, не завершённая изоляция.

## Пользовательский промт

«хорошо. давай дальше».

Продуктовое решение R301: «Общие межбизнесовые задачи и проекты с отдельными
правами и участниками». Повторное согласование модели не запрашивалось.

Сохраняются ограничения: рабочий экземпляр на текущей Windows-машине; дополнительные
ресурсы освободить нельзя; не предоставлять команды ручной фиксации. Старое
разрешение на commit/push истекло; Git staging/commit/push в этой фазе не выполнялись.

## Baseline и scope

HEAD/origin/main/actual remote:
99cfefde03f6ebd067a7495af3ddcfb98ca5aae0 — R301; parent
f54e88324993d8de2fcaf13efb94ae46579d394f — metadata R300.
Первичный checkout чистый. Detached sandbox создан от exact baseline.
Entity blobs: Task.java 14c953f02281cea1c9c0c981a5708f49e6b8b943;
Project.java ceecbc097e17c4b86b8ea6c32b67629c5a2ab133.

13 project files:

- новый V88__shared_work_access.sql;
- новые SharedWorkAccessService.java и SharedWorkAccessController.java;
- Task.java и Project.java — nullable numeric owner field;
- новые SharedWorkAccessPostgresTest.java и SharedWorkMigrationPostgresTest.java;
- README.md, ai-context/tasks/task-details/01-278.md;
- ai-context/rules/03-multibusiness-s0-contract.md и docs/BOT_RUNTIME_CONTRACT.md;
- новый docs/runbooks/shared-work-access.md и этот новый changelog.
Старые changelog не изменены. Generated CSS не входит в scope: filtered blobs
соответствуют baseline.

## Реализовано

V88 добавляет shared_work_grants с отдельными can_read/can_create_task/
can_create_project, shared_task_members и shared_project_members с OWNER/
EDITOR/VIEWER. FK/CHECK/partial unique indexes защищают IDs, hierarchy и одного
OWNER. PUBLIC не получает новые таблицы. Numeric shared_owner_user_id nullable
у tasks/projects; ни grants, ни ownership старых строк не заполняются автоматически.

Current account определяется по authenticated PanelUserPrincipal users.id и raw
username, с enabled/blocked/ambiguity checks. Business ADMIN/ALL_BUSINESSES,
GENERAL, системная роль или текстовые creator/lead не дают implicit object READ.
Для объекта нужны одновременно shared READ и участие. Создание требует отдельное
разрешение; trusted writer сохраняет numeric owner и регистрирует OWNER в одной
внешней транзакции. registerCreated использует MANDATORY; rollback удаляет и root,
и member. Старый unowned id присвоить по creator/lead нельзя.

Ядро предоставляет object READ/WRITE/OWNER checks, visibleIds, roster и
идемпотентные member mutations. VIEWER читает, EDITOR изменяет, только OWNER
меняет состав. OWNER через public member API не назначается, не передаётся
и не удаляется. Программный owner recovery ещё не добавлен.

Новый control API: /api/shared-work-admin/access и /api/shared-work/{kind}/{id}/members.
Общими grants управляет текущий ROLE_ADMIN +PAGE_SETTINGS из прямых/assigned
прав; это не даёт object READ. Состав требует PAGE_TASKS и текущий OWNER.
CSRF включён; текущие права проверяются owning SQL, не только session authorities.
Read responses no-store, BIGINT IDs сериализуются строками.

Account/grant/root/member locks удерживаются до commit. Revoke ждёт текущую
транзакцию, следующий request отвергается. Для создания из locked reader CTE
передаются can_create flags: повторный plain MVCC lookup не может вернуть
отозванное право после ожидания. Eligibility target участника также защищена
FOR SHARE account/grant. Имя/бизнес/последний выбор не являются fallback.

## Валидация

350 уникальных targeted cases/5 GREEN XML, failures/errors/skips0:
- final validation-v5:98 SharedWorkAccessPostgresTest +2 SharedWorkMigrationPostgresTest;
- validation-v2:246 GeneralTicketAdminAuthorizationPostgresTest,
  2 PanelTaskServiceTest и2 ProjectServiceTest — неизменённые regression suites.

Это сумма подтверждённых suites, не заявление об одном350-case Maven run.
Новые100 cases покрывают actual PostgreSQL и Spring Security MVC/CSRF,
role matrix, exact/Unicode/padded identity, BIGINT, current revocation, no implicit
business/admin access, immutable owner, creation rollback, настоящие concurrent
locks и post-wait create revocation. Flyway проверен с нуля до V88 и с V87 до V88;
legacy rows/links сохранены, shared owner NULL/grants/members пустые.

Validation-v1:337 GREEN. Validation-v2:5 HTTP fixture failures415/406 из-за
отсутствия EnableWebMvc; остальные suites GREEN. Fixture исправлена без ослабления
проверок. Validation-v3 остановился до Maven: pg_isready попал во временный Unix
startup server PostgreSQL. В v4/v5 readiness идёт по TCP; stale XML не копируется
как новый результат. V4:98 GREEN, final v5 после locked-create проверки:100 GREEN.
Все логи/RED/partial proof сохранены.

Native JDK17, repository Maven wrapper, bounded parent/fork heap512/384MiB.
Disposable PostgreSQL192MiB, собственный nonce, dummy r290_edit, localhost random
port, без application mounts/working network. Полные20 existing container
snapshots ID/image/config/Started/Restarts/Running exact до/после final run.
Тестовый PostgreSQL остановлен/удалён только после owner+nonce/image guard.

После GREEN validated source перенесён в основной checkout; сравнение UTF-8
с одинаковой EOL normalization. Stage/commit/push/deploy не выполнялись.
Working DB V87, flags/principals/Compose R300 и боты не менялись; V88 выполнялась
только в изолированной БД. Working rebuild не запускался: новая source-модель
требует V88 и следующего wiring-пакета, независимая активация здесь не нужна.

Evidence/operators/proofs:
../iguana-runtime/tg_ref_b24_sup/releases/r302-shared-access-20261109/,
validation-v5/proof.json, source-inputs.json, final-suites.json и source-applied.json.

## Следующий пакет

Подключить trusted manual create и существующие task/project SQL list/count/
pagination/direct card/update/delete/comment paths, project links и Kanban.
Остаются settings/report/search/export/notification projections, current recipient
READ перед доставкой, audit доступа и UI/save-reload/revoke приёмка. Их нельзя
считать защищёнными только по наличию нового ядра/control API. Основные старые
TaskApiController/TaskQueryService/ProjectService/TaskBoardService не менялись.

Incident/system notification policy, AI privacy/retention/media/LLM/delivery и
полный отдельный S6/§7.1 остаются. S2B снятR190; закрытый R301 MAX gate не открывать
заново. Пересборка/миграция/активация R302 отдельно до wiring не выполняется.
