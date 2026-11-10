# Границы данных AI — R308

## Хранение и ручная очистка — R308

Согласовано пользователем: «Всё хранить до ручного удаления, без автоочистки».
AI-данные не получают автоматический TTL или расписание удаления. Архивирование
материала само по себе сохраняет текст. Нынешний шаг реализует очистку заголовка
и текста KNOWLEDGE/MEMORY; общий purge scoped state/events и поиск orphan
материалов пока остаются отдельной работой. Политика не меняет срок хранения
инфраструктурных backup и истории monitoring.

В редакторе существующего материала раскрыть «Очистить содержимое», отметить
подтверждение и отправить форму. Заголовок становится «Материал очищен», тело —
«Содержимое очищено.», статус ARCHIVED и revision+1. Предыдущий текст из панели
не восстанавливается. ID/key/business/origin, история metadata, fingerprints,
chat_history и резервные копии этим действием не удаляются. Для нового материала
или читателя без CONFIGURE форма отсутствует.

POST /business-ai-sources/erase-content требует PAGE_KNOWLEDGE_BASE, текущий
READ+business ADMIN либо ALL_BUSINESSES ADMIN, CSRF, confirmed=true, точные
business/kind/key/sourceId/revision. ROLE_ADMIN без бизнес-прав не подходит.
400 — нет подтверждения/некорректные параметры,403 — отказ page/business,
CSRF отказ использует существующий redirect /error/403 без записи данных,
409 — старая/подменённая форма или отзыв доступа после precheck,404 — rollout
foundation/uiSelector выключен. Ответы controller no-store; redirect в каталог
того же бизнеса. Numeric ID и точное имя/состояние аккаунта повторно проверяются
в SQL, как и права/active business/CAS; audit failure откатывает всё изменение.

MEMORY сохраняет неизменного владельца. Ранее открытая форма позволяет очистить
её текст после смены/удаления origin без чтения другого бизнеса; обычный editor/
catalog/retrieval по-прежнему скрывает stale origin. Отдельного UI поиска orphan
пока нет. Очистка увеличивает revision и снимает APPROVED, поэтому сохранённый
кандидат и handoff evidence больше не допускаются. Новое последующее одобрение
другого текста не оживляет старую frozen evidence revision.

312 tests/6 suites GREEN, failures/errors/skips=0 в source sandbox. Проверены реальные native PostgreSQL и полный
Spring Security/HTTP/Thymeleaf, включая CSRF из отрендеренной формы, оба kind,
матрицу прав, revocation/rename-reuse перед SQL, CAS, audit rollback, origin loss
и handoff invalidation.2 main/3 test sources пересобраны поверх проверенного
R307 cache1459 sources; HTML включён в hash manifest входов валидации. Полный
repository suite/image/runtime браузерный smoke не выполнялся. Известные8
ChannelApiControllerWebMvcTest failures R306 вне scope и не повторялись.

Приёмка на машине проекта после нового backend выпуска:

1. Business ADMIN открывает выделенный тестовый материал, читатель видит только
   чтение; у нового материала очистки нет. Простое «В архив» сохраняет текст.
2. Подтверждённая очистка меняет title/body,ARCHIVED,revision+1 и добавляет одну
   metadata-запись. Другой бизнес с тем же ключом сохраняется; audit не хранит текст.
3. Старая вкладка erase/revise получает409. После отзыва CONFIGURE/блокировки
   очистка не проходит. Отсутствующий CSRF/confirmation не меняет материал.
4. Старый approved кандидат/handoff не выдаёт очищенный текст. Chat history и
   evidence остаются на месте. Живой provider/LLM для этой проверки не требуется.

## Изолированные пути — R307

BUSINESS AI использует canonical owner tuple/public ID/business, текущий READ
и отдельные scoped records/sources. Поиск approved KNOWLEDGE/MEMORY ограничен
бизнесом обращения; curated MEMORY создаётся вручную с подтверждением очистки
PII/secrets. Incoming записывает handoff и references для оператора; scoped LLM,
скачивание media и автоматический ответ клиенту пока не включены.

Старая цепочка работала с global memory/intent/context и raw ticket ID. R307
закрывает попадание новых бизнес-данных в неё через facade и diagnostic API.

