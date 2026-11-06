# MAX: пересылки и ответы с вложениями — R292

R292 подготовлен как source change задачи 01-278 в другой сессии и включён
в Build R296/sourceR295. R297 активировал этот image на Windows-машине рабочего
тестового экземпляра: V86/V87 и отдельные SQL/Rabbit principals уже применены,
backend и3 active children обновлены. Повторять их provision не требуется.
Flags прежние; signed MAX v4 choices возвращает404 по существующему feature gate.
42 MAX PG/Rabbit cases ещё pending из-за fixture port publication;15 Docker
principal IT GREEN. Live linked-media/provider и S6 приёмка остаются.
См. [актуальный checkpoint](../../ai-context/tasks/task-details/01-278.md#runtime-r297--2026-11-06-v86v87-и-изолированные-боты-активированы).
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

Подготовлены 42 MAX PostgreSQL/Rabbit cases: прежние38 и4 новых BUSINESS/GENERAL
forward/reply chain/replay cases. Полный запуск остаётся `PENDING_DOCKER`: local
desktop-linux engine в source-сессии недоступен. На машине с локальным Docker/
JDK17 после пользовательского commit:

```powershell
powershell.exe -NoProfile -File .\java-bot\bot-feedback-acceptance\run-max-media-acceptance.ps1
```

Ожидаются 34 wire/isolation contracts и57 IT (42 media +15 SQL principal cases),
без skips. Оператор создаёт detached source/test containers, проверяет
неизменность существующих контейнеров и пишет `r292-proof.json`.
`-ValidateOnly` выполняет compile/34 contracts, не закрывает инфраструктурную
приёмку. Здесь 34 contracts и15 native principal cases выполнены отдельно;
Docker variant 15 principal cases остаётся вместе с общим Docker gate.

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

Общий пункт1 остаётся в работе: другие legacy/direct пути, cross-platform
GENERAL/questionnaire restore/replay/rebind и operational provider/rollback
приёмка. Программный delivery/rating блок R289 и ручной checklist §7.1
production-e2e-smoke не открываются заново.
