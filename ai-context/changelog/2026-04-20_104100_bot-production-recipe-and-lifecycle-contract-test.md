# Bot production recipe and lifecycle contract test

- runtime contract расширен production/lifecycle секциями: preferred production launcher, recommended artifact path, production readiness и blocking reasons;
- в `app.bots` добавлены `preferred-production-launcher` и `recommended-artifact-directory`;
- `BotProcessService` открыт для controlled test override рабочего каталога без изменения боевого runtime behavior;
- добавлен runnable probe app и `BotProcessLifecycleContractTest`, который проверяет полный цикл `start -> readiness -> status -> stop`;
- `BotRuntimeContractServiceTest` и `BotProcessApiControllerWebMvcTest` расширены под production/lifecycle payload;
- `docs/BOT_RUNTIME_CONTRACT.md` дополнен production recipe и lifecycle contract test описанием;
- roadmap обновлён под новый прогресс `Phase 5` и `Phase 6`.
