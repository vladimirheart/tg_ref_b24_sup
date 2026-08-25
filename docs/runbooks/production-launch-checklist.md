# Iguana Production Launch Checklist And Runbook

Актуально на `2026-08-25`.

## 1. Назначение

Этот документ нужен как единая практическая инструкция для production-запуска Iguana. Он не заменяет глубокие архитектурные документы, а собирает в один сценарий:

- preflight-проверки;
- go/no-go критерии;
- шаги развёртки;
- миграцию и cutover;
- smoke после запуска;
- rollback;
- первую неделю сопровождения.

Базовые архитектурные источники, на которые этот runbook опирается:

- [POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md](../POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md)
- [postgresql-production-contour.md](./postgresql-production-contour.md)
- [postgresql-cutover-rehearsal.md](./postgresql-cutover-rehearsal.md)
- [production-e2e-smoke.md](./production-e2e-smoke.md)
- [BOT_RUNTIME_CONTRACT.md](../BOT_RUNTIME_CONTRACT.md)
- [environment_variables.md](../environment_variables.md)
- [observability-baseline.md](../observability-baseline.md)
- [docker-production-edge-deploy.md](./docker-production-edge-deploy.md)

## 2. Для какого контура этот runbook

Production launch для Iguana считается допустимым только в canonical contour:

- `spring-panel` как backend-owner business state и operator UI;
- `PostgreSQL` как canonical business/runtime storage;
- `Redis` как coordination layer;
- `RabbitMQ` как integration transport;
- `S3-compatible` storage как attachment boundary;
- `java-bot` как transport/integration runtime;
- bot child processes в режиме `APP_DB_MODE=worker`;
- transport boundary в режиме `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`.

Что не считается production contour:

- `APP_DB_MODE=sqlite` как основной runtime;
- `APP_INTEGRATION_TRANSPORT_MODE=jdbc` в live-среде;
- `APP_STORAGE_OBJECT_MODE=local_fs` как production storage boundary;
- bot child с прямыми `SPRING_DATASOURCE_*` credentials;
- production-старт с дефолтными security secrets.

## 3. Профиль запуска

Этот runbook ориентирован на текущий ожидаемый контур нагрузки:

- около `3000` обращений в сутки;
- около `30` одновременно работающих операторов;
- умеренный, а не high-scale throughput;
- основной риск не в raw performance, а в операционной зрелости, безопасности, доставке интеграций и наблюдаемости.

Практический вывод:

- одного инстанса `spring-panel` и одного активного bot runtime на канал может быть достаточно для первого production-среза;
- но даже для такого профиля обязательны `PostgreSQL + Redis + RabbitMQ + S3-compatible storage`;
- отказоустойчивость нужно обеспечивать не ручными договорённостями, а явным deployment и recovery-процессом.

## 4. Роли и зоны ответственности

- `Release owner`:
  утверждает go/no-go, фиксирует окно запуска, координирует rollback.
- `Backend owner`:
  отвечает за `spring-panel`, миграции, readiness, internal API, бизнес-инварианты.
- `Integration owner`:
  отвечает за `java-bot`, каналы `Telegram/VK/MAX`, webhook/long-poll ingress, outbound delivery.
- `Infra owner`:
  отвечает за `PostgreSQL`, `Redis`, `RabbitMQ`, object storage, reverse proxy, secrets, backup/restore.
- `Support lead`:
  подтверждает операторскую готовность, smoke рабочего места, первую волну мониторинга после релиза.

Если один человек совмещает несколько ролей, это нужно явно зафиксировать до запуска.

## 5. Go/No-Go критерии

### 5.1. Можно идти в production, если одновременно выполняется всё ниже

- production contour реально использует `PostgreSQL`, а не compatibility `SQLite`;
- `Settings -> Production readiness` возвращает `ready`, а не `degraded` или `compatibility`;
- `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`;
- `APP_COORDINATION_MODE=redis`;
- object storage переведён в `s3`-совместимый режим и bucket probe успешен;
- bot runtime contract не содержит прямых `SPRING_DATASOURCE_*` для child processes;
- задан безопасный `APP_INTERNAL_BOT_API_TOKEN`;
- задан безопасный `APP_SECURITY_REMEMBER_ME_KEY`;
- если в external DB ещё нет пользователя с `ROLE_ADMIN`, заранее заданы:
  - `APP_SECURITY_BOOTSTRAP_ADMIN_USERNAME`
  - `APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD`
