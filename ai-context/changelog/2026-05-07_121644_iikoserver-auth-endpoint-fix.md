# 2026-05-07 12:16:44 - iikoserver auth endpoint fix

## Промпт пользователя

```text
при ручной синхронизации департамантов получаю ошибку: Не удалось загрузить департаменты из iiko https://gk-sv.iiko.it: HTTP 404 при получении access token Не удалось загрузить департаменты из iiko https://bb-chain.iiko.it: HTTP 404 при получении access token
проверь, корректно-ли ты прописал методы работы с api
```

## Что сделано

- Проверен текущий auth flow live-синхронизации департаментов и найдено смешение двух разных API-контуров:
  - в коде использовался `iiko biz/transport`-style endpoint `/api/0/auth/access_token`;
  - для `iikoServer API` переведено на `GET /resto/api/auth`.
- В `IikoDepartmentLocationCatalogService.HttpIikoDepartmentGateway` исправлены query-параметры авторизации:
  - было `user_id` / `user_secret`;
  - стало `login` / `pass`.
- Для `pass` добавлена нормализация секрета под `SHA-1`:
  - если в настройках уже хранится 40-символьный hex `SHA-1`, он используется как есть;
  - если введён обычный секрет, перед запросом вычисляется `SHA-1`.
- Сохранён существующий запрос департаментов через `GET /resto/api/corporation/departments/?key=...`.
- В unit-тесты добавлен реальный HTTP flow через локальный `HttpServer`, который проверяет:
  - auth идёт в `/resto/api/auth`;
  - используется `login=test-login`;
  - `pass` уходит как `SHA-1` хэш;
  - departments читаются через `key=token-123`.
- Задача `01-075` переведена в статус `🟣`.

## Дополнительная проверка

- Прямыми probe-запросами к пользовательским host'ам подтверждено:
  - `https://gk-sv.iiko.it/api/0/auth/access_token` -> `404`
  - `https://gk-sv.iiko.it/resto/api/auth` -> `401`
  - `https://gk-sv.iiko.it/resto/api/corporation/departments/` -> `401`
  - аналогичное поведение у `https://bb-chain.iiko.it`
- Это подтверждает, что проблема была именно в неверном auth endpoint/path, а не в самом факте доступа к server API.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=IikoDepartmentLocationCatalogServiceTest,IikoDepartmentLocationsSyncServiceTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Итог

- Ручная синхронизация департаментов больше не должна ходить в несуществующий `access_token` endpoint на chain-серверах.
- Для `iikoServer API` теперь используется корректный server-auth маршрут и ожидаемый формат параметров авторизации.
