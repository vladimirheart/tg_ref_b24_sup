# 01-278 — отправка документов GENERAL в Telegram (R142)

Дата: 2026-10-20. Задача 01-278, статус 🟡.

Промт пользователя:

> продолжай

Рабочий Docker и проект — на другой локальной машине и в другой сессии агента.
Здесь source/tests и isolated PostgreSQL Testcontainers; runtime revision/DB/
flags/services не инспектировались и не менялись. Deploy/рабочие migrations/
backfill/config/rebuild/restart/live acceptance не выполнялись.

Fresh HEAD/origin/main/actual remote main 1a0c6e8c7ca7ced47e98bc114b66020d4d8575ae,
parent 55b30a8eedab18c8489ef41608aa9d6dea5e75ae, actual R141 — 13 файлов.
Main clean; реализация и validation в detached sandbox.

Добавлены multipart/CSRF форма и отдельный POST отправки Telegram GENERAL
document до 10 МиБ. Current PAGE_DIALOGS/READ/OPERATE/account/flags/status,
exact owner/public generation/root/channel/revision и provider-origin proof
обязательны. MANUAL local client не получает channel. V72 хранит immutable
bounded bytes/SHA-256 и actor/input/destination-bound intent/audit. One claim,
share locks, frozen payload/channel и final network guard предотвращают
same-key duplicate и отправку по устаревшим данным. Positive provider ID,
object key/size/bytes proof предшествуют atomic SQL history/metadata/SENT/audit/
GENERAL metadata-only outbox. UNKNOWN/SENDING не повторяются автоматически.
Новый key является новой отправкой; автоматической reconciliation нет.

Download проверяет SENT registry, exact canonical owner/generation/history/
metadata и actual bytes/SHA/size, повторяет READ/metadata после IO. Legacy
fallback исключает и marker, и registry-linked rows. Response — attachment,
octet-stream, nosniff/no-store. Журнал до 20 документов, escaped text.
Объектное хранилище вне SQL transaction: возможны orphan objects/неучтённые
отправки после rollback. SQL payload остаётся копией; retention/quotas/cleanup/
reconciliation требуют отдельной policy до rollout.

Validation: 242/242 GREEN, 8 XML, failures/errors/skips=0.
PostgresRuntimeReadinessVerifierTest: 17; GeneralTicketControllersWebMvcTest: 10; GeneralTicketWorkHttpPostgresTest: 74; DialogReplyTransportServiceTest: 27; GeneralTicketDocumentReplyPostgresTest: 79; GeneralTicketReadServicePostgresIntegrationTest: 7; UiEventOutboxScopePostgresIntegrationTest: 16; UiEventOutboxWatcherTest: 12.
Full Flyway V72 и V71→V72 preservation, actual multipart Cookie/XOR CSRF,
duplicate concurrency/key binding, current/late access/scope/destination,
queued cancellation, grant locks, guarded HTTP/frozen multipart, provider/
storage/post-send persistence failure, download integrity/revocation и real
local_fs roundtrip во временной папке. Compile 686 main / 508 test.
Initial run: один HTTP assertion RED из-за charset=UTF-8 общего filter и два
SQL fixture errors при 256 chars в existing ticket_id varchar(255). v2 проверяет
совместимый octet-stream MIME с exact bytes и смену raw tuple в допустимой
длине. Constraints и production source не менялись. Повторены 74 HTTP + 79
document SQL cases; остальные 89 GREEN cases сохранены без rerun.
Initial log сохранён. Два Node harness GREEN, diff check GREEN.
Transform v1 остановлен до tracked source write на неуникальном elapsedMillis
anchor; v2 использует точный call anchor. Guarded source apply: 15 project files,
SHA-256/exact-set/report freshness, generated CSS исключён. Manual PS: UTF-8 BOM,
parser Windows PowerShell 5.1; staging/commit/push оставлены пользователю.

Inline media, VK/MAX uploads, media edit/delete, VK/MAX text edit/delete,
feedback/legacy attribution/auto-assignment/notifications и retention/
reconciliation остаются PENDING; полный список — в текущей сводке 01-278.
Source apply не означает runtime acceptance. Общий статус 🟡.
