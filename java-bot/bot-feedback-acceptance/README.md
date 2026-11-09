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
повтор после отказа **до вызова провайдера**.

R260 дополнительно проверяет конкурентный захват одной retryable-генерации на
реальном PostgreSQL (барьер после чтения снимка, пять повторов), сохранение
границы отправки при rollback вызывающего кода и отсутствие повторной отправки
после provider false/потери HTTP-ответа/сбоя записи результата. Для нового ledger:

| Состояние | Значение | Повтор без сверки с провайдером |
| --- | --- | --- |
| `processing` | Попытка захвачена, граница отправки ещё не подтверждена | Запрещён, даже после таймаута |
| `retryable` | Новый код зафиксировал отказ до провайдера | Допустим явный replay с новой авторизацией |
| `sending` | Граница перед провайдером сохранена независимо от caller transaction | Запрещён |
| `uncertain` | После границы нет достоверного результата | Запрещён |
| `delivered` | Провайдер вернул успех, результат сохранён | Запрещён |
| Старый `failed`/неизвестный статус | Нет доказательства, что провайдер не вызывался | Запрещён |

Boolean false и даже тестовый HTTP503 после handoff не являются универсальным
доказательством недоставки для Telegram/VK/MAX SDK. Прежняя R259 проверка повтора
после такого результата заменена более строгой: неопределённый исход блокирует
replay; отдельно доказан безопасный повтор после pre-provider API503.

R263 добавил private V85 receipt journal: committed nonce до SDK и положительный
provider message ID до final delivered. `FeedbackReceiptRecoveryTest` проверяет
восстановление по сохранённой квитанции после service/consumer restore, без
нового admission/SDK; потерянный ответ без квитанции остаётся blocked. Журнал
не является подтверждением чтения клиентом и не открывает повторную отправку.

## R265: аварийное завершение отдельной delivery JVM

`FeedbackProcessRestartTest` запускает test-only `FeedbackProcessWorker` в
отдельной JVM с heap96/Metaspace96MiB. В ней работают production listener,
dispatcher, ledger/receipt transactions, signed HTTP clients и MessagingService.
Барьеры установлены **после возврата настоящих committed операций**, без
подмены их результатов. Родитель проверяет БД и принудительно завершает процесс
через `Process.destroyForcibly()`, не выполняя graceful stop/закрытие consumer.

RabbitMQ сам возвращает исходное unacked-сообщение в очередь. Проверяются
ready1/consumers0 перед новым процессом, другой PID, `redelivered=true`, точный
SHA256 исходного payload и финальный ACK после graceful stop второго процесса.
Тест не вызывает fixture replay/republish для восстановления. Текущие канал и
credentials отзываются, signed API отвечает503: восстановление исторического
факта не может стать новым разрешением доставки.

| Остановка после | До kill: provider calls | Durable состояние | После redelivery |
| --- | --- | --- | --- |
| Claim | 0 | processing, journal отсутствует | blocked, строки прежние |
| Prepare | 0 | sending, prepared без acceptance | blocked, строки прежние |
| Ответа provider | 1 | sending, acceptance ещё не сохранён | blocked, строки прежние |
| Записи receipt | 1 | sending + committed acceptance | delivered, без нового provider/admission |
| Записи delivered до ACK | 1 | delivered + acceptance | точные строки и timestamps прежние |

Матрица:4 scope ×3 platform routes ×5 crash points =60 сценариев. Ещё8 проверок
отклоняют рабочие/non-loopback endpoints, public schema, стандартные DB/AMQP
порты и неподдерживаемые route/mode до соединения;1 smoke. Вместе с прежней
регрессией —323 выполнения/4 suites, включая5 повторов существующей ledger race.
Source и replay проверяются независимо, их результаты не суммируются.

Дочерний процесс не наследует Spring/provider/datasource environment, не читает
application YAML или shared-config. Разрешены только isolated loopback endpoints,
owned schema и фиксированные фиктивные credentials. Main/product код не меняется,
выпуск test-only модуля в runtime image запрещён; rebuild/restart стенда не нужен.

## R266: сохранение ответа на запрос оценки

`FeedbackRatingCallbackTest` связывает доставленный prompt с общим bot
`FeedbackService`, настоящими signed HTTP read/write clients и backend
controllers/services. Pending/feedback JPA repositories и native V84 context
locks работают на том же owned PostgreSQL; Spring AOP/JpaTransactionManager
применяет реальные `@Transactional`, JDBC UI outbox участвует в той же транзакции.
Узкий ticket repository adapter читает фактические owner rows через JDBC;
это не полная JPA-модель Ticket и не реальный platform SDK callback.

