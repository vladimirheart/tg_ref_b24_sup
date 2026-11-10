# Границы данных AI — R307

## Изолированные пути

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

## Проверка

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
старого image код не обновит. R306 и R307 можно включить в один согласованный
backend выпуск. Сохранить текущий Compose/ENV/mounts/replica counts и rollback
pointer на машине проекта; не возвращать старые R300 images. Bot children и
инфраструктура не требуют изменения этим source scope. Последний
документированный runtime — R305/V89, а не наблюдение этой source-сессии.

После выпуска при foundation/strict проверить legacy suggestions/control/debug:
пустой результат, отсутствие старых AI state/events/learning/context/LLM effects;
новый scoped BUSINESS handoff должен сохраняться. Затем обычная сессия R306 по
[общему контракту](common-system-access.md). Runtime/browser checks этого шага
здесь не выполнялись.

Остаются правила сроков хранения и удаления AI-данных, обработка media/LLM
контекста, фактическая scoped доставка и решение о допуске auto-reply. Не
выбирать срок хранения или право внешней отправки за пользователя. Старые
данные тестовые, heuristic migration/backfill не нужен; общая задача01-278🟡.