| Состояние | Legacy ticket-bound AI |
| --- | --- |
| foundation.enabled=true | Отклонён для любого ticket ID |
| strictReadScope=true | Отклонён для любого ticket ID |
| Оба флага false | Только одна точная строка с business_id NULL и is_general FALSE |
| BUSINESS/GENERAL/unknown marker/коллизия/нет policy/schema/SQL failure | Отклонён |

Один guard действует на incoming, operator reply/learning, suggestions, control,
review, processing state, ticket-filtered events и три diagnostics: decision trace,
reclassify, retrieval debug. Оба HTTP варианта /api/dialogs/{id}/... и
/api/dialog-ticket/... проходят через тот же service guard. При отказе сохраняется
existing response shape: пустые коллекции/maps, false/updated=false. Page rights
и CSRF по-прежнему проверяет внешний HTTP слой. Разрешение page gate не снимает
ограничение scope. Нет попытки подменить raw ID trimmed sibling или выбрать одну
из коллизий. Guard повторяет текущие flags/SQL на каждом обращении; разрешение
не кэшируется. Общие compatibility guards остальных модулей прежние.

При обоих выключенных rollout flags сохраняется только прежний UNRESOLVED
контур. Его admission — проверка на момент вызова; это не блокировка строки
на время сетевых операций. Для многобизнесового контура foundation/strict
закрывают raw вход полностью. Прямые низкоуровневые legacy primitives/offline
evaluation не объявляются универсальным public scoped API этим изменением.

## Проверка R307 (исторический source checkpoint)

282 tests/15 suites GREEN, failures/errors/skips0. Среди них33 facade guard cases,
28 real persistence round trips, legacy exact-ID и scoped incoming regression.
Четыре новые PG/standalone-MVC cases выполняют24 HTTP requests по обоим URL
вариантам с реальными guard/facade/runtime и разрешённым внешним page gate:
нет context/media/LLM calls, данных trace или записей legacy delegates.
Полный authentication/permission/CSRF transport отдельно покрыт existing MVC suites.
BUSINESS и GENERAL сначала воспроизведены RED на R306; финальные assertions
не ослаблялись. Test compile RED из-за имени foundation setter исправлен на
существующий nested setter; логи/XML сохранены в .tmp/r307-*.

1459 Java sources cache сверены с validated R306 с нормализацией LF/CRLF;
3 main/4 test изменённых sources пересобраны. Полная новая image сборка здесь
не выполнялась; полный repository suite не объявляется GREEN. Известные8
baseline channel-test failures R306 не повторялись и не исправлялись этим scope.

## Активация и оставшаяся работа

Рабочие Docker/проект находятся на другой машине и в другой сессии. Здесь
только source worktree и изолированная nonce-owned native PostgreSQL. Рабочие
БД/Redis/Rabbit/provider/configuration не использовались; новые migrations,
backfill, retention deletion, grants/flags/principals не выполнялись.

Для активации нужен новый backend image panel-web/ops-worker; простой restart
старого image код не обновит. R306–R308 можно включить в один согласованный
backend выпуск. Сохранить текущий Compose/ENV/mounts/replica counts и rollback
pointer на машине проекта; не возвращать старые R300 images. Bot children и
инфраструктура не требуют изменения этим source scope. Последний
документированный runtime — R305/V89, а не наблюдение этой source-сессии.

После выпуска при foundation/strict проверить legacy suggestions/control/debug:
пустой результат, отсутствие старых AI state/events/learning/context/LLM effects;
новый scoped BUSINESS handoff должен сохраняться. Затем обычная сессия R306 по
[общему контракту](common-system-access.md). Runtime/browser checks этого шага
здесь не выполнялись.

Срок согласован R308: хранение до ручного удаления, без автоочистки. Остаются
ручная очистка scoped state/events и поиск orphan материалов, обработка media/LLM
контекста, фактическая scoped доставка и решение о допуске auto-reply. Право
внешней отправки и auto-reply не подставлять вместо решения пользователя. Старые
данные тестовые, heuristic migration/backfill не нужен; общая задача01-278🟡.
