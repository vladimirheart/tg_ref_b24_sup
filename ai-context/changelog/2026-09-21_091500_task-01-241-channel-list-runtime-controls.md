# 01-241: lifecycle controls and compact platform disclosure

Дата: 2026-09-21

## Контекст

Во время live acceptance нового `panel-web -> bot-runner` lifecycle boundary Telegram channel 3 был фактически `running`, но таблица каналов всё равно показывала только кнопку `Запустить`. Проверка была остановлена до любого stop/start действия.

## Изменения

- runtime status строки теперь управляет lifecycle-кнопками: для `running` доступна `Остановить`, для `stopped/error` — `Запустить`; во время первичной проверки Start остаётся disabled;
- list-level Stop использует тот же `/api/bots/{channelId}/stop` путь и поэтому проходит через подтверждаемый `panel-web -> bot-runner` command boundary из основного slice 01-241;
- колонка `Платформа` сведена к двум компактным строкам с ellipsis; полный текст доступен через hover/title и через кнопку `i`, которая раскрывает подробности inline;
- ширины таблицы изменены с 50/25/25 на 34/42/24, чтобы убрать пустое место из названия и дать platform summary больше полезной ширины;
- добавлен source-contract test, фиксирующий status-aware lifecycle controls и disclosure contract.

## Приёмка

После source validation/tests/rollout повторить live acceptance channel 3: `running -> Остановить -> stopped -> Запустить -> running`, проверить смену PID и отсутствие нового `409 Conflict`.
