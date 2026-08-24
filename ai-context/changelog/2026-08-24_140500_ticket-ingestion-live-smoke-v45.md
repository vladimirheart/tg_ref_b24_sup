# 2026-08-24 14:05 — ticket ingestion live-smoke fix v45

## User report

После нового question-flow бот подтвердил обращение неверным UUID-номером, но обращение не появилось в панели, notification/bell отсутствовал, AI auto-reply не сработал.

## Evidence

- bot создал технический ticket UUID и опубликовал `ticket.created.initial_contact`;
- panel Rabbit listener упал на PostgreSQL inbox: `event_id` был UUID, а Java передавал String;
- затем recovery-query получил SQLSTATE 25P02 из-за aborted transaction;
- dialog list остался на прежнем количестве записей;
- AI runtime не вызывался, потому что canonical `chat_history` не был создан.

## Changes

- PostgreSQL inbox event id нормализован к TEXT;
- duplicate claim переведён на `ON CONFLICT DO NOTHING`;
- client-facing request number ждёт canonical panel value bounded polling;
- UUID fallback удалён из Rabbit confirmation flow Telegram/MAX/VK;
- добавлены transport/number regression tests;
- заведена 01-185; 01-184 остаётся на повторный AI live smoke после восстановления transport path.