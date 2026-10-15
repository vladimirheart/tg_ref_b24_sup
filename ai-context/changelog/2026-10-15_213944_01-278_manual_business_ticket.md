# 01-278 R110: локальный клиент и ручное обращение независимого бизнеса

- Время: 2026-10-15 21:39:44 MSK.
- Промт пользователя: «да» для обоих пунктов.
- Подтверждённые пункты предыдущего уточнения: при ручном создании обращения
  без бота автоматически создавать локального клиента с новым ID; в общем
  канале явно выбирать бизнес до первого вопроса.
- Baseline/HEAD/origin_main: `83403f0be684deec1a08907ba45dde363d299d71`.
- Состояние: `source_applied=true`, `staged=false`, `committed=false`,
  `pushed=false`, `deployment=false`; отдельная sandbox не запускалась.

Изменены `ai-context/rules/03-multibusiness-s0-contract.md` и
`ai-context/tasks/task-details/01-278.md`: закреплены оба решения и граница
между реализованным ручным путём и ожидающим S4 общим каналом.

Добавлена `spring-panel/src/main/resources/db/migration/postgresql/V54__manual_business_clients.sql`:
таблица локальных клиентов с FK на бизнес/оператора и отрицательная
последовательность ID в точном для JavaScript диапазоне. Ни одна существующая
строка не меняется. Добавлены `ManualBusinessTicketCreationService` и
`ManualBusinessTicketController`: transactional создание клиента, root message,
canonical ticket и первого span без внешнего канала; явный business owner,
проверка роли и повторная проверка в SQL INSERT; опциональная локация только
своего бизнеса. `TicketLocatorService` теперь принимает ненулевой отрицательный
ID при точном composite lookup.

Добавлен выключенный по умолчанию `multibusiness.manual-ticket-creation` в
`MultibusinessFeatureFlags`; `BusinessScopePageController`, шаблон
`businesses/dialogs.html` и `business-dialogs.js` показывают форму ручного
создания только под флагом и отправляют выбранный business ID с CSRF. Форма не
выводит данные через HTML. Обновлены/добавлены tests: флаги, точный locator,
полная Flyway-цепочка, PostgreSQL service isolation/fail-closed,
WebMvc security/gate и Node UI-сценарий.

Чистый targeted Maven `clean test`: 12/12 GREEN; отдельный чистый
`BusinessPagesWebMvcTest` 1/1 GREEN, включая видимость формы по флагу.
Все 22 Node UI-теста,
`node --check` и `git diff --check` GREEN. Рабочая БД, флаги, миграция в
рабочей среде, backfill, deployment и Git staging/commit/push не выполнялись.
Общий канал и business-scoped вопросники ещё не реализованы.
