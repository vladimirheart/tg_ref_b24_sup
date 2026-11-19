# 2026-11-19 — Уведомление и история закрытия, время обращения

Задача01-296, baseline HEAD/origin/main
`44c4a9d282f6762580001ff41b5ffa7d934294a7`, после свежего fetch; root clean.

Промт пользователя:

```text
слушай, не то чтобы большая проблема, но при закрытии диалога почему-то перестало приходить оповещение клиенту и нет инфо в самом диалоге, что диалог закрыт. да, и время обращения в блоке "метрики" не отобразилось
```

В предыдущем исправлении01-295 common BUSINESS close был переведён в canonical
work command без переноса клиентского уведомления и записи в chat_history.
Добавлен BusinessTicketClosureNotificationService: typed системное сообщение
сохраняется атомарно с close; guarded provider send выполняется после commit
для captured client/channel. Destination и full owner повторно проверяются,
повторное закрытие не отправляет уведомление снова, отказ provider/ledger
не отменяет закрытие и запись истории. Webform использует только историю.
Оба business close entrypoints подключены через BusinessTicketWorkService.
Durable delivery queue/replay/backfill не добавлены; crash после commit до HTTP
остаётся ограничением синхронной отправки. Legacy feedback/activity не вызываются.

В dialogs-details-runtime нормализованы PostgreSQL dates с microseconds и
offsets +00/+03/+0300, timezone-less UTC, separate date+time и epoch zero.
Длительность закрытого обращения рассчитывается до resolved_at, открытого —
до текущего времени. Без даты закрытия остаётся «—».

146 Java cases GREEN (27 new native PostgreSQL +119 related), 19 Node checks
GREEN в UTC/Europe/Moscow. Проверка полной схемы V1–V98 подтверждает реальное
чтение нового сообщения через DialogConversationReadService. Baseline RED
воспроизводит отсутствие history и duration. Initial harness dependency-cache
compile issue и GENERATED ALWAYS test fixture исправлены; final checks GREEN.
Targeted javac/JUnit из dependency cache, JS syntax/diff/guards; полный Maven,
live provider/browser и рабочий Docker не проверялись. Точный scope8 файлов
и evidence — в карточке задачи. Собственный test PostgreSQL остановлен.

Изменения проверены в detached sandbox и перенесены guarded copy в рабочий
checkout. Staging/commit/push/deployment нет. Рабочая БД, .env и runtime не
менялись; новых migrations/backfill нет. Нужен новый panel-web image и проверка
следующего закрытия пользователем.
