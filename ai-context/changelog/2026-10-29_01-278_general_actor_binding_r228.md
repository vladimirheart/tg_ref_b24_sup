# 01-278 R228 — GENERAL work/write/SSE связаны с ID сессии

Время: 2026-10-29T15:35:23.418Z (UTC; Europe/Moscow +03:00).
Baseline 0ea477d630d2316c3d7a0d00fa788d7c09aa5e10, parent 3f16baa81047fb923c9c8eebb0d26badc930ddaf; HEAD/origin/main/actual remote
совпали. R227 SHA/parent/subject/7 paths/content проверены и сохранены.

Промт пользователя:

> делай

GeneralTicketActor фиксирует ID/exact raw name из authenticated PanelUserPrincipal.
9 GENERAL services/29 actor-facing methods и3 controllers сохраняют исходную
Authentication; owning SQL требует ID+raw name вместо нового поиска по lower(name).
Account/READ/OPERATE/nullable-blocked/fold-uniqueness guards прежние. Work/status/
time/assignment/participant/reply/edit/document/manual flows используют тот же
snapshot. Queued dispatch восстанавливает сохранённую пару без name-login; SSE
ключ удерживает пару на весь stream и rechecks перед delivery/heartbeat.
Закрыт перенос прав старой сессии на аккаунт с повторно использованным логином.

Новые тесты проверяют все32 операции (29 APIs плюс status/participant branches):
rename/delete reuse/case/wrong ID/unbound/disabled/blocked, независимость READ/
OPERATE, Unicode/exact raw names, BIGINT, nullable-blocked, queued commands и
open streams;64 cases меняют fixture непосредственно перед owning SQL. Это не
внешний concurrent revoke после row lock. Снимки18 tables включают все поля,
при отказе нет domain/audit/outbox/provider/storage effects; при успехе сохранён
actor ID/name. Reflection bridge позволяет прогнать frozen новые cases до/после
смены API, без String fallback в product. Старые test-only login fixtures/matchers
адаптированы; result assertions прежние. Scope27:13 main,
9 existing tests,2 new tests,task/S0/этот changelog.

1287 tests /13 suites GREEN, failures/errors/skips0 одним selected run: 529 новых
SQL/transaction/queued-command/SSE cases и 758 existing cases. На неизменном
product baseline те же529 дали 232 assertion failures/0 errors,
controls297; case names и frozen source/class SHA одинаковы до/после.
Existing HTTP/work/assignment/reply/edit/document/manual/realtime/Redis wiring и
GENERAL entry real-login suites повторены. Старые result assertions сохранены;
test inputs адаптированы с String на ID-bound Authentication через test-only
login fixture, mock matchers проверяют Authentication. Production login по имени
не добавлен. Runtime/provider/whole-repository acceptance этим не заявляется.

В тестовой подготовке сохранены три неудачные попытки: v1 — 529 setup errors
(fixture IDs без OVERRIDING SYSTEM VALUE в GENERATED ALWAYS columns); v2 smoke —
32 cases/19 snapshot failures из-за сравнения JDBC byte[]/Array по ссылке.
v4 full baseline — 232 assertion failures и32 setup errors: is_blocked имеет NOT NULL.
До product edits исправлены fixture inserts, сериализация всех полей row_to_json
и синтетическая nullable-policy probe: DROP/restore NOT NULL только test users,
восстановление в finally. Поля/проверки отказов не исключались. v3 smoke32 GREEN,
затем v5 полный RED/0 errors на unchanged product. Test XML/logs и
версии fixture/operator сохранены. Maven wrapper/JDK17, verified R227 cache
1368 Java inputs/2221 class hashes; изменённые main/test sources перекомпилированы.
Native PostgreSQL16.15/loopback54167/fresh isolated schemas, ignored R200 JDBC
supplier с SHA/version/exact-directory guards; tracked supplier не менялся.
Все83 migration sources прежние; миграции выполнены только в test schemas.
Docker/containers не запускались, owned PostgreSQL остановлен, artifacts сохранены.

URL/Redis envelope/public payload/UI/JS/schema/deps/flags/admin policy unchanged.
Detached sandbox, source/class/case/report/log hashes, exact scope/replay/inverse/
diff proof. Root apply отдельно от staging/commit/push; JS не rerun.

Source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний документированный
runtimeR223: webR222/workerR198/foundationOFF/readiness200UP/health503DOWN из-за
прежнего inboundDLQ1. Полные R184/R198/R204/R207/R216/R223 runtime/rollback/V83
blocks сохранены. Working DB/deploy/restart/flags/provider/queue не затрагивались;
V83 не повторять. R224–R226 и R228 требуют будущего panel-web image; R227 tests/docs
отдельного rollout не требует. Никакого подтверждения развёртывания R228 здесь нет.

Этот пакет закрывает identity binding 29 actor-facing входов GENERAL work/write/SSE,
а не весь аудит. Далее — post-entry/atomic authorization admin GENERAL grant CRUD,
сквозные child/media/edit/feedback/realtime boundaries, PENDING product policies
для task/project/system/global и AI privacy/context/retention/delivery,
совместимый fleet/flags/S6 acceptance и операционный DLQ gate. S2B снятR190.
Общий статус — 🟡. Commit/push разрешены до конца29 октября Europe/Moscow;
отдельный publisher проверяет expiry/scope/content после root apply.
Git-факты отражаются только после выполнения в r228-result/final.json.
