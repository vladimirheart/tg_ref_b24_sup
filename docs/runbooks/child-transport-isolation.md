# Изолированные transport principals дочерних ботов — R291

R301: полный изолированный gate выполнен —90 contracts +42 MAX PG/Rabbit
+15 principal IT GREEN, failures/errors/skips0. Production code/образы прежние.
После восстановления Docker61 live access checks GREEN, health/readinessUP,
production-readiness ready, DLQ0; bot-runner restart1, web/worker restart0.
Это не live provider/S6 приёмка и не повторный provision/migration.

R295: операторские действия только из панели; child больше не получает
reopen/activity/relay/operator-edit/support-chat mutations. Новые backend и
children нужны совместно; [контракт и приёмка](panel-only-operator-actions.md).

R294 подключает legacy Telegram/VK service-chat summary/relay/unblock/digest
к тому же signed exact recipient gate, включая Telegram files и fallback.
Инструкция новой сборки и приёмки: [legacy notifications](legacy-support-chat-delivery.md).
Build R296 содержит R292/R294/R295; на рабочем тестовом экземпляре они
активированы в R297. R300 дополнительно выпустил backend lock fix R299 на
panel-web/ops-worker, сохранив прежний bot-runner и3 children.
См. [актуальный checkpoint](../../ai-context/tasks/task-details/01-278.md#runtime-r300--2026-11-09-backend-r299-с-исправлением-deadlock-выпущен).

R292 добавляет linked MAX media и требует также V87; см.
[max linked media](max-linked-questionnaire-media.md). Ниже transport contract R291.

Это повторно используемая инструкция выпуска. Исторический source R291 был
подготовлен в другой сессии; R297 выполнен на Windows-машине рабочего тестового
экземпляра. V86/V87 и SQL/Rabbit principals Telegram1/3 и MAX2 уже применены,
backend и3 children обновлены. Не создавать роли и не повторять миграции только
из-за старых source checkpoints. Текущий полный Compose snapshot —
releases/r300-e661588b5-20261109/private/compose.final.json; backups R297 сохранены
в releases/r297-activation-20261106/private вне Git. Flags прежние, общий
PG/Rabbit/media/provider/S6 gate ещё не закрыт. Исторический S2B снят R190.

R298 исправляет MAX acceptance fixture для Docker29 с сохранением internal
service network. R301 завершил общий42+15 gate после исправления inspection
serializer/query bridge и точных duplicate-root rejection assertions.
[Команда и границы проверки](max-linked-questionnaire-media.md#исправленная-инфраструктура-r298).
Это только test tooling: рабочие backend/bots перезапускать или provisioning
повторять не требуется; текущие runtime images/Compose соответствуют R300.

## R299: одновременные selected-media записи

Child principal удерживает channel/principal FOR SHARE до конца ingress
transaction. Selected media verifier сохраняет FOR UPDATE для ticket/active,
но канал читает с FOR SHARE. Повышать channel lock до FOR UPDATE в этом пути
нельзя: параллельные ingress одного канала взаимно блокируют такое повышение.
Advisory lock по точному legacy ticket ID сохраняется; последнее свободное
место в quota выдаётся одному событию. Чужие roots не сериализуются общим
исключительным channel lock. Изменение platform/is_active и principal revoke
по-прежнему ждёт окончания текущей транзакции.
Семантика row locks: [PostgreSQL16](https://www.postgresql.org/docs/16/explicit-locking.html#LOCKING-ROWS).

Proof R299: старый source —6 real deadlocks; новый —66 PG cases для Telegram/VK/
MAX × BUSINESS/GENERAL без ошибок. Проверены параллельные roots, последний slot,
реальные DB platform/active/principal/root-channel/business/status changes после
раннего admission, blocked channel UPDATE во время actual byte read до commit,
повтор processed event после пересоздания production services без второго file
read/history/metadata. Узкие JDBC repository adapters, настоящие principal/
listener/ingestion/transaction/storage/metadata/inbox; это не live broker/SDK,
Redis/session/process restart и не вся cross-platform приёмка. Всего397 targeted
JVM cases/13 suites, включая79 actual SQL cases и75 acceptance contracts GREEN.

Source R299 сам не выполнял deployment. R300 на текущей Windows runtime-машине
пересобрал и обновил panel-web/ops-worker до e661588b5/image348066da11f5,
включая inbound worker.313 свежих targeted tests/8 suites GREEN,79 actual PG.
API/DTO/child JAR/schema/flags прежние; V86/V87/provision повторять не нужно.
Actual Compose — R300 private/compose.final.json, не старый R297/R280 override.
Bot-runner и3 children не перезапускались; provider send не проверялся.

MAX acceptance adapters также исправлены: generic Mockito argument больше не
приводится к Object[] на JDBC varargs границе. Текущий runner R301 ожидает90
contracts и прежние42+15 IT. Два duplicate-root cases требуют точный R291
AmqpRejectAndDontRequeueException до inbox; отсутствие history/metadata и
неизменность active profile проверяются. Полный gate R301 GREEN.
[Команда](max-linked-questionnaire-media.md#r299-sql-concurrency-и-jdbc-adapters).

## Что меняется

В режиме `rabbitmq` launcher больше не передаёт child общий control-plane API
token, SQL login или RabbitMQ login. Для canonical channel ID/platform он
вычисляет отдельные API/SQL/Rabbit credentials. SQL и Rabbit login имеют вид
`iguana_child_<channelId>`. API token содержит platform/channel и отдельный MAC.
Три ключа имеют разные HMAC domains; одинаковый master не делает их взаимозаменяемыми.
Настроенный signing secret должен содержать не менее 32 символов; если он пуст,
это требование относится к master token. Слабая конфигурация останавливает запуск.

Backend проверяет подлинность child, активную canonical channel/platform и
`bot_child_principals.enabled` в owning transaction до idempotency replay.
Доступ к root ограничен каналом; история и unblock summary фильтруются в SQL
до LIMIT. Отправка в служебный чат дополнительно требует текущего точного
`support_chat_id`; локальный снимок не даёт права послать в старый чат.

V86 включает FORCE RLS на трёх transport tables. Child не получает business
tables, DDL, role memberships или BYPASSRLS. Startup проверяет фактический SQL
login/привилегии до запуска обработчиков. Отзыв запрещает новые действия, но
не мешает сохранить положительную квитанцию уже принятой провайдером отправки.
Неизвестный исход не даёт права повторной отправки.

AMQP publication имеет `user_id`, который брокер сопоставляет с publisher login,
и независимую подпись исходных bytes. Backend повторно доказывает channel,
platform, actual user и exact raw ticket root до ingestion. Ключ в AMQP не
передаётся. У child нет broker management/impersonator tags или configure rights.
Проверка `user_id` описана в [RabbitMQ validated user-id](https://www.rabbitmq.com/docs/validated-user-id),
права ресурсов — в [RabbitMQ access control](https://www.rabbitmq.com/docs/3.13/access-control).

Это изоляция transport API/SQL/Rabbit и соответствующих read/send paths.
Она не является полной изоляцией ОС/Redis/object storage и не закрывает весь
первый блок 01-278. MAX forwarded/reply media реализованы sourceR292; legacy
Telegram/VK operator notifications подключены sourceR294. Остальные legacy/
inbound group permissions закрыты R295; rollout и общая cross-platform restore/replay/
rebind/provider приёмка остаются отдельно.

## До выпуска на машине с проектом

1. Зафиксировать source commit/image и отдельный rollback image для backend
   panel-web/ops-worker и bot-runner/трёх bot jars. Сохранить backup БД и broker
   definitions, текущие flags и конкретные Compose project/file/env-file.
2. Отключить auto-start детей и остановить старый intake/детей штатным lifecycle.
   Разобрать prepared/outbox/ledger/receipt по
   [feedback delivery recovery](feedback-delivery-recovery.md). Дождаться drain
   согласованных событий. Не очищать очереди, не публиковать старые события и
   не повторять provider send автоматически.
3. Убедиться, что все backend consumers и launcher будут использовать одни
   точные master/signing secrets. Отдельная смена master, signing secret или
   platform меняет производные credentials и подписи сохранённых событий.
   Смешанный выпуск старых и новых детей не поддерживается: старые unsigned
   inbound события новый backend отклоняет. V86 сама не создаёт LOGIN roles.
4. Выполнить обычный согласованный backend rollout с V86 и auto-start=false.
   Проверить Flyway success, доступность API и наличие трёх transport tables
   с RLS. Только затем provision каждого канала и запуск новых детей.

## Подготовка файлов для каждого канала

На Windows машине с рабочим checkout, **после выбора реального channel ID**:

```powershell
# Пример: замените 22/max на значения из панели.
powershell.exe -NoProfile -File .\scripts\prepare-child-transport.ps1 -ChannelId 22 -Platform max
```

Скрипт запросит точные настроенные `APP_INTERNAL_BOT_API_TOKEN` и
`APP_INTERNAL_BOT_API_SIGNATURE_SECRET` через SecureString. Пустой второй ввод
означает пустой signing secret в конфигурации. Не передавать secrets строками
в command line, transcript или чат. При нестандартном broker vhost/exchanges
задать параметры `VirtualHost`, `InboundExchange`, `OutboundExchange`,
`OutboundDeadLetterExchange` согласно конфигурации.

Результат — новый каталог в `.tmp` с ACL только текущего Windows SID:

- `provision.sql` содержит производный SQL password;
- `rabbitmq-definitions.json` содержит salted password hash и точные очереди/
  bindings/permissions одного child;
- `manifest.json` содержит hashes/identity и `Applied=false`, без secrets.

Скрипт не подключается к БД/брокеру и не читает конфигурацию приложения.
Готовые файлы секретны; не добавлять их в Git, не печатать SQL/definitions в
логи. Salted hash использует [официальный формат RabbitMQ](https://www.rabbitmq.com/docs/passwords).

## Применение администратором

В той же runtime-сессии сначала получить полные ID контейнеров именно выбранного
Compose проекта. Подставить их и реальные DB/user в переменные ниже; проверить
image и project labels. Здесь нельзя использовать ID из старого checkpoint.

```powershell
$pgContainer = '<полный ID PostgreSQL выбранного проекта>'
$rabbitContainer = '<полный ID RabbitMQ выбранного проекта>'
$database = '<реальное имя БД>'
$databaseAdmin = '<администратор БД с правом CREATE ROLE и GRANT>'
$privateDirectory = '<полный путь каталога из manifest.json>'
docker inspect --format '{{.Id}} {{.Config.Image}} {{index .Config.Labels "com.docker.compose.project"}}' $pgContainer $rabbitContainer
```

После сверки ID, backup и V86 выполнить однократный provision:

```powershell
Get-Content -LiteralPath (Join-Path $privateDirectory 'provision.sql') -Raw |
    docker exec -i $pgContainer psql -X -v ON_ERROR_STOP=1 -U $databaseAdmin -d $database
if ($LASTEXITCODE -ne 0) { throw 'SQL provisioning failed; children must remain stopped' }
```

SQL выполняется одной транзакцией, требует exact active canonical channel и
отказывается присваивать/ротировать существующую роль. PUBLIC grants требуют
отдельного разбора, скрипт не отзывает их глобально. Не применять повторно после
ошибки без проверки роли/привязки. Не использовать `psql -a/-e` или statement
logging для файла с credentials.

Каждый новый канал требует такого же provision до первого запуска. Удаление
канала удаляет его binding в V86, закрывая API/новые SQL действия; SQL/Rabbit
login автоматически не удаляется. Отзыв оставшихся подключений и удаление
неиспользуемых identities выполняет администратор после разбора receipts/drain.

Для RabbitMQ definitions уже должны существовать backend-owned exchanges.
После проверки выбранного контейнера импортировать один файл:

```powershell
$definitionPath = '/tmp/iguana-child-' + [guid]::NewGuid().ToString('N') + '.json'
docker cp (Join-Path $privateDirectory 'rabbitmq-definitions.json') "${rabbitContainer}:$definitionPath"
if ($LASTEXITCODE -ne 0) { throw 'Definitions copy failed' }
docker exec $rabbitContainer rabbitmqctl import_definitions $definitionPath
if ($LASTEXITCODE -ne 0) { throw 'Definitions import failed; inspect broker state before retry' }
docker exec $rabbitContainer rm -- $definitionPath
if ($LASTEXITCODE -ne 0) { throw 'Remove the private temporary definitions file in the selected container' }
```

Импорт брокера не является общей транзакцией с SQL. Проверить login без tags,
configure=`^$`, write только inbound exchange и read только own outbound queue,
две очереди и bindings. Children не объявляют ресурсы сами. Не расширять права
до `.*`, не возвращать общие credentials при startup failure.

Запустить новые bot jars через обновлённый bot-runner, сначала один тестовый
канал. Проверить startup principal gate, обычный intake/ответ, историю только
данного канала и отказ при чужом channel/root/service-chat. Затем остальные
каналы и controlled revoke/rebind/restart. Зафиксировать фактические revisions,
V86, результаты и flags в acceptance checkpoint. Файлы с credentials хранить
с закрытыми ACL согласно принятой политике, отдельно от test evidence.

## Автоматическая приёмка и rollback

Для source R291 прошли 1023 JVM tests/43 suites, без failures/errors/
skips: в том числе 55 SQL/API/inbound/Flyway cases, 8 scoped history SQL cases
и 15 actual bot startup SQL cases на собственном native PostgreSQL16.15.
Windows PowerShell5.1 generator checks — 14 GREEN; runner self-test GREEN.
Loopback HTTP и provider doubles не означают live provider acceptance.

MAX media PG/Rabbit gate остаётся `PENDING_DOCKER`: local engine недоступен.
R292 расширяет прежние38 cases до42; новый source proof описан в связанном runbook.
15 principal cases выполнены native, их Docker variant ещё не выполнялся.
После пользовательского commit на машине с локальным Docker/JDK17:

```powershell
powershell.exe -NoProfile -File .\java-bot\bot-feedback-acceptance\run-max-media-acceptance.ps1
```

Оператор создаёт detached checkout и только свои nonce-labelled test containers;
проверяет неизменность существующих. Текущий итог R292 — 34 contracts +42 media +15 principals,
без skips, `r292-proof.json`, IntegrationTests=57. `-ValidateOnly` не закрывает
integration gate. Он не выполняет rollout или операции с рабочими данными.

При неуспехе остановить новый intake/детей, сохранить очередь/outbox/ledger/
receipts и диагностические факты. Откатить согласованные backend/launcher/bot
images по сохранённому plan при остановленных детях. Не удалять V86/principals/
receipts, не выдавать старым детям общие backend credentials для обхода gate.
Ротация существующих ролей и восстановление старых in-flight envelopes требуют
отдельного проверенного плана; этот one-time provision их не делает. При
неопределённом provider outcome повторная отправка запрещена до reconciliation.
