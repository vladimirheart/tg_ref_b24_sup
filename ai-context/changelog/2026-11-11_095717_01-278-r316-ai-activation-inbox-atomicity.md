# 01-278 R316 — рабочая AI-приёмка и атомарное подтверждение inbox

Время:2026-11-11,09:57 MSK. Задача01-278 остаётся🟡.

Промты пользователя:

- «продолжай по задаче»
- «возьми как и в предыдузем тесте»
- «Сообщения нет»
- На сверку пары бот–аккаунт: «Да, это мои бот и аккаунт».
- На отдельный диагностический ответ: «Да, получил одно сообщение».

## Изменения

- IntegrationInboundEventInboxService: markProcessed теперь требует owning write
  transaction, как markProcessedWithWrites. Known rollback после late JPA flush
  записывает failed отдельной транзакцией; уже processed не понижается поздним
  callback. Успех сохраняется атомарно с history/root/typed outbox, retry/duplicate
  protection сохранены. Старые ложные processed не переоткрываются.
- ScopedAiSourceHttpPostgresTest: четыре PostgreSQL/JPA regression cases —
  late flush, rollback, exact retry/duplicate и обязательная transaction.
- ScopedAiAutoReplyService: UNKNOWN диагностика содержит только eventId, HTTP
  status и классы исключений. Ни text/payload/token, ни provider body/message,
  ни stack trace в журнал не передаются; SENDING/UNKNOWN не переочередяются.
- ScopedAiAutoReplyPostgresTest: два cases проверяют отсутствие чувствительных
  данных в диагностике и единственную попытку после UNKNOWN.
- S0-контракт, карточка01-278, BOT_RUNTIME_CONTRACT, README и runbooks
  ai-data-boundaries/shared-work-access/common-system-access синхронизированы
  с фактическими flags/image/Compose и оставшимися gates.

## Фактический результат

Baseline HEAD/origin/main/actual remote7a2ad5969332ceebe0f95d4c336b71596db5c1af;
R316 source применяется без staging/commit/push. Sandbox сначала GREEN, затем
те же functional changes применены в primary.300 tests/5 suites GREEN:
177 неизменённых passing cases +123 повторно проверенных delivery cases.
Новые diagnostic fixtures исправлены до GREEN, прежний RED сохранён.
Четыре inbox regressions воспроизвели ошибки на неизменённом R315; positive
control passing. Native PG fixture/volume удалены; рабочая БД тестами не использовалась.
Сверены1961 inputs,2406 unchanged classes и1913 packaged payloads.
Targeted compile2 main/2 test, Maven packaging/image build; не полный repo suite.

Через Compose --no-deps обновлены только web/worker, по1 replica/healthy/restart0.
Imagec05e3e15963950b2e8b35dde131d90bfeec0a7260139a0170933a6a6aac4b936,
baseline7a2ad5969 +uncommitted R316. Включены foundation/ui-selector через
canonical nested SPRING_APPLICATION_JSON; временный manual creation выключен.
Root .env/settings.json сохранены. V91/schema history прежние; migrator/backfill
не запускались. Боты R295 и18 остальных контейнеров не обновлялись.

32 actual Edge gates: source review/rules/CSRF/stale, ADMIN/VIEWER/revoke,
AI control и ручная очистка истории. Допуски выданы только тестовому оператору
в новом QA BUSINESS, исходные бизнесы1/2 не изменялись. Отдельный typed signed
AMQP test дал Telegram SENT10455, пользователь подтвердил одно сообщение.
Replay до/после Compose restart worker сохранил один receipt/ответ и тот же ID.
Новый тест имеет отдельные reviewed body/trigger/root/event; исходный UNKNOWN
сохранён без сброса. Причина прежней неподтверждённой попытки не доказана.
Настоящий входящий callback/создание root ботом, MAX/VK и общая S6 этим не закрыты.

Первый malformed QA event с NULL identity выявил inbox defect и попал в DLQ.
Единственный точный wire сверен по ID/signature/body/всем AMQP properties,
полностью архивирован и адресно ACK, без purge/replay. Body SHA256
608686624da3361caee8ea7cfb15944d31982c1c255c03e4de9f380235d8cbe4;
wire SHA2564ca289c22d8f4a98d126ec01057848e70cb00e0a6f4d051f005354b01757e456.
Health/readiness200UP. Все три QA roots завершены (resolved), AI заблокирован,
активных правил0; sources/audit/receipts сохранены до ручного удаления.
Исходные53 обращения не распределялись (closed16/resolved37).
Evidence/rollback защищены вне .tmp в ../iguana-runtime/tg_ref_b24_sup/releases/
r316-ai-activation-20261111/. Активный Compose private/compose.finalize.v4.json.
Rollback tags R316 сохраняют прежний R315/image3998c9ea829a и original Compose/
выключенные flags; реальный rollback/restore рабочей БД не разыгрывались.
LLM/вложения/массовые approvals и другие внешние системы не использовались.
