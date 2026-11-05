# Разбор доставки запроса оценки

Пункт 1 задачи 01-278: доставка/оценки, восстановление после остановки обработчика
и неизвестный результат отправки. Живые проверки пользователь подтвердил
5 ноября 2026: «тесты уже проводились ранее - всё было ок.». Дата и revision
самих проверок не сообщены; это подтверждение пользователя, не новый live run
этой source-сессии. Повторять клиентские отправки ради этого пакета не нужно.

Исторические доказательства: R265 — принудительная гибель delivery JVM на пяти
границах, исходный Rabbit redelivery в новом PID, 60 сценариев плюс smoke/guards;
R266 — signed rating round trip, rollback/duplicate/unknown HTTP response;
R267 — согласованный cold start штатного launcher/трёх children с пустыми
очередями. Эти результаты не являются новым измерением рабочего экземпляра
и не доказывают аварийный restart всей fleet с сообщениями в обработке.

Программная часть этого пункта завершена. Оставшаяся ручная проверка полного
аварийного перезапуска с сообщениями в обработке перенесена в раздел7.1
[документации приёмки](production-e2e-smoke.md), статус MANUAL_PENDING.
Это не повторная разработка доставки или оценок.

## Решение по состоянию попытки

| Состояние | Действие |
| --- | --- |
| `delivered` | Сохранить запись и timestamps; повтор не нужен. |
| `sending` / `uncertain` с точной положительной V85-квитанцией | Разрешено восстановить только ledger из committed receipt, без отправки. |
| `sending` / `uncertain` без квитанции | Оставить blocked; собрать факты провайдера/клиента по конкретному event ID. |
| `processing` после остановки | Оставить blocked: таймаут не доказывает, что провайдер не вызывался. |
| `retryable` | Допустим только отдельный явный replay исходного события через штатный путь с новой авторизацией. Этот оператор replay не выполняет. |
| Старый `failed`, неизвестный статус, несовпадение контекста | Оставить blocked; ручная смена на `retryable` запрещена. |

Отсутствие provider ID, пустая очередь, HTTP timeout/false/503 и истечение времени
не доказывают недоставку. Положительная квитанция подтверждает принятие
провайдером, не прочтение клиентом. Сообщение, увиденное клиентом, само по себе
не позволяет дописывать фиктивную квитанцию в V85. Если исход нельзя доказать,
правильный результат разбора — сохранённая блокировка без повторной отправки.

## Точная проверка одной попытки

Выполнять на машине с рабочим проектом и её локальным Docker. Утилита запускает
только `psql` внутри явно выбранного уже работающего PostgreSQL container.
Образы, контейнеры приложения, Compose, конфигурация и секреты не меняются;
пересборка или перезапуск приложения для утилиты не нужны. Нужны Windows
PowerShell 5.1+ и PostgreSQL 14+; в проекте используется PostgreSQL 16.

Сначала найти полный ID нужного PostgreSQL container:

```powershell
docker ps --no-trunc --format '{{.ID}} {{.Image}} {{.Names}}'
```

Заполнить значения из текущего экземпляра. Event ID берётся из журнала
`OutboundFeedbackPromptDispatchService` или соответствующей outbox-записи.
`ChannelId` — внутренний ID канала панели, `UserId` — получатель, не служебный чат.
Все ID передаются строками: значения выше 2^53 нельзя округлять через JSON number.

```powershell
$attempt = @{
    DatabaseContainerId = '<полный ID PostgreSQL container, 64 hex>'
    DatabaseName = '<имя БД>'
    DatabaseUser = '<роль БД с нужными правами>'
    Schema = 'public'
    Platform = 'telegram' # либо vk / max
    ChannelId = '<ID канала>'
    UserId = '<ID получателя>'
    EventId = '<точный event ID>'
}
& .\scripts\feedback-delivery-recovery.ps1 @attempt
```

По умолчанию SQL выполняется в read-only transaction и возвращает JSON:
`Outcome`, состояние, строковые ID, признаки квитанции/совпадения контекста
и `SnapshotSha256`. Prompt, токены, raw ticket ID, provider ID, fingerprint
и last_error не выводятся. `NOT_FOUND` включает несовпадение выбранной платформы,
канала, получателя, event kind или маршрута; автоматического поиска другого
получателя/канала нет. Пароль не передаётся через командную строку или `.env`;
используется существующая локальная аутентификация `psql` внутри container.

## Восстановление по существующей квитанции

Только если read-only результат — `RECEIPT_AVAILABLE`, сохранить выданный
`SnapshotSha256` и явно вызвать:

```powershell
& .\scripts\feedback-delivery-recovery.ps1 @attempt `
    -ReconcileReceipt -ExpectedSnapshotSha256 '<SnapshotSha256 из проверки>'
```

Одна repeatable-read transaction блокирует только выбранные ledger/receipt rows.
Повторно проверяются SHA256 полного снимка обеих записей, platform, canonical
route, event kind, channel/user/ticket/request и сохранённый положительный
provider receipt. Меняются только `status`, `delivered_at` (из receipt),
`last_error` и `updated_at`. Receipt, outbox, pending request и rating не меняются;
нет вызова provider/API/Rabbit, новой авторизации или восстановления очереди.

Успех — `DELIVERED_FROM_RECEIPT` и `Reconciled=true`. Повтор с прежним proof
отклоняется без изменения данных; свежий read-only запрос покажет
`ALREADY_DELIVERED`. Любое изменение снимка требует новой проверки.
SQL/native/lock/serialization failure не считается успехом. При потере ответа
после возможного commit выполнить только read-only review, а не отправку.
Не удалять ledger/receipt и не менять событию ID, чтобы обойти блокировку.

## Проверка самой утилиты

Без Docker и соединений:

```powershell
powershell -NoProfile -File .\scripts\feedback-delivery-recovery.ps1 -SelfTest
```

Регрессия SQL/оператора на отдельном native PostgreSQL для Windows:

```powershell
powershell -NoProfile -File .\scripts\tests\feedback-delivery-recovery.tests.ps1 `
    -PostgresBin '<каталог bin с initdb.exe, pg_ctl.exe, postgres.exe и psql.exe>'
```

Тест создаёт fresh private cluster в repo/.tmp, случайный loopback port,
фиктивную роль и применяет V85 только к этой disposable БД. В конце останавливает
свой cluster; данные/логи/proof сохраняет. Рабочая БД/Docker не используются.
Проверяются три платформы, exact Unicode/raw event, BIGINT/Long.MAX_VALUE,
дубликат, прежние статусы, отсутствие/подмена receipt, stale proof, SQL rollback
и отказ неверным входам. Это проверка SQL и Windows operator, не новый
provider/fleet acceptance. Полная fleet/rollback проверка относится к S6;
R287 MAX media PG/Rabbit gate остаётся отдельным и этим пакетом не закрывается.
