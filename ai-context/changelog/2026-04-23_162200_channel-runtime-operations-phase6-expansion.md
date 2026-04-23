# 2026-04-23 16:22:00 — channel runtime operations phase 6 expansion

## Что сделано
- Расширен `ChannelApiControllerWebMvcTest` на runtime-операции каналов.
- `test-message` теперь прикрыт сценариями:
  `404`, non-telegram guard, missing recipient guard и successful send
  в `group/channel` с deduplication.
- `refresh bot info` теперь прикрыт сценариями:
  `404`, non-telegram guard, failure-path через `Telegram getMe`
  и successful persistence `bot_name/bot_username`.
- Синхронизированы `ARCHITECTURE_AUDIT_2026-04-08.md`,
  `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и `01-024.md`.

## Проверка
- `spring-panel\mvnw.cmd -q -Dtest=ChannelApiControllerWebMvcTest test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

