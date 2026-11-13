# 2026-11-13 — Автоматическая подготовка инфраструктуры ботов

Задача: 01-288. Baseline HEAD/origin/main:
`35bed94d00b301286c79a99df4030cb302d4147d`.

Промт пользователя:

```text
автоматизируй подготовку инфраструктуры
```

Добавлены V94 и backend services/repository: создание ограниченного PostgreSQL
principal и journal, подготовка RabbitMQ user/queues/bindings/permissions через
management API, проверяемый повтор после частичного сбоя без ротации credentials.
Существующие manual/revoked/чужие identities не присваиваются. Child по-прежнему
получает отдельные SQL/Rabbit credentials и не получает management administrator.

ChannelTransportService готовит доступы после сохранения и возвращает отдельный
результат подготовки; канал сохраняется при broker failure. BotProcessService
повторяет подготовку перед lifecycle start, включая restart/auto-start; неудачный
preflight не останавливает прежний рабочий процесс. Настройки каналов показывают
spinner добавления, блокируют дубли submit и объясняют повтор через «Старт».
Настройки application, Compose/.env.example, документация окружения и runbooks
обновлены. Test constructors обновлены для новой зависимости; добавлены SQL/HTTP/
service/API/JS проверки и environment cases для management credentials.

Проверки: 849 targeted Java cases/17 suites и11 Node cases GREEN. Включены69
actual PostgreSQL cases с SCRAM для child, полной Flyway chain до V94 и прежними
RLS/principal assertions; broker — loopback stateful HTTP double. Docker недоступен,
настоящий RabbitMQ/AMQP/provider gate не выполнялся. Java compile, JS syntax,
Compose config и diff check GREEN.10 failures общего старого API suite отдельно
воспроизведены на exact baseline; этот набор не объявляется GREEN. Детали и
артефакты — в task-details/01-288.md.

Изменения проверены в detached worktree и перенесены guarded copy в основной
checkout;35 project files. Никаких staging/commit/push/deployment или изменений
рабочей инфраструктуры. Временный test PostgreSQL остановлен. Для применения
потребуется выпуск backend и bot-runner с V94; bot module sources прежние.