- проведён rehearsal миграции или есть подтверждённый чистый старт без legacy cutover;
- есть проверенный backup и проверенный способ restore;
- есть ответственный за release window и rollback.

### 5.2. Это автоматический No-Go

- production запускается на `jdbc` transport;
- panel или bot используют local fallback вместо `Redis`/`RabbitMQ`;
- production secrets остались на встроенных дефолтах;
- неизвестно, где лежат canonical attachments;
- нет rollback-плана;
- нет smoke для входящего сообщения и ответа оператора;
- нет мониторинга очередей, DLQ, PostgreSQL и object storage;
- нет подтверждения, что внешние API credentials актуальны.

## 6. Production readiness checklist

### 6.1. Инфраструктура

- Подготовлен production host или cluster для `spring-panel`.
- Подготовлен host или deployment для `java-bot`.
- Есть отдельный production `PostgreSQL`.
- Есть отдельный production `Redis`.
- Есть отдельный production `RabbitMQ`.
- Есть production bucket в `MinIO/S3-compatible` storage.
- Настроен reverse proxy с TLS перед `spring-panel`.
- Зафиксированы DNS-имена, порты, firewall rules и access lists.
- Зафиксирован способ перезапуска сервисов без ручного SSH-квеста.

### 6.2. Деплой и артефакты

- Для `spring-panel` есть воспроизводимый deploy-пакет.
- Для `java-bot` собраны production jars.
- Используется явный production launcher (`jar` предпочтителен).
- Известно, где лежат артефакты каждого bot module.
- Конфигурация запуска лежит в versioned deploy-артефакте или секретном хранилище, а не в чьих-то локальных заметках.

### 6.3. Безопасность

- `APP_INTERNAL_BOT_API_TOKEN` переопределён безопасным значением.
- `APP_SECURITY_REMEMBER_ME_KEY` переопределён безопасным значением.
- `APP_SECURITY_BOOTSTRAP_ADMIN_USERNAME/PASSWORD` заданы, если нужны для первого старта.
- Доступ к `/internal/api/bot/**` ограничен не только токеном, но и сетевым контуром.
- Входящие webhook secrets актуальны и проверены.
- Ротация production secrets описана заранее.
- Пароли/токены не лежат в `.env`, если этот файл не защищён контуром deployment.
- Production admin-аккаунт не использует `admin/admin`.

### 6.4. Данные и миграция

- Известен источник данных для cutover.
- Есть снимок данных до запуска.
- Есть backup `PostgreSQL`.
- Есть backup `attachments/object storage`.
- Если миграция идёт с legacy SQLite, проведён rehearsal на production-like копии.
- Есть сверка критичных сущностей:
  - tickets
  - messages
  - channels
  - users/roles
  - attachments references
  - incidents/monitoring state, если это входит в scope запуска

### 6.5. Интеграции

- Проверены токены `Telegram`, `VK`, `MAX`.
- Проверены webhook/base URLs.
- Проверены внутренние `APP_PANEL_INTERNAL_API_*`.
- Проверены quota/rate-limit ограничения, если они есть у провайдера.
- Для каждого канала понятен fallback-сценарий при недоступности внешнего API.

### 6.6. Наблюдаемость

- Есть доступ к логам `spring-panel`.
- Есть доступ к логам bot runtime.
- Есть проверка PostgreSQL availability.
- Есть проверка Redis availability.
- Есть проверка RabbitMQ queues и DLQ.
- Есть проверка object storage availability.
- Есть проверка incident/transport surfaces в UI.
- Есть понятный канал alerting для команды запуска.

### 6.7. Операторская готовность

- Есть тестовый operator account.
- Проверен вход в panel.
- Проверен dialogs workspace.
- Проверен reply flow.
- Проверены уведомления и базовые action-сценарии.
- Операторы знают, куда смотреть при деградации: `/incidents`, transport analytics, production readiness.

