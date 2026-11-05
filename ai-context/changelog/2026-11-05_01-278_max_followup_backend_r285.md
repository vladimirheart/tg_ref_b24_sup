# 01-278 / R285 — backend MAX published followup media

Промт пользователя:

> проверь репо на изменения и продолжи по задаче

Дата:2026-11-05, Europe/Moscow +03.

Подготовлена backend-часть MAX published followup media. Signed internal
/channels/{channelId}/questionnaire-media/max-batch возвращает Admission version3,
Cache-Control:no-store; TG1/VK2 не являются MAX-допуском. Owner/channel/raw ticket/
BUSINESS|GENERAL/template/catalog/quota читаются из canonical v4 root с прежними
flags, active/status/platform/unique-ID checks; текущие bindings не подставляются.
Consumer принимает только versioned media-batch event, exact deterministic tuple,
positive BIGINT provider ID (включая Long.MAX_VALUE), без forward/reply. Legacy
single-media MAX event по-прежнему не даёт обхода этого gate.

До открытия bytes проверяются все1–10 items: slot/type/safe name и owned
questionnaires/max namespace. Actual bytes1–10MiB/SHA/size проверяются для всего
пакета до первой history/metadata/activity записи. Некорректный второй файл не
оставляет первый history row. Canonical legacy-ID guard/SQL row locks и прежний
markProcessedWithWrites batch contract сохранены; ingress отдельно проверяет
MAX platform канала. TG/VK verifier behavior не расширялся и не заменялся.

315 fresh targeted tests/11 suites GREEN,79 новых (61 verifier,10 ingestion,
8 signed MVC); frozen baseline RED79/29 assertion failures/50 controls,errors/
skips0.3 new sources/4 class hashes/case names exact после product edit.
Real service/row mapper/storage streams/controller/request guard и in-order
ingestion, mocked JDBC ResultSet/repos/object storage. Это не PostgreSQL lock/
rollback/concurrent quota proof, real Redis/Rabbit/S3/MAX provider или S6.
Первый полный test compile выявил неверный TicketId constructor в новом fixture;
исправлен только fixture, failed log/source сохранены. Все прежние tests intact.
Retained R279 cache проверен по hashes; stale Java inputs принудительно
инвалидированы: fresh baseline compile741 main/690 test sources с последующей
докомпиляцией248 test sources после исправления fixture. Не production package.

Fresh HEAD/origin/main/actual remotef93973310 (R284), parentd799f4227;24 prior
paths/content verified, clean root. Detached worktree, Maven wrapper/JDK17;
scope10:3 backend main,3 new tests,task/S0/BOT/new append-only changelog.
POM/resources/schema/flags/bot sources/session JSON не меняются; Sass EOL output
восстановлен вне scope. Root apply отделён от staging/commit/push/deploy.

Source/test machine:C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий Docker и
проект находятся на другой машине и в другой сессии; здесь они не измерялись
и не изменялись. Local Docker/PG/Redis/Rabbit/provider не запускались. R280
release/health, R281 cleanup и остановленные20 containers в R282 — записи
исходной runtime-сессии, не свежие observations этого checkout от5 ноября.
Исторические checkpoints, rollback/backups/private archive не затрагивались.
R285 source-only; новых migrations/flags нет, V83/V84/V85 не повторять.

Клиент MAX ещё не вызывает этот endpoint и не публикует scoped followup batch:
вложения опубликованного root пока явно отклоняются. Следующий шаг — client
admission/owned writer + durable pending batch/CAS/outbox/replay, включая потерю
session. Это отдельный пакет, не закрытый R285. Затем нужны coordinated release,
prepared/outbox drain/rollback и live provider acceptance. Остальные политики/
principals/AI/S6 остаются; S2B снятR190, общий статус задачи🟡.

Изменённые файлы:

- `ai-context/changelog/2026-11-05_01-278_max_followup_backend_r285.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `spring-panel/src/main/java/com/example/panel/controller/SelectedTicketFollowupMediaController.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/InboundClientMessageIngestionService.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/SelectedTicketFollowupMediaService.java`
- `spring-panel/src/test/java/com/example/panel/controller/MaxFollowupMediaControllerTest.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/MaxFollowupMediaIngestionTest.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/MaxFollowupMediaVerifierTest.java`
