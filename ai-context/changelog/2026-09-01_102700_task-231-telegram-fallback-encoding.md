# 2026-09-01 10:27:00 - task 01-231 Telegram fallback encoding

## User prompt

> диалог приходит, текст есть, но при отправке стикера ничего нет, а клиенту в ответ прилетает "��������� ������� ���. �������� ���������, � � ������ ����� ���������.", хотя потом всё заработало. то есть смущает битая кодировка

## Summary

- Restored the readable UTF-8 fallback used when an attachment arrives before the bot resolves an active dialog.
- Added a regression test for the missing-dialog response.
- Found and restored two additional executable Telegram bot strings: the default greeting and `ё/е` alias normalization.
- Added `01-232` after discovering recurring Telegram `409 Conflict` errors from competing long-polling processes; the runtime restart is intentionally deferred until duplicate ownership is resolved.
- Moved `01-231` to `🟣` for manual verification.

## Verification

- `rg "�" java-bot/bot-telegram/src/main ...`: no replacement characters remain in Telegram bot source.
- `java-bot\\mvnw.cmd -pl bot-telegram -am -Dtest=SupportBotTest,SupportBotChoiceInputContractTest -Dsurefire.failIfNoSpecifiedTests=false test`: 6 tests passed.

## Files

- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
- `java-bot/bot-telegram/src/test/java/com/example/supportbot/telegram/SupportBotChoiceInputContractTest.java`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-231.md`
- `ai-context/tasks/task-details/01-232.md`
