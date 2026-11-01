# Изолированная приёмка доставки feedback

Модуль включается явно и исключён из обычного reactor `java-bot`. Сначала нужно
скомпилировать Java-классы панели из **того же checkout**, затем использовать
репозиторный Maven wrapper с профилем `feedback-chain-acceptance`, выбором
`:bot-feedback-acceptance` и `-am test`. Модуль не копирует Sass/assets,
application YAML, секреты или executable panel JAR. Это не runtime-артефакт:
его нельзя включать в образы развёртывания.

Тестовый classpath использует `spring-panel/target/classes/com/example/panel`
и reactor `bot-core`. Без сборки панели компиляция падает; подмена устаревшими
или непроверенными классами запрещена. При ограниченных ресурсах допустимо
переиспользование артефактов только с зафиксированным соответствием исходников
и bytecode точному baseline.

По умолчанию fixture создаёт собственные одноразовые PostgreSQL 16 и RabbitMQ
3.13 через Testcontainers: лимиты 192/256 MiB, по одному CPU. Docker должен
работать; недоступная инфраструктура приводит к ошибке, а не пропуску тестов.
Тестовые доступы — публичные фиктивные значения, не связанные с рабочими.

Для малоресурсного оператора можно одновременно задать
`FEEDBACK_CHAIN_PG_JDBC_URL` и `FEEDBACK_CHAIN_AMQP_PORT`, указывающие на отдельно
созданные **одноразовые контейнеры, принадлежащие только этому тесту**. URL БД:
`jdbc:postgresql://127.0.0.1:<port>/r259_chain`, AMQP — нестандартный loopback-порт;
оба сервиса используют `r259_test` / `r259-test-only`. Другие overrides не
принимаются, конфигурация приложения не загружается. Оператор отвечает за
удаление этих контейнеров и сброс тестовой БД перед повторным прогоном. Fixture
создаёт таблицы/триггеры и удаляет свои canonical queues; направлять её на
существующий стенд или делить broker с другим прогоном запрещено.

Проверяемая реальная цепочка:

`panel outbox + V84 context -> publisher confirms -> RabbitMQ -> production
listener/converter -> persistent bot ledger -> signed recipient HTTP -> signed
sender HTTP -> MessagingService -> loopback provider HTTP`.

Recipient SQL, JPA-репозиторий каналов, credential resolver, HMAC guard и оба
HTTP-клиента — production-код. HTTP-мост вызывает реальные контроллеры через
standalone MockMvc с UTF-8, соответствующим servlet-кодировке Boot. Это не полная
приёмка Spring Security/TLS/proxy. Файловый каталог credentials и чтение канала
ботом — управляемые тестовые границы, не приёмка launcher/API. Провайдер —
локальный имитатор, не Telegram/VK/MAX SDK и не реальный клиент.

Матрица: BUSINESS, GENERAL, SHARED_GENERAL и legacy без привязки на трёх
платформенных маршрутах; точные Unicode/raw ID и bigint выше 2^53; исключение
служебного snapshot из wire; подтверждённая доставка; replay с persistent ledger
после перезапуска потребителя; expiry/root ABA/отзыв scope/канала/credentials,
ротация, неверная подпись и недоступный API; подмена child/prompt/user; явный
повтор после **известного отказа** провайдера.

Не доказаны exactly-once, конкурентный захват failed/stale ledger, восстановление
при неизвестном результате провайдера, полный перезапуск процесса, клиентский
rating callback, live-доставка, fleet isolation или приёмка S6. Рабочие feature
flags, пользователи, права, схемы и очереди не меняются.