241 новый сценарий на4 scope ×3 outbound routes: round trip/дубликат,8 изменений
контекста после чтения (expiry, root recreate, business/channel/general/user/
public-ID ABA, duplicate raw ID), ratings0/6, потеря commit response/idempotent
HTTP retry,3 отказа записи (rating/UI event/expiry), конкурентные replies,
HMAC401/API503, wrong-user/channel lookup и1 smoke. Проверяются точный Unicode
raw ID/bigint, один feedback/один scoped UI event, expiry, reload и отсутствие
новой отправки провайдеру. Это495 integration executions +6 core/5 suites;
source/replay независимы. R265 kill suite здесь повторно не запускается.

Production fix: `FeedbackService.storeFeedback` в RabbitMQ mode теперь бросает
исключение, если backend не подтвердил запись. Telegram/VK/MAX вызывают success
ack только после нормального возврата сервиса; false/expired/denied/unknown HTTP
не должны превращаться в «Спасибо»/`feedback_saved=true`. Void API/успешный путь
и legacy JDBC path прежние. Новый unit сначала RED, затем все6 GREEN.
Это не доказательство, что неизвестный исход был отклонён: положительная запись
без ответа может существовать, поэтому сообщение об успехе не выдумывается.

Fixture добавляет JavaTimeModule (как Boot) и сохраняет JSON response bytes;
default MockMvc ISO-8859-1 string decode не используется для Unicode ID.
Ошибки SQL P0001 и HTTP401/500/503 здесь намеренно injected в disposable schema,
не ошибки рабочего стенда. Рабочие миграции/config/данные не затрагиваются.
Acceptance module по-прежнему не runtime artifact; **изменённый bot-core требует
отдельного clean-commit runner release**, новых миграций/flags нет. Runtime R264
пока остаётся прежним, тесты сами deploy/restart не выполняют.

Не доказаны exactly-once, разрешение unknown без квитанции, startup/batch recovery,
live platform rating callback, live SDK/доставка, restart штатного bot application/
launcher/всей fleet или приёмка S6. Это реальный process-kill production delivery
компонентов, но не full-stack Spring/TLS/SDK acceptance. Рабочие flags, пользователи,
права, схемы и очереди не меняются. Старые children нельзя смешивать с новой ledger
логикой; выпуск/откат требуют отдельного schema/backup/cold-fleet gate.

## MAX followup media: текущий запуск R298 (исходная матрица R287/R292)

Подготовлены42 media +15 principal случаев PostgreSQL/Rabbit: published BUSINESS/GENERAL batch,
whole-batch rollback, duplicate/requeue после committed write, real advisory
quota race и PostgreSQL JSONB outbox lookup. Fixture использует narrow JDBC
repository adapters и production services/transactions; это не полный JPA/Boot,
MAX SDK, Redis session, process kill или live acceptance.

R297 на другой runtime-машине:34 contracts и15 Docker principal IT GREEN,
MAX42 не стартовали из-за internal-only port publication. R298 исправляет fixture:
69 local contracts/3 suites GREEN, включая35 новых topology/inspect cases. Docker
здесь недоступен, исправленный MAX42 ещё **не выполнен**. Это не runtime change.

Нужны JDK17 в PATH и запущенный локальный Linux Docker engine. После commit/push
и обновления checkout, из корня репозитория:

```powershell
powershell -NoProfile -File .\java-bot\bot-feedback-acceptance\run-max-media-acceptance.ps1
```

Оператор собирает отдельный detached checkout, сначала панель и69 contracts,
затем раздельно42 media и15 principal IT (API1.44 для Docker29). Application YAML/.env/Compose/working targets не используются.
Endpoint overrides старого feedback fixture не читаются: только собственные
nonce-labelled PostgreSQL/Rabbit/internal service network, отдельный ограниченный
Socat relay/ingress network, random loopback ports только relay на127.0.0.1 и
фиктивные credentials. Test images могут скачиваться при первом запуске.
Прежние containers/config hashes проверяются после cleanup.

Логи, включая failed/startup XML, и `r292-proof.json` (Phase=R298) останутся в новом `.tmp/r287-acceptance-*`.
Успех полного запуска: `INTEGRATION_GREEN`,57 IT (42+15), failures/errors/skips0,
`IntegrationPassed=true` и `ExistingContainersUnchanged=true`. Ошибка Docker
не становится skip/success. `-ValidateOnly` запускает только compile/contracts
и сохраняет `IntegrationPassed=null`; `-SelfTest` не запускает builds/Docker.
Оба режима не заменяют интеграционный результат. Полный IT — отдельный явный
`-Dtest=MaxFollowupMediaChainIT` с nonce от оператора; обычная сборка его не запускает.