## 7. Обязательная production-конфигурация

Минимальный production baseline:

```text
APP_DB_MODE=postgresql
APP_INTEGRATION_TRANSPORT_MODE=rabbitmq
APP_COORDINATION_MODE=redis
APP_STORAGE_OBJECT_MODE=s3
APP_INTERNAL_BOT_API_TOKEN=<secure-random-secret>
APP_SECURITY_REMEMBER_ME_KEY=<secure-random-secret>
```

Для panel external contour дополнительно должны быть заданы:

```text
SPRING_DATASOURCE_URL=...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
SPRING_RABBITMQ_HOST=...
SPRING_RABBITMQ_USERNAME=...
SPRING_RABBITMQ_PASSWORD=...
APP_STORAGE_OBJECT_BUCKET=...
APP_STORAGE_OBJECT_ENDPOINT=...
APP_STORAGE_OBJECT_ACCESS_KEY=...
APP_STORAGE_OBJECT_SECRET_KEY=...
```

Для bot runtime в `rabbitmq` contour обязательны:

```text
APP_DB_MODE=worker
APP_INTEGRATION_TRANSPORT_MODE=rabbitmq
APP_PANEL_INTERNAL_API_BASE_URL=...
APP_PANEL_INTERNAL_API_TOKEN=...
```

