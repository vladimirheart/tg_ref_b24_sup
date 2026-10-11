# 01-278: идентичность обращения в bot-runtime и связанных записях (R16)

Дата: 2026-10-11.

Промт пользователя: `прододжай`

## Фактические изменения

- `BotRuntimeTicketReadService` проверяет единственный canonical ticket до
  выдачи active ticket, последнего контекста, недавних обращений и pending
  feedback request. Числовой пользователь должен совпадать с root; при
  заданном канале проверяется и он. Недопустимые recent rows отбрасываются
  до limit и запроса feedback; чужая пользовательская оценка не раскрывается.
  Fallback feedback request не пересекает заданный channel scope.
- `BotRuntimeTicketWriteService` проверяет unique root и согласованность
  пользователя/канала feedback request до чтения или сохранения оценки,
  UI outbox и истечения request. Несогласованная уже существующая оценка
  не перезаписывается. Истёкший корректный request сохраняет прежний no-op.
- Client edit требует ровно одну строку истории по
  `(channel_id, tg_message_id, sender=client)` и согласованный unique root.
  UPDATE выполняется по внутреннему `chat_history.id` с повторными
  predicates идентичности; отсутствующие/duplicate locator, root collision
  и чужой user/channel не меняют историю и не публикуют событие.
- Reply target выбирается только из согласованной с canonical ticket пары
  user/channel. Прямые outgoing text/media writes требуют unique legacy ID
  до записи истории и attachment metadata. Operator edit/delete не меняют
  строки истории с чужим canonical user/channel.
- Убрана обрезка legacy ID в затронутых read/write, participant, delivery
  ledger, UI outbox, realtime dispatch и SSE payload paths. Проверенный
  точный ID сохраняется до связанных записей; ` T-EXACT ` и `T-EXACT`
  не объединяются. Нормализация username, текста и event type не менялась.

## Проверки

- Fresh baseline `HEAD=origin/main=fe45deab061fd31eb6871bf39ea29d72f4a74408`.
  Предыдущий R15 проверен по SHA, parent, message и изменённым файлам.
- Изменения сначала применены в detached temporary worktree от baseline.
  Maven wrapper: `126/126` targeted tests в 14 классах, без failures,
  errors или skipped; в том числе `43` PostgreSQL bot-runtime write tests,
  controller WebMvc, shared guard/resolver и source contracts.
- `PanelApplicationTests`: `1/1`, полный Spring context и существующие
  Flyway V1–V52 успешно применены только во временной PostgreSQL
  Testcontainers-схеме.
- `git diff --check` и exact changed-file set проверены до применения;
  основной checkout получает те же validated patches с последующей
  сверкой содержимого с sandbox с учётом LF/CRLF.
- Фактический scope: 8 production services, 7 test files, task details
  и этот новый append-only changelog. Generated CSS не входит в пакет:
  Maven изменил только EOL checkout, semantic diff отсутствует.

## Границы

- Это source-only S2A hardening, не завершение всей 01-278; статус остаётся
  🟡. Рабочая/production БД, migration/backfill, feature flags, deploy,
  staging, commit и push агентом не выполнялись.
- Исторические channel mismatch не переписаны и не классифицированы;
  новые write guards отклоняют неподтверждённый контекст. Business owner
  не вычисляется из user/channel: canonical business assignment и строгий
  бизнес-scope здесь не включаются.
- R12 task provenance остаётся `PROPOSED_NOT_APPROVED`; S2B approvals,
  approved aliases, business role matrix, shared-channel decisions и
  последующие S2B–S6 остаются за границей этого пакета.

## Изменённые файлы

- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeTicketReadService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogParticipantService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTargetService.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeTicketWriteService.java`
- `spring-panel/src/main/java/com/example/panel/service/ProviderDeliveryLedgerService.java`
- `spring-panel/src/main/java/com/example/panel/service/UiEventOutboxAppendService.java`
- `spring-panel/src/main/java/com/example/panel/service/UiEventStreamService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogRealtimeEventService.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeTicketReadServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeTicketWriteServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogReplyTargetServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogParticipantLegacyTicketGuardTest.java`
- `spring-panel/src/test/java/com/example/panel/service/UiEventOutboxAppendServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/UiEventStreamFanoutTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogRealtimeEventServiceTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-11_122106_01-278_bot_runtime_ticket_identity_guard.md`
