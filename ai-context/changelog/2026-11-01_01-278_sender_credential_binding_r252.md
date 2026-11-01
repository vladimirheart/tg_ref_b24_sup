# R252 — проверенный отправитель: launcher, provider credentials и backend admission

Время: 2026-11-01 18:18 +03:00. Задача01-278, статус🟡.
Промт пользователя: «делай давай. на месте топчемся без особого результата».

## Фактическое изменение

Связный scope32:16 main/resource files,10 new tests,2 old setup-only fixtures,
BOT_RUNTIME_CONTRACT/task/S0 и этот append-only changelog.

- BotProcessService и новый общий BotChannelCredentialResolver: positive active
  channel, строгий explicit credential ID/unique/active/platform/token; missing
  binding больше не подменяется inline channels.token. Без binding inline остаётся.
- BotRuntimeContractService/shared YAML: APP_BOT_CHANNEL_ID/APP_BOT_PLATFORM, не
  external chat ID; diagnostic required keys. BotRuntimeSenderIdentity фиксирует
  runtime inputs; OutboundSenderIdentity хранит только hash, toString redacted.
- Telegram/VK/MAX OutboundMessenger и реальные VkSupportBot/MaxApiClient: identity
  из фактического provider token, а не event или второй properties-копии.
- MessagingService/OutboundMessenger: default unbound provider deny; user и support
  sends проходят новый signed PanelOutboundSenderAuthorizationClient. API error/
  mismatch/deny не вызывает provider; local channel/token proof повторён после I/O.
- Новый BotOutboundSenderAuthorizationService/controller: current repository
  channel+общий effective credential resolver, exact digest, прежний internal guard,
  read-only без cache/idempotency replay. Response без token/hash/private prompt.
- Документы фиксируют закрытый credential gate, кандидат сборки и крупный остаток.

## Проверка и сборка

Baseline clean HEAD/origin/main/actual remote03d538f61812394c340d8686013238114507e747,
parent978f6995d, message/scope6 R251 проверены. RED90/78 assertion failures/12 controls,
0 errors/skips: bot48/42 и launcher42/36. Новые source/bytecode/cases frozen.
GREEN600/19 suites:441 bot/12 (все4 modules),141 panel/6 и18 native Windows
launcher/1;242 новых/358 прежних. Old2 bot fixtures только setup, checked inverse
восстанавливает каждую прежнюю строку/assertion. Other old tests не менялись.
Backend doubles/real signed MVC, bot independent HMAC loopback и actual adapters;
нет real PostgreSQL/live Rabbit/provider/fleet acceptance в этом packet.

180 bot main Java/280 classes; core208:203 unrelated exact/2 existing changed/3 added.
Panel incremental5 main/1433 classes:1417 unrelated exact/11 existing family changed/
5 added. Native использовал те же1433 main bytes; всего216 compiled inputs exact.
Helper512MiB/1.5CPU, sequential/no test DB; native parent heap96/fork128MiB,
JDK25 ByteBuddy compatibility только в test options. Assertions не ослаблялись.

Full repository-wrapper reactor собрал три executable bot JAR. Incremental panel
JAR1433 classes/795 unrelated entries exact, SHA256
C6BED5E6C413AB78574953464A41C9A5F388ED655C009C51E6C743859AED8D98.
Strict ZIP writer нормализовал descriptors/declared empty entries; unrelated file
payloads и STORED nested libraries сохранены. Не full web Maven/Sass rebuild.
Local image iguana-panel:candidate-r252-03d538f61-20261101,
sha256:63ba7ea78a380aac4b3718572e218a001ea77ff627c6502010a14f10d89dee78;
revision03d538f61812394c340d8686013238114507e747+r252.8a275f3a1520 — проверенный
baseline+delta, не clean Git release. Build network none; все4 embedded JAR exact
проверены64MiB/no-network sha256sum container без Java service/DB/provider start.

Evidence .tmp/01-278-r252-evidence/: RED/GREEN/native/frozen/inverse/input/class
proofs, dist/four JARs, tested-artifacts/candidate-image/runtime/guard results.
Начальный offline cache miss/TLS failure решён read-only copy существующего host
Maven release cache без изменения versions/TLS validation. Ошибки новых controller/
operator/port fixture и исправленные версии сохранены. Старые Linux launcher
platform/classpath mismatches закрыты полноценным Windows run18 без test edits.
Detached sandbox/replay/exact baseline/blob/content/scope/frozen inputs/diff guards
до apply. Stage/commit/push/deploy не выполнялись.

## Runtime и остаток

Working18/config/DB84/settings/accounts/grants/roots/pending/ratings/outbox прежние;
126 published/actionable0/active pending0, web/worker R246 healthy/restart0/readinessUP.
Migration/backfill/flags/working queues/provider writes отсутствуют. Rollback tags
и backup R247 сохранены; restore/actual rollback не делались. Working services/
WSL/VS Code ради ОЗУ не останавливались. R248–R252 не развёрнуты.

Credential matching для MessagingService выполнен. Separate per-child API/SQL
principal isolation/shared-secret compromise, direct legacy provider calls,
service-chat recipient rights, child/media/rating attribution, AI privacy/retention,
GENERAL/fleet/policies/S6 acceptance и operational gates ещё открыты. Не обещается
atomic API→provider/in-flight revoke/exactly-once. Для новых ботов сначала оба API
R249/R252, затем новый runner launcher и bot artifacts; workerR248 согласуется.
Исторический S2B снятR190; очистка данных/новые product policies не подразумеваются.
