# 2026-08-14 11:41:25 - runtime contract postgres readiness diagnostics

## Промпт пользователя
- `давай дельше. шаг выбери сам`

## Что сделано
- в `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java` SQLite runtime-contract явно помечен как `local/dev bootstrap perimeter`, а не как production-ready ownership path;
- для SQLite-режима в `runtime-contract` добавлены warning и production blocker, которые прямо требуют external PostgreSQL datasource contract для production-ready состояния;
- сохранён позитивный production-ready сценарий для explicit jar launcher в external PostgreSQL path без ложных SQLite-warning;
- в `spring-panel/src/test/java/com/example/panel/service/BotRuntimeContractServiceTest.java` добавлены и обновлены проверки для SQLite-perimeter и external PostgreSQL-ready контрактов;
- в `docs/BOT_RUNTIME_CONTRACT.md` зафиксировано, что API-диагностика должна различать local/dev SQLite bootstrap и production-ready PostgreSQL runtime;
- карточка `ai-context/tasks/task-details/01-181.md` обновлена новым фактическим остатком по readiness-части задачи.

## Затронутые файлы
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeContractServiceTest.java`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `ai-context/tasks/task-details/01-181.md`

## Проверка
- `cmd /c "mvnw.cmd -q -Dtest=BotRuntimeContractServiceTest,BotProcessServiceTest test"` (`spring-panel`) - passed
