# Fix MAX idempotency build

## Пользовательский запрос

Пользователь передал ошибку Docker-сборки после применения патча.

## Фактические изменения

- Исправлено использование JsonNode-only helper для MAX message ID.
- Исправлен порядок аргументов общего inbound command: имя вложения и provider message ID передаются в соответствующие поля.