И запрещены как live-path:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
DATABASE_URL
APP_DB_BOT_RUNTIME
SUPPORT_BOT_DATABASE_PATH
```

## 8. Preflight за 1-3 дня до релиза

### 8.1. Freeze и подготовка

- Зафиксировать release branch или commit SHA.
- Заморозить несвязанные schema/runtime changes.
- Подготовить release notes для команды.
- Назначить точное окно релиза и rollback cutoff.

### 8.2. Техническая проверка

- Проверить сборку `spring-panel`.
- Проверить сборку production bot jars.
- Проверить наличие всех secrets.
- Проверить доступность production infra endpoints.
- Проверить readiness каждой очереди RabbitMQ.
- Проверить доступность bucket.
- Проверить health reverse proxy и TLS.

### 8.3. Rehearsal

Подробный сценарий выполнять по [postgresql-cutover-rehearsal.md](./postgresql-cutover-rehearsal.md).

- Прогнать staging или pre-prod сценарий запуска.
- Прогнать smoke:
  - inbound message
  - ticket creation
  - operator reply
  - outbound delivery
  - attachment path
- Прогнать recovery path:
  - остановка одного runtime
  - повторный старт
  - чтение логов
  - проверка UI incident/recovery surfaces

## 9. Запуск в день релиза

### 9.1. Перед началом окна

- Подтвердить, что команда запуска на связи.
- Подтвердить, что alerting-канал включён.
- Подтвердить freeze на конфликтующие изменения.
- Сделать финальный backup.
- Зафиксировать текущее production/non-production состояние.

### 9.2. Порядок запуска

1. Подтвердить доступность `PostgreSQL`, `Redis`, `RabbitMQ`, object storage.
2. Развернуть и проверить `spring-panel` с production env.
3. Убедиться, что panel startup не упал на fail-fast security или readiness guards.
4. Проверить `Settings -> Production readiness`.
5. Развернуть `java-bot` runtime jars.
6. Проверить runtime contract для production channels.
7. Запустить bot child processes.
8. Включить ingress/webhooks/live traffic.
9. Выполнить smoke на реальном контуре.

### 9.3. Что смотреть сразу после старта

- panel стартовал без schema/startup ошибок;
- `Production readiness = ready`;
- RabbitMQ queues не накапливают неожиданную DLQ;
- internal bot API отвечает авторизованно;
- bot child processes не получают direct DB credentials;
- incident/recovery surfaces пусты или объяснимы;
- логи читаются в UTF-8 без mojibake.

## 10. Smoke после запуска

Минимальный production smoke:

Подробный пошаговый пакет выполнять по [production-e2e-smoke.md](./production-e2e-smoke.md).

### 10.1. Входящий путь

- Отправить тестовое сообщение в `Telegram`.
- Отправить тестовое сообщение в `VK`, если канал участвует в запуске.
- Отправить тестовое сообщение в `MAX`, если канал участвует в запуске.
- Убедиться, что обращение дошло до panel и появилось в dialogs.

### 10.2. Операторский путь

- Открыть обращение оператором.
- Назначить или подтвердить owner.
- Отправить ответ оператором.
- Убедиться, что reply ушёл клиенту.

### 10.3. Служебный путь

- Проверить attachments/media, если они входят в launch scope.
- Проверить incident surfaces.
- Проверить transport analytics.
- Проверить readiness snapshot.

### 10.4. Негативный контроль

- Убедиться, что не растёт unexplained DLQ.
- Убедиться, что нет silent fallback в SQLite/local business path.
- Убедиться, что child runtime не держит неожиданный direct datasource.

## 11. Rollback plan

Rollback должен быть согласован до релиза, а не придуман в процессе.

### 11.1. Триггеры rollback

- `Production readiness` устойчиво остаётся `degraded`;
- входящие сообщения принимаются, но не доходят до operator UI;
- operator replies не доставляются наружу;
- массовый рост DLQ;
- object storage недоступен;
- панель не проходит auth/security/startup guards;
- выявлена ошибка миграции данных или неконсистентность критичных сущностей;
- production admins не могут войти и нет безопасного recovery path.

### 11.2. Минимальный rollback сценарий

1. Остановить ingress/live traffic.
2. Остановить bot child processes.
3. Остановить новый runtime panel, если он повреждает поток.
4. Вернуть предыдущий release artifact.
5. При необходимости восстановить DB из backup или откатить на заранее согласованный snapshot.
6. Вернуть предыдущий configuration/secrets set.
7. Повторно выполнить smoke уже на rollback-контуре.

### 11.3. Что нельзя делать при rollback

- Нельзя смешивать старый код и новую схему без понимания совместимости.
- Нельзя включать трафик до проверки входящего и исходящего пути.
- Нельзя оставлять частично работающий transport contour без наблюдения за DLQ.

## 12. Первая неделя после запуска

### 12.1. Первые 2 часа

- Наблюдать очереди RabbitMQ.
- Наблюдать readiness.
- Проверять incidents/recovery surfaces.
- Проверять operator feedback по UI и reply flow.

### 12.2. Первый день

- Снять статистику по:
  - входящим обращениям
  - успешным ответам
  - failed outbound
  - DLQ
  - latency до первого ответа
- Проверить, что нет скрытого накопления attachment/storage проблем.

### 12.3. Первые 7 дней

- Ежедневно проверять:
  - PostgreSQL health
  - Redis health
  - RabbitMQ health
  - object storage health
  - incident delivery
  - канальные интеграции
- Отдельно собирать production issues в backlog:
  - observability gaps
  - support monitoring gaps
  - integration retry/replay improvements
  - operator UX pain points

## 13. Что ещё остаётся после первого production launch

Даже после успешного запуска это не считается финальной зрелостью контура. Следующий обязательный слой развития:

- базовый observability stack уровня metrics/alerts/dashboard;
- расширенный мониторинг support-систем и внешних зависимостей;
- richer worker forensics и replay tooling;
- формальный migration rehearsal runbook;
- автоматизированный deployment recipe.

Это соответствует отдельным задачам:

- `01-194` observability baseline;
- `01-195` ingress/egress hardening;
- `01-196` migration rehearsal и E2E smoke;
- `01-197` расширение monitoring coverage.

## 14. Короткий go-live checklist

Если нужен совсем короткий operational cut перед нажатием кнопки, используйте этот список:

- `PostgreSQL`, `Redis`, `RabbitMQ`, `S3-compatible storage` доступны.
- `APP_DB_MODE=postgresql`.
- `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`.
- `APP_COORDINATION_MODE=redis`.
- `APP_STORAGE_OBJECT_MODE=s3`.
- Security secrets не дефолтные.
- Есть valid `ROLE_ADMIN` или bootstrap admin credentials.
- Есть backup и rollback owner.
- `Production readiness = ready`.
- Smoke inbound + operator reply + outbound delivery пройден.
- Команда знает, где смотреть incidents, queues, logs и readiness.
