# 01-278 / R278 — подтверждённая подсказка восстановления MAX

Промт пользователя:

> продолжай

Дата:2026-11-03, Europe/Moscow +03.

MAX подтверждает restart_questionnaire только после подтверждённой отправки
подсказки о /start. Общая проверка используется при недоступном/изменённом catalog,
выборе BUSINESS/GENERAL, восстановлении legacy session и финализации вопросника.
Warning false/exception, повторная ошибка channel resolution или markProcessed
освобождают claim и допускают повтор того же события. Исключение отправки не
переинтерпретируется как новая ошибка runtime-config с повторным warning в том же
запросе. В outer catch добавлено явное освобождение: sibling RuntimeException
catch не обрабатывает исключения, брошенные внутри другого catch. Текст подсказки
и успешный HTTP200/restart_questionnaire contract сохранены.

540 tests/26 suites GREEN:502 MAX/23 (все386 прежних +116 новых),38 core/3.
Frozen baseline RED:116 cases,79 assertion failures/37 controls,errors/skips0.
Тот же test source,2 class hashes и case names exact после исправления.23 сценария
включают bootstrap, legacy restore, BUSINESS v1–v4/GENERAL v2–v4 menu choice,
restored complete session и новый последний ответ; false/exception/scoped-exception/
dedup failure/retry/duplicate, плюс отдельный повторный channel resolution failure.
Real handler/JSON/local CAS и delivery guard, mocked runtime/provider; последние
ответы и frozen scope/journal сохраняются без повторной записи ответа или заявки.
Положительные controls сохраняют прежний результат после доставленного warning.
Это не real Redis eviction/rebind, live SDK/customer acceptance или полный S6.

Fresh HEAD/origin/main/actual remotedb5859a36 (R277), parent14c00a87d;6 prior
paths/content verified, checkout clean. Detached worktree и pinned R277 cache
(2677 classes), Maven wrapper/JDK17/bot-max -am. Scope6:1 main,1 new test,
task/S0/BOT/new append-only changelog. Existing tests/POM/resources/schema/flags/
session JSON не меняются. Sandbox validation и root apply раздельны; exact scope,
content/class/report hashes, inverse source plan и runtime history проверены.

Source/test machine:C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр
на другой машине и в другой сессии; local Docker/PG/Redis/Rabbit/provider здесь
не запускались, рабочая среда не измерялась и не менялась. Последний записанный
runtime R275/R274/R272 относится к исходной машине; это не свежая проверка здесь.
R277 уже опубликован; R278 source-only, stage/commit/push/deploy не выполняются.
Для будущей выкладки нужен MAX child в bot-runner и backend context R276;
panel-web-only не активирует fix. Новых migrations/flags нет, V83/V84/V85 не
повторять; backup/rollback/private DLQ archive сохранены. Scoped MAX byte writer,
полный Redis/live gate, legacy isolation, остальные permissions/policies/AI/S6
остаются. S2B снятR190, DLQ gate закрытR275, общий статус задачи🟡.

Изменённые файлы:

- `ai-context/changelog/2026-11-03_01-278_max_restart_confirmation_r278.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxQuestionnaireRestartDeliveryTest.java`
