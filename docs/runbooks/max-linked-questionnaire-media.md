# MAX: пересылки и ответы с вложениями — R292

R299: устранён SQL channel-lock deadlock одновременных selected-media ingress
в Telegram/VK/MAX;66 новых PG cases GREEN. MAX fixture JDBC adapters исправлены,
runner теперь75 contracts. R300 выпустил backend fix на panel-web/ops-worker
текущей Windows runtime-машины; боты и инфраструктура прежние. Подробности ниже.

R292 подготовлен как source change задачи 01-278 в другой сессии и включён
в Build R296/sourceR295. R297 активировал этот image на Windows-машине рабочего
тестового экземпляра: V86/V87 и отдельные SQL/Rabbit principals уже применены,
backend и3 active children обновлены. Повторять их provision не требуется.
Flags прежние; signed MAX v4 choices возвращает404 по существующему feature gate.
R298 исправляет fixture port publication:69 local contracts GREEN, но запуск
42 MAX PG/Rabbit cases на Docker ещё pending;15 Docker principal IT из R297
GREEN. Текущий runtime checkpoint — R300, backend e661588b5. Live linked-media/
provider и S6 приёмка остаются. Для R298 rebuild/restart backend/bots не нужен.
См. [актуальный checkpoint](../../ai-context/tasks/task-details/01-278.md#runtime-r300--2026-11-09-backend-r299-с-исправлением-deadlock-выпущен).
Общая задача остаётся 🟡, S2B снят R190.

## Поддерживаемое поведение

В выбранном v4 вопроснике MAX принимает прямой файл, пересылку с файлами или
новые файлы в ответ на сообщение: document/photo/audio/video, до 10 файлов на
обращение и до 10 МиБ на файл. Initial свободный ответ сохраняется через прежний
session CAS/journal; после публикации используется frozen root admission и
один immutable outbox batch. BUSINESS и GENERAL имеют одинаковую границу.

Для пересылки файлы берутся только из canonical `link.message.attachments`.
Для ответа — только из внешнего `body.attachments`: процитированные файлы не
скачиваются и не считаются новыми. Смешанные direct+forward batches, legacy
aliases, вложенные link/body и формы без точного строкового `link.message.mid`
отклоняются до download/CAS. Прежние trusted-host, actual-byte/hash, quota и
owning-root проверки сохраняются. Opaque/token-only вложения не добавлены.

`sourceLink` содержит `forward|reply`, исходный строковый MID и доступные ID/имя
исходного автора. Отсутствие автора допустимо; при наличии ID он сохраняется
канонической строкой без JavaScript rounding. Данные исходного автора — контекст
сообщения, они не предоставляют полномочий и не выбирают владельца обращения.
Владелец всегда внешний actual sender; root/channel/catalog/storage key и
delivery identity остаются прежними. Числовой outer provider ID не вычисляется
из исходного MID. Формат `link.message` соответствует
[официальному объекту MAX Message](https://dev.max.ru/docs-api/objects/Message).

Подпись события охватывает исходные bytes вместе с контекстом. Новый batch имеет
`messageType=selected_media_batch.max_linked.v1`; deterministic event ID сохраняет
прежний outer tuple. Initial publisher помечает только связанные media entries
как `max_linked.v1:<type>`. Backend проверяет наличие обеих частей контракта,
сохраняет логический media type и typed JSON. Старый backend не принимает эти
wire types. Служебные marker types не выводятся оператору.
Legacy initial/JDBC/generic-active операции отклоняют sourceLink до domain
writes: контекст нельзя использовать для обхода selected media validation.

V87 добавляет nullable `chat_history.source_link_json` с JSON shape/length CHECK.
Новый backend читает старые записи с NULL, новый бот читает старые session JSON.
Исходные ID/история не переписываются, backfill не выполняется. История BUSINESS
и GENERAL возвращает контекст только после прежних scope/actor checks; удалённая
запись не раскрывает новый контекст. Отображение использует escaped HTML либо
textContent, исходный MID не становится href или командой отправки.

Повтор webhook после сохранения CAS или outbox использует исходный контекст.
Изменённые MID/имя/подпись при retry не пересобирают ранее принятый batch и не
запускают новый download. Утрата transport dedup не разрешает новую отправку.

## Capability и согласованный выпуск

Для связанных initial media новый клиент требует отдельный GET
`/internal/api/bot/channels/{channelId}/questionnaire/v4/max-linked-media-selection`
с прежними writer/media markers и точным frozen selection. Для опубликованного
обращения используется `/questionnaire-media/max-linked-batch`, admission
version=4. Прямой MAX batch сохраняет прежнюю version=3. Новый capability
проверяет active canonical MAX channel и доступность V87 до download. Generic
v4/direct admission, ошибка или недоступность нового route не являются fallback.

На машине с проектом требуется один согласованный выпуск panel-web/ops-worker,
bot-runner и MAX jar из R292, с Flyway V87. Сначала backend/schema и проверка
capability, затем новый MAX child. До запуска должны быть выполнены R291/V86
и [provision отдельных transport principals](child-transport-isolation.md).
Остальные children должны соответствовать контракту R291; flags автоматически
не включаются. Один restart старого image не добавляет новый контракт.

До переключения сохранить image/commit, PostgreSQL backup, Redis/session и
outbox/receipt evidence; остановить intake и согласовать drain. Не проигрывать
события вручную и не очищать pending/outbox/очереди. Положительные receipts и
unknown outcomes разбирать по [delivery recovery](feedback-delivery-recovery.md).

Обратная совместимость JSON односторонняя: новый код читает старые данные.
Старый MAX reader может игнорировать новое поле draft history. Поэтому после
появления R292 drafts нельзя просто запускать старый MAX jar поверх этих
сессий. Для rollback оставить intake/детей остановленными, сохранить текущие
Redis/outbox/историю и согласованный комплект images. Восстановление snapshot
или совместимого reader — отдельный проверяемый шаг; автоматического downgrade,
удаления V87 или восстановления provider send этот пакет не делает.

## Выполненная проверка

Совокупно 1090 JVM test/36 suites, 0 failures/errors/skips, по последним XML
нескольких targeted runs. В том числе 55 прежних principal/API/SQL cases с
полной Flyway V1–V87; 13 новых V87/Hibernate/history cases; 5 новых GENERAL SQL
cases; 272 прежних GENERAL actor checks; новый MAX handler/CAS/replay и signed
client/wire checks. Использован собственный nonce-owned native PostgreSQL16.15
на loopback/nonstandard port. Hibernate test использует настоящие ChatHistory/
Channel mapping и Spring naming strategy; provider network — doubles.

Node: 7 новых BUSINESS/GENERAL renderer cases и прежние R117/R134/R51/R52 suites
GREEN. PowerShell5.1 acceptance runner self-test/parser GREEN. Это не live
MAX/provider/Redis restart acceptance и не проверка полного рабочего Boot fleet.

### R299: SQL concurrency и JDBC adapters

Два concurrent signed ingress одного канала раньше повышали channel FOR SHARE
до FOR UPDATE и получали deadlock. R299 сохраняет channel SHARE, ticket/active
UPDATE и точный advisory guard.66 новых PG cases проверяют3 платформы и оба
scope: parallel roots, quota, stale admission/DB rebind, channel UPDATE во время
byte read и reconstructed-service replay; общий targeted proof397/13 suites.
См. [контракт и граница выпуска](child-transport-isolation.md#r299-одновременные-selected-media-записи).

В MAX IT channel/active repository adapters явно типизированы Long/String
Mockito arguments перед JDBC varargs. Прежний generic getArgument(0) давал
ClassCastException Long/String→Object[];6 regression contracts воспроизводят
ошибку старой формы и используют те же factory methods, что actual IT fixture.
Теперь полный runner требует75 contracts (прежние69 +6 adapters), Phase=R299
в прежнем r292-proof.json.42 media +15 principal IT и assertions не изменены.
Пользователь09 ноября: runner ещё не запускался.75 local contracts не являются
результатом57 Docker IT; engine здесь недоступен. Запуск ниже работает с clean
committed source, отдельно от рабочего runtime. Backend/bots он не обновляет.
R300 уже выпустил это исправление на backend/inbound-worker;313 fresh targeted
cases/8 suites, включая79 actual PG, GREEN. Child artifacts/миграции/provision/
flags не менялись. Полный75+57 Docker runner этим не заменён.

### Исправленная инфраструктура R298

Docker29 может не создавать host mappings у контейнера только в internal
bridge ([наблюдение в Moby](https://github.com/moby/moby/discussions/53256)).
R298 сохраняет PostgreSQL/Rabbit в internal service network без публикации
портов. Socat relay alpine/socat:1.7.4.3-r0 имеет второй собственный ingress
bridge и только два фиксированных target; наружу публикуются два случайных
порта исключительно на 127.0.0.1. Relay без capabilities, с read-only root и
ip_forward=0. Чужие/рабочие networks не подключаются. До protocol connections
проверяются actual Docker inspect memberships и bindings. Отсутствие mapping
или потеря isolation остаются ошибкой, fallback на внешнюю БД/брокер отсутствует.
PostgreSQL/Rabbit готовы по startup logs; internal service не ожидает host port.
См. [правила Docker для localhost bindings](https://docs.docker.com/engine/network/port-publishing/).

Новый source proof:69 tests/3 suites, включая35 fixture topology/inspect cases,
PowerShell5.1 parser/SelfTest GREEN. Это ещё не Docker42. Для повторной проверки
нужны clean committed checkout, JDK17 и локальный Linux Docker на машине запуска;
runner сам передаёт api.version=1.44 тестовой JVM, не меняет daemon settings.
MAX42 и principal15 запускаются раздельно; failed/startup XML и оба Maven logs
сохраняются даже при частичном результате. Успех — только все57 без skips,
IntegrationPassed=true, ExistingContainersUnchanged=true. Сейчас Phase=R299 в прежнем
r292-proof.json. У cleanup точные owner+nonce/image/role/isolation guards обеих
сетей; existing containers остаются под прежней проверкой неизменности.

Ниже общий запуск обновлённой матрицы. Это тестовый шаг: миграции/provision R297,
пересборка или перезапуск рабочего backend/bots для него не требуются.

Подготовлены 42 MAX PostgreSQL/Rabbit cases: прежние38 и4 новых BUSINESS/GENERAL
forward/reply chain/replay cases. Повторный полный запуск R299 остаётся `PENDING_DOCKER`: local
desktop-linux engine в source-сессии недоступен. На машине с локальным Docker/
JDK17 после пользовательского commit:

```powershell
powershell.exe -NoProfile -File .\java-bot\bot-feedback-acceptance\run-max-media-acceptance.ps1
```

Ожидаются 75 wire/isolation/fixture/adapter contracts и57 IT (42 media +15 SQL principal cases),
без skips. Оператор создаёт detached source/test containers, проверяет
неизменность существующих контейнеров и пишет `r292-proof.json`.
`-ValidateOnly` выполняет compile/75 contracts, не закрывает инфраструктурную
приёмку. 15 Docker principal cases уже GREEN в R297; runner повторяет их вместе
с42 MAX для единого57-case proof. Не приписывать local75 результат Docker42.

После выкладки проверить через тестового клиента BUSINESS и GENERAL:

1. Пересылка и ответ с собственным файлом до и после создания обращения:
   правильные caption/bytes/имя файла, исходный автор/MID в истории, actual
   client остаётся владельцем. Проверить скрытого автора.
2. Ответ на сообщение с чужим вложением: сохраняется только новый внешний
   файл; процитированный файл не появляется в обращении и не скачивается.
3. Повтор одного webhook после saved CAS/outbox и утраты dedup: неизменные
   контекст/количество файлов, без повторного download/provider send.
4. Отзыв прав, channel/root rebind и quota не дают нового admission; после
   READ revoke оператор не получает history/source metadata.
5. Сохранить фактические images/revisions/V87/flags и результаты в acceptance
   checkpoint другой runtime-сессии. Не отмечать здесь S6 принятым автоматически.

Общий пункт1 остаётся в работе:42 MAX cases и cross-platform
GENERAL/questionnaire restore/replay/rebind и operational provider/rollback
приёмка. Программный delivery/rating блок R289 и ручной checklist §7.1
production-e2e-smoke не открываются заново.
