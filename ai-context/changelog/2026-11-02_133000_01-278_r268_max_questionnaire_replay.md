# 01-278 R268: durable event journal выбранного вопросника MAX

Время source/replay проверки: 2026-11-02 13:24–13:35 MSK.

Промт пользователя: «продолжай по задаче».

## Выполнено

- Проверены clean HEAD/origin/main/actual remote
  bfe70a8599ff9c2b4bdf254427bae75a72f0e7df (R267,parentd0b467204), exact scope4
  и applied manifests предыдущего выпуска. Рабочий runner уже R267/R266/image9cc3,
  повторная выкладка не выполнялась.
- Найден product defect MAX: промежуточный ответ durable до prompt/HTTP ACK,
  но event ID отсутствовал в session. Повтор после отказа markProcessed отвечал
  на следующий вопрос и создавал ticket_id=T-MAX. Новый regression RED1/1 failure/
  0 errors → GREEN1, существующие assertions не ослаблялись.
- MaxConversationSession.Selection теперь хранит immutable acceptedEventKeys;
  choice/page/answer/back/skip сохраняют ID тем же CAS, что прогресс. До4096
  уникальных непустых IDs; null/blank/duplicates/overflow и несовместимый новый
  submission fail closed, ключи не вытесняются. Старый JSON без поля читается с
  пустым журналом и прежним scope/progress; неизвестные старые IDs не выдумываются,
  пустое поле не сериализуется. Legacy direct path прежний.
- MaxWebhookController проверяет журнал до повторной мутации, возвращает
  questionnaire_replayed и может повторить только текущий prompt. Raw JSON,
  answers/history/scope/business/template/catalog неизменны. Final-answer CAS
  commit/response loss до prepareSubmission восстанавливает исходный последний
  accepted ID даже при новом входящем сообщении; новое сообщение не становится
  финальным и получает retryable ответ, если canonical root ещё не готов.
  Existing submission/ticket marker и fail-closed root binding сохранены.
- 214 executions/20 suites — весь MAX test set,81 новая проверка:63 matrix
  BUSINESS v1–v4/GENERAL v2–v4 ×ACK/prompt failure/dedup reset ×answer/back/skip;
  1 RED regression,2 final CAS gap,2 optimistic retry,2 delayed replay и11
  journal/JSON/limit/legacy guards. Source/replay независимо GREEN, не428
  уникальных тестов. Прежние133 MAX tests, включая layout guard, прошли.
- Первая targeted матрица111 tests имела0 failures/28 fixture errors: GENERAL-only
  catalog не является shared menu. Fixture дополнен обязательным соседним
  BUSINESS choice, который не выбирается и не поставляет данные GENERAL.
  Production catalog requirements и негативные assertions не ослаблялись.
- 249 inputs/264 bytecode files frozen и source/replay exact, все212 core classes
  exact относительно R266. Bounded wrapper Maven -pl :bot-max -am/offline,
  Maven96MiB/test JVM192MiB/2CPU; нет full panel/Sass build или тестовых Docker
  ресурсов. Первичный слишком длинный emitted patch был обрезан лимитом вывода,
  replay/root тогда не менялись; minimal-hunk emitter-v2 применён и скомпилирован
  независимо. Первичный readiness probe обработал byte[] как строку; UTF-8 decode
  исправлен, фактический ответ200/UP. Это operator/fixture исправления, не runtime
  сбои. Исходный рабочий checkout изменён только после source/replay GREEN.
- Рабочие18 containers ID/image/config/restarts exact, включая runnerR267/R266,
  web/workerR257 и исходный db-migrate/инфраструктуру. V85/settings455/accounts/
  grants/roots/pending/ratings/outbox/ledger17delivered/receipt0 exact, config/env
  unchanged.3 feedback consumers/ready0/unacked0, readinessUP; backup/rollback
  R267 сохранены. Общий health/DLQ/TLS/iiko gates не исправлялись.
- Source scope:2 MAX main+2 test, task01-278/S0/BOT и эта append-only запись —
  8 project files. Historical task tail от Runtime R267 сохранён. Evidence/
  operators/frozen manifests/reports: .tmp/01-278-r268-evidence/.

## Не выполнены и остаток

Fix source-only, runtime R267 ещё не содержит R268. Для активации нужен отдельный
clean-commit bot-runner release без новых migrations/flags; test classes в image
не включать. Не было deployment/migrations/db-migrate/backfill/flags/purge/requeue/
provider sends/staging/commit/push или внешних mutations.

Доказательство использует реальные MAX handler/settings normalizer/local JSON
store/CAS/delivery guard и fake runtime/provider/TicketService boundaries. Нет
real Redis eviction, live MAX webhook/provider/client, ретроактивной дедупликации
старых сессий, exactly-once prompt или полного cross-platform/rebind gate. Другие
core/platform/feedback suites в этом шаге не повторялись. Пять крупных блоков/S6
остаются, задача🟡, исторический S2B снятR190.
