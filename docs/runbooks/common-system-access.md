# Общий доступ к инцидентам и системным уведомлениям — R306

Решение пользователя от2026-11-10: «все всем, кроме распределения настроек бота».
Это общая модель доступа для действующих пользователей панели. Никаких новых
business grants или incident memberships не требуется.

| Объект/действие | Доступ |
| --- | --- |
| /incidents и весь /api/incidents lifecycle: read/write/events/watchers/routes/redelivery | Любая текущая включённая, незаблокированная учётная запись панели |
| Направления incident monitoring и внешние объекты инцидентов | Та же общая модель; mutation требует CSRF |
| Справочник сотрудников для ответственного/наблюдателей/маршрута | Минимальные username/label действующих однозначных сотрудников |
| Колокольчик, unread/cursor/read/read-all | Только свой inbox; все действующие пользователи имеют доступ |
| Источник: диалог, задача, проект, паспорт/диагностика | Действуют собственные page/module/business/object проверки |
| Боты/каналы, их credentials и распределение настроек | Системный ROLE_ADMIN плюс PAGE_CHANNELS или PAGE_SETTINGS; прежние ограничения сохраняются |

SharedSystemAccessService проверяет numeric PanelUserPrincipal, точный raw
username и текущее enabled/is_blocked в users. Неподписанный, строковый,
удалённый, переименованный либо переиспользованный principal не даёт доступа.
CSRF остаётся обязательным для браузерных изменений. Пункт меню доступен
обычной numeric сессии без PAGE_DIALOGS/PAGE_TASKS/PAGE_OBJECT_PASSPORTS.
Текущий читатель задачи видит связанные общие incident summaries.

Все действующие пользователи могут быть получателями; конкретную доставку
выбирают существующие маршруты/наблюдатели и notification routing. Это не
принудительная рассылка каждого сообщения всем и не общий журнал прочтения.
Общие notifications с URL /incidents или /settings (граница / ? # или конец)
и без ticket/shared-work scope видимы при strictReadScope. List/count/cursor
и отметки прочтения используют один SQL predicate. Неизвестные unclassified
старые записи и untyped dialog/GENERAL/task/project notices остаются скрытыми
в строгом режиме; typed private notices сохраняют свой текущий READ.
SSE notifications_changed не содержит текст уведомления или private поля;
получение контента идёт через защищённый inbox API.

## Проверка

162 tests/17 suites GREEN, failures/errors/skips0. Среди них40 actual
PostgreSQL/JPA/HTTP cases: обычная сессия без page/business/shared grants,
внешний каталог, мониторинг/CSRF, bot admin запрет, блокировка/disabled/rename/
delete/username reuse/wrong numeric ID/raw whitespace/string principal,
связанные incident summaries и strict inbox pagination/count/read-all.
Baseline до правок:127 tests/3 suites GREEN и compile748 main/710 test sources.
R306 добавляет один main class; изменённые sources скомпилированы поверх базы.

При расширении проверки ChannelApiControllerWebMvcTest дал8 failures/50 tests.
Те же8 воспроизведены в отдельном неизменённом worktree5b3a09013; этот набор
не входит в GREEN162 и его assertions не ослаблялись. Логи R306 RED/GREEN и
baseline proof сохранены в .tmp/r306-*. Ранние собственные RED исправлены
в test fixtures (bean name/duplicate bean/normalizer mock), product asserts сохранены.

## Текущий runtime R316 и историческая приёмка R309

Текущие web/worker — imagec05e3e159639, baseline7a2ad5969 +uncommitted R316,
схема V91 прежняя. Модель общего доступа/settings/principals сохранена;
foundation/ui-selector включены только для controlled AI activation, manual
creation выключен.32 Edge AI gates включают incidents/inbox200.
Актуальные Compose/rollback/evidence — Runtime R316 в карточке01-278.
Ниже сохранена более широкая функциональная приёмка common доступа R309;
весь её браузерный набор R316 повторно не запускал.

R306 включён в рабочие web/worker на этой машине; health/readiness200UP,
V89/flags/.env прежние, боты и инфраструктура не обновлялись.
23 actual Edge проверки подтвердили обычную сессию без page grants, внешний
объект, UI create/reload инцидента, реальную доставку notice/SSE, переход в
нужную карточку и личный read; блокировка закрывает инциденты/inbox/monitoring
в уже открытой сессии. Чтение monitoring/can_edit проверено live, сохранение
monitoring — в изолированных HTTP tests; рабочую конфигурацию не меняли.

R309 исправляет обе новые ссылки producer/outbox: /incidents?incidentId=<id>.
Раньше они указывали на /dialogs и скрывались строгим inbox R306. Workbench
принимает строковый numeric ID напрямую, даже вне первой страницы; stale/invalid
query не подменяет ID. Старые уведомления не переклассифицированы.
Исправлен boolean binding блокировки пользователя; PostgreSQL block/unblock
и реальный stale session проверены. Каналы/боты остаются закрыты обычной сессии.

Web imageee124b5d137e, worker9201cfbf6d5a; HEAD5dd39138a +uncommitted R309 patch.
Compose и rollback pointers — Runtime R309 в карточке01-278.528 Java/21 suites,
11 new Node cases и прежние UI regressions GREEN; полный repository suite
не объявляется GREEN. Тестовые записи удалены, общая S6 остаётся отдельно.

## Историческая инструкция активации source R306

Текущая сессия — source/test на другой машине. Рабочие Docker/БД/Redis/queues/
provider не использовались и не изменялись. Disposable native PostgreSQL имеет
отдельный nonce/порт и не является рабочей БД. Общая задача01-278 остаётся🟡.

Нужна пересборка backend image из исходников R306 и обновление обеих ролей
panel-web/ops-worker через существующую процедуру релиза с --no-deps и по одной
replica. Один restart прежних контейнеров новый код не установит. Новых SQL
миграций/флагов/principals нет; bot-runner, children и инфраструктуру для R306
обновлять не требуется. Использовать актуальный R305 Compose pointer из
[shared-work runbook](shared-work-access.md), предварительно закрепив новый image
обеих backend ролей; повторный up старого compose.final.json вернёт старый image.

После выпуска проверить обычную сессию без page grants: открытие инцидентов,
создание внешнего объекта, выбор ответственного/наблюдателя, сохранение и
перечитывание инцидента/monitoring, появление своего incident notice в строгом
режиме. После блокировки та же сессия должна получать отказ; bot/channel
управление обычному пользователю недоступно. Зафиксировать image/source/health
и результат отдельно. Эти runtime/browser проверки здесь не выполнены;
последний подтверждённый runtime — R305/V89, боты R295.
