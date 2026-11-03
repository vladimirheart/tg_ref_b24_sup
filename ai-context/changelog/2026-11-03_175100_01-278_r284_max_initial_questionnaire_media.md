# 01-278 / R284 — MAX-вложения в первом v4 вопроснике

Промты пользователя:

> делай
> продолжи

Дата:2026-11-03,17:51 Europe/Moscow. Baseline HEAD/origin/main/actual remote
d799f42274147c5b555bb3815e5cb9bb06e0fa6c, parent b4ae682493fdd1a7c5386214761f097420d6ed51.
R283 scoped byte writer уже находится в main. До GREEN основной checkout чист;
разработка и проверки выполнены в отдельном detached worktree вне repo/.tmp.

## Изменение

Initial MAX v4 BUSINESS/GENERAL free answers принимают direct batch1–10 файлов,
по1байт–10MiB, общий лимит10 файлов на сессию. Весь payload проверяется до I/O;
unsupported/opaque/mixed/foreign/forward/reply не превращают caption в ответ.
Active-ticket lookup и explicit signed backend capability нужны до download.
Существующий writer R283 сохраняет owned bytes; answer/history/event journal
коммитятся одним session CAS. Повтор после CAS/lost ACK не скачивает bytes
заново и сохраняет первоначальный submission event. Transient I/O/storage/CAS
оставляют вопрос для retry; size reject ACK только после confirmed warning.

Новый signed /v4/max-media-selection требует точные singleton markers
MAX-Media-Version1/writer4/media1 и frozen selection. Старый generic v4 endpoint
не выдаёт разрешения. Capability повторно проверяется перед первым
prepareSubmission; ранее prepared/submitted replay сохраняет текущий contract.
Backend initial ingestion проверяет owner/channel/catalog/provider-event/slot,
actual bytes/SHA/size, сохраняет metadata; canonical reader проверяет тот же
owned namespace и целостность bytes. Published followup media и missing-session
v4 media остаются закрыты; legacy MAX v1–v3/TG/VK поведение сохранено.
Session restore отбрасывает чужие/битые media metadata и отсутствие event journal.
HistoryEvent имеет7 компонентов и совместимый3-аргументный конструктор;
NON_NULL сохраняет прежний трёхполевой text JSON. Layout test усилен на новую
форму и совместимый конструктор, остальные ownership/layout assertions сохранены.

## Проверки

Final redis-4:1042 executions/34 suites =203 core/4 +839 MAX/30.
Panel-2:182/7. Итого1224/41, failures/errors/skips0. Включены23 direct и те же23
real Redis initial-media cases, прежние179 session-loss/replay tests,
signed loopback HTTP negotiation, реальное FS хранение, chunked HTTP size reject.
Новые backend suites:12 validator/read-proof +6 capability cases; ещё2 initial
ingestion cases BUSINESS/GENERAL. Внешних MAX/provider/CDN/S3 вызовов нет.
Redis tests используют настоящий session CAS/delivery-claim Lua с собственным
namespace; sentinel keys2 сохранены. Это не live webhook/process-kill/fleet
acceptance и не реальный S3 upload. Existing R282 gate не объявлен новым.

Repository Maven wrapper3.9.6/JDK17 offline, cached images,768MiB/CPU1,
test JVM192m; core/MAX main/test sources fresh compile. Backend3 изменённых
main classes и targeted test sources fresh compile с unchanged R280/R279
target/classes. Git diff1fea→d799 меняет там только уже включённый в compile
QuestionnaireMediaStorageProof. Full panel Maven/Sass/package не выполнялся.

Ранний bots-1 RED: новая fixture restub вызывала свой throwing stub; исправлена
на doReturn. Старый layout literal3-field ожидал прежний record; изменён на
строгий7-field+compat constructor contract с отдельным JSON compatibility test.
Redis-1 Maven/XML GREEN, runner RED из-за устаревшего точного числа core suites:
wildcard Max*Test выбирает R283 storage suite. Новый runner требует именованные
signed suites и проверяет каждый XML, не пропуская дополнительные suites.
Redis-3 compile RED: дополнительный тест использовал несуществующую RABBITMQ
константу; исправлен на реальный BotIntegrationTransportMode(MockEnvironment).
Все неудачные logs/reports/operators сохранены отдельно; финальный redis-4 GREEN.

## Scope и эксплуатация

24 project files:10 main (2 core,5 MAX,3 backend),8 test files,2 opt-in Redis
runners, task/S0/BOT/этот append-only changelog. POM/schema/flags не менялись.
Evidence/operators/proofs: ../iguana-runtime/tg_ref_b24_sup/work/r284-max-questionnaire-media/.
Существующие20 containers были и остались остановлены; ID/image/start/running/
restart exact. Disposable builders/Redis/internal network удалены; named
volumes0, новых images0. Рабочие DB/Redis/queues/provider и настройки не трогали.
R280 release/rollback/recovery и R281 cleanup archive сохранены.

Source-only: sandbox_green=true, применение в основной checkout отдельно
фиксируется в apply proof; staging/commit/push/deployment=false. Пересборки
runtime нет. Для включения нужен согласованный backend web/worker + MAX runner/
core release. Нельзя ограничиться panel-web или считать capability решением
drain/rollback при prepared/outbox/неопределённом результате. Published followup,
forward/reply, live provider, остальные policies/AI/S6 остаются; S2B снятR190.
Task остаётся🟡. Документ остатка обновлён: initial writer/session/backend связь
больше не является невыполненным пунктом.
