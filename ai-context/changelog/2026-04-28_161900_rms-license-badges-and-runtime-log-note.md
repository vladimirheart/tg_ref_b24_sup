# 2026-04-28 16:19:00 — Видимое отображение лицензий RMS и примечание по runtime-логу

## Что изменено

- В общем списке RMS type-specific лицензии переведены из малозаметного текста в отдельные бейджи:
  - основная лицензия по `server_type`
  - дополнительный `iikoConnector for Get Kiosk` для `IIKO_RMS`, если найден

## Что показал лог

- В `logs/spring-panel.log` есть запись:
  - `2026-04-28 16:17:15 ... RMS license refresh failed for https://bb-chain.iiko.it: Не найдена лицензия RMS (Front Fast Food) с id=100`
- Это подтверждает, что в тот момент у пользователя ещё работал старый runtime, потому что в актуальном исходнике `IIKO_CHAIN` уже должен проверяться по `FullEdition (Server)` с `id=10`.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-061.md`
- `spring-panel/src/main/resources/static/js/rms-monitoring.js`

## Проверка

- `spring-panel\\mvnw.cmd -q -DskipTests compile`
