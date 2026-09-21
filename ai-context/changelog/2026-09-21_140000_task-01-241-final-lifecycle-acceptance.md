# 01-241 — final production lifecycle acceptance GREEN, status 🟣

Дата: 2026-09-21 14:00 +03:00
Задача: 01-241

## Итог

Ручной production lifecycle smoke завершён успешно через тот же UI path, которым оператор управляет ботами.

## Подтверждённый lifecycle

- channel: `3` / Telegram;
- UI STOP acknowledgement: GREEN;
- old PID: `338` -> terminated;
- shared status after STOP: `stopped`;
- PID file after STOP: absent;
- UI START acknowledgement: GREEN;
- new PID: `11356` -> alive;
- new PID differs from old PID: true;
- final owner count in `bot-runner`: `1`;
- owner count in `panel-web`: `0`;
- owner count in `ops-worker`: `0`;
- current process-log Telegram `409 Conflict` count: `0`.

## Runtime preservation

- `panel-web` preserved: `d1e8a0a82eff5d1a4864b1af2ab9c756974a581752c6a4002a70ebae17376463`;
- `panel-web` image: `sha256:80c8b9f308dd519f11dadca7c7f80fdb8433ca07f2642ce0e76930da9e320d91`;
- `bot-runner` preserved: `376297bbc7e46531ded11700fa47887cd567e257ecc1d490e9913a82155e1242`;
- `ops-worker` preserved: `5c44fb563e5771a7530cef7974b5b64bc144af239c1ae87acbf8b0eb615d518b`;
- `panel-direct` preserved: `aed39c5fa27ecc479fb2e56af52c0ebfa83ca197e9e90aece32333945f309355`;
- legacy static bots running: `0`;
- mixed ownership: `false`.

## Acceptance probe correction

Первый post-START log guard остановился не из-за runtime-дефекта, а из-за неверного предположения о том, что process log append-only. `BotProcessService` открывает child stdout через `ProcessBuilder.Redirect.to(...)`, поэтому при новом START файл создаётся заново. Последующий read-only continuation проверил текущий process log напрямую и подтвердил `409 count = 0`.

## Статус

`01-241_FINAL_LIFECYCLE_ACCEPTANCE=GREEN`.

Задача переводится `🟡 -> 🟣`: AI implementation, rollout и live acceptance завершены. Статус `🟢` остаётся исключительно пользовательским.

DB migration: `false`.
Data mutation: `false`.
