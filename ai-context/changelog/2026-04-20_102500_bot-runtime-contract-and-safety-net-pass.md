# Bot runtime contract and safety net pass

- runtime boundary ботов вынесен в отдельный `BotRuntimeContractService`, который централизует launcher plan, env contract и readiness expectations;
- `BotProcessService` переведён на использование runtime contract service вместо локального хранения launcher/env логики;
- в `app.bots` добавлены явные настройки `startup-readiness-timeout` и `startup-poll-interval`;
- добавлен диагностический endpoint `GET /api/bots/{channelId}/runtime-contract` для проверки launcher/env/readiness контракта без запуска бота;
- задокументирован runtime contract в `docs/BOT_RUNTIME_CONTRACT.md`;
- добавлены `BotRuntimeContractServiceTest` и `BotProcessApiControllerWebMvcTest`;
- `DialogsControllerWebMvcTest` усилен smoke-проверкой раннего `ui-head` bootstrap;
- roadmap обновлён под новый прогресс `Phase 5` и `Phase 6`.
