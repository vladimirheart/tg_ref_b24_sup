# 01-211 - preserve loopback ingress redirect port

## Промт пользователя

`запустил сервис. иду на https://127.0.0.1/, но страница не открывается`

## Что изменено

- В `docker/nginx/panel-direct.conf` сохранён исходный loopback host header для проксирования в `panel-web`.
- Добавлен расчёт `X-Forwarded-Port` из входящего `Host`, чтобы loopback ingress не терял нестандартный внешний порт `8080`.
- Для `panel-direct` отключён автоматический `proxy_redirect`, потому что именно он переписывал корректный upstream redirect `http://127.0.0.1:8080/login` в битый `http://127.0.0.1/login`.
- В `spring-panel/src/test/java/com/example/panel/runtime/DockerProductionRoleTopologySourceContractTest.java` добавлены source-contract проверки на новый ingress contract.
- В `ai-context/tasks/task-details/01-211.md` зафиксированы причина инцидента, исправление и живая проверка.

## Проверка

- `curl -I http://127.0.0.1:8080/` -> `Location: http://127.0.0.1:8080/login`
- `curl -I http://127.0.0.1:8080/login` -> `HTTP/1.1 200`
- `docker compose ... restart panel-direct`
