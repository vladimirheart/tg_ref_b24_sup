# Автоматическая подготовка инфраструктуры ботов — 01-288

После выпуска backend с V94 добавление Telegram/MAX/VK-канала в режиме
`rabbitmq` автоматически готовит отдельный SQL login, RabbitMQ user, две
очереди, bindings и минимальные permissions. Запуск процесса остаётся отдельной
кнопкой «Старт». Подготовка также выполняется перед ручным стартом, перезапуском
и штатным автозапуском bot-runner. Дополнительная сборка на каждый канал не нужна.

## Однократная конфигурация при выпуске

- Обновить panel-web и bot-runner согласованно; ops-worker использует тот же
  backend artifact. Backend Flyway применяет V94. Bot JARs не меняются.
- Владелец функции `bot_provision_child_transport` должен иметь возможность
  `CREATE ROLE` и выдавать перечисленные в функции grants на transport tables.
  При обычном Compose это владелец БД, которым выполняются миграции. Runtime
  backend не нужно отдельно наделять глобальным CREATEROLE, если migration owner
  отличается: администратор выдаёт ему EXECUTE только на эту функцию и обычный
  доступ backend к новой таблице `bot_child_infrastructure`. PUBLIC и children
  не имеют доступа к функции или таблице. Функция имеет фиксированный search_path,
  допускает только положительный ID активного canonical channel и ограниченный
  `iguana_child_<id>` login; произвольный SQL/роль/набор grants через неё не задаются.
- RabbitMQ management plugin должен быть доступен backend и bot-runner.
  `APP_BOTS_INFRASTRUCTURE_RABBITMQ_MANAGEMENT_URL` задаёт базовый URL без `/api`.
  Compose по умолчанию использует `http://rabbitmq:15672`, локальный запуск —
  `http://${SPRING_RABBITMQ_HOST}:${APP_RABBITMQ_HTTP_PORT:-15672}`.
- По умолчанию management API использует серверные `SPRING_RABBITMQ_USERNAME`
  и `SPRING_RABBITMQ_PASSWORD`. Они должны разрешать управление пользователями
  и ресурсами данного broker/vhost. Для отдельного администратора доступны
  `APP_BOTS_INFRASTRUCTURE_RABBITMQ_MANAGEMENT_USERNAME` и
  `APP_BOTS_INFRASTRUCTURE_RABBITMQ_MANAGEMENT_PASSWORD`.
- Используются те же точные master/signing secrets, vhost и названия exchanges,
  что у backend/launcher. Существующие backend exchanges создаёт штатный RabbitAdmin;
  подготовка канала их не заменяет. Management credentials не передаются child.

## Ошибки и повтор

Канал сохраняется до подготовки. Ответ `POST /api/channels` содержит
`success=true`, `channel` и `infrastructure.state/message`. `ready` означает
готовность новых доступов, `existing` — ранее подготовленный вручную канал,
`not_required` — другой transport mode, `failed` — подготовка не завершилась.
UI показывает предупреждение и предлагает повторить «Старт»; повторно добавлять
канал не требуется. Пока форма ждёт ответ, отображается spinner и исключён
повторный submit. Просмотр списка и статусов ничего не подготавливает.

SQL identity и журнал фиксируются одной транзакцией до HTTP-запросов. Следующий
этап удерживает блокировку строки журнала и shared lock canonical channel,
поэтому web/runner replicas не готовят один канал параллельно. При частичном
сбое данные и очереди не удаляются. Повтор использует сохранённый salted broker
hash, проверяет существующие ресурсы и добавляет только отсутствующие. Если
ответ на создание пользователя потерян, его создание не повторяется при
совпадающем hash. SQL пароль в журнал не записывается.

Чужая SQL role без binding/journal, изменённые SQL identity/master, отозванный
principal, административные broker tags, другой broker password hash, расширенные
permissions, чужие bindings или несовместимые очереди останавливают подготовку.
Автоматического присвоения, ротации, снятия отзыва или удаления сообщений нет.
Ранее вручную подготовленные каналы не усыновляются и не меняются; для них
сохраняется существующий startup principal gate. Изменение platform или secrets
действующего канала по-прежнему требует отдельной контролируемой процедуры.

Ошибки SQL/HTTP не возвращаются в UI и логи вместе с credentials или statements;
оператор видит этап PostgreSQL/RabbitMQ. Проверять нужно endpoint/доступы,
canonical channel, journal, атрибуты role и точные broker resources. Не включать
логирование bind parameters или DDL с паролями. Management HTTP timeout 3 секунды
на запрос и 30 секунд на этап; ожидание SQL locks ограничено 5 секундами.

SQL права child ограничены V86 transport tables и разрешёнными колонками receipt.
Rabbit configure=`^$`, write — только inbound exchange, read — только собственная
outbound queue, без tags. Удаление канала удаляет binding/journal, но cleanup
SQL/Rabbit identities и очередей остаётся административной операцией после drain.

## Проверки

`ChildInfrastructurePostgresTest` использует собственную nonce-checked БД:
Docker PostgreSQL16 по умолчанию либо `-Dinfra288.jdbc=jdbc:postgresql://127.0.0.1:<port>/infra288`
и `-Dinfra288.nonce=<32 hex>`. Native кластер должен быть одноразовым, с пользователем
`infra_test`, trust на loopback только для test-admin (children — SCRAM), портом выше1024 кроме5432 и таблицей
`infra288_owner(nonce text)` с этим nonce. Рабочий datasource не загружается.
Broker проверки `ChildRabbitProvisionerTest` используют только stateful loopback
HTTP double. Это не приёмка настоящего RabbitMQ или сообщений Telegram/MAX.

HTTP contract сверяется с [RabbitMQ HTTP API](https://www.rabbitmq.com/docs/http-api-reference)
и [реализацией user endpoint3.13](https://github.com/rabbitmq/rabbitmq-server/blob/v3.13.7/deps/rabbitmq_management/src/rabbit_mgmt_wm_user.erl).
