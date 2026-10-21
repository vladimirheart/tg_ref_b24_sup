# 01-278 — v4 каталог и страницы выбранных вопросников (R152)

Дата: 2026-10-21, Europe/Moscow. Статус задачи 🟡.

Промты пользователя:

> продолжай

> продолжи

Сохраняются размер пакета и ограничение среды:

> давай дальше большим пакетом

> продолжай. возьми более широкий пакет. напоминаю, что рабочий экземпляр проекта на другой машине.
> коммит и пуш разрешаю сразу после внечения правок без моего подтверждения - разрешение только в течении этих суток

Разрешение на commit/push обновлено на 2026-10-21 Europe/Moscow и действует до
2026-10-22 00:00 +03:00; default manual git flow заменяется только на эти сутки.
Это не разрешение rollout. Рабочий экземпляр — на другой машине и в другой сессии.

- Добавлены signed v4 choices/selection и строгий writer ACK=4. Новые starts
  общего каталога идут только через v4, без downgrade; прежние v1/v2/v3 endpoints
  и сохранённые сессии обслуживаются отдельно, в v4 автоматически не переводятся.
  Подпись включает path/query/forStart; Cache-Control=no-store сохранён.
- V4 допускает до 1000 choices на канал: 1000 BUSINESS либо до 999 BUSINESS и
  отдельный GENERAL. Переполнение закрывает весь каталог, без усечения/скрытия
  GENERAL. Старые версии сохраняют предел 100. Общее число бизнесов в системе
  этим лимитом меню не ограничивается. GENERAL bind учитывает новый v4 budget.
- Каталог фиксируется целиком в сессии и разбит на локальные страницы по 8
  вариантов, до 2 navigation buttons. Переходы не запрашивают новый каталог;
  membership/ID/template/resource hashes проверяются при выборе и перед INSERT.
  Переименование, изменение шаблона либо ресурсов любого, в том числе далёкого
  пункта, меняет общий catalog SHA и требует нового выбора. Labels не являются
  owner identity; GENERAL сохраняет nullable business_id и пустые ресурсы.
- V4 registry делает один общий reservation lookup вместо чтения глобальных
  settings на каждый бизнес. Ресурсы всех участников проверяются/хешируются,
  но runtime payload и ресурсы удерживаются только для выбранного бизнеса;
  копии вопросников/локаций всех участников не создаются. Shared v4 selection
  использует один capture бизнес-каталога и выбранного runtime в своей транзакции.
  Цена capture по-прежнему линейна по числу бизнесов/ресурсов; server paging,
  нагрузочная и live Redis/retention приёмка не объявляются выполненными.
- SDK проверяет v4 capacity, exact ACK и byte/resource proof. MAX/VK/TG сохраняют
  frozen каталог/выбор и собственные ресурсы, включая выбор на последней странице.
  MAX получил страницу в Selection, guarded native navigation и сохранение до
  provider send. Page=0 не добавляется в JSON прежних сессий; отсутствующее поле
  восстанавливается как 0. Telegram допускает page token до страницы 124.
  VK использует прежнюю native pagination с новым валидатором каталога.
- Ingestion узнаёт v4 по проверенному catalog SHA через selectExpanded. До
  первого нового root/child INSERT применяет тот же required/forward path и
  canonical attributes validator, что v3. Frozen replay не перечитывает текущий
  каталог, не переносит владельца и не создаёт новые children. Новая migration
  не нужна: минимальная source-схема остаётся V75.
- Validation: 837 Java tests / 75 XML, failures/errors/skips=0;
  backend 259/7 и полный fleet 578/68. PostgreSQL границы 101/999+
  GENERAL/1000/1001, отсутствие усечения, offscreen resource/name invalidation,
  signed HTTP/ACK, создание/replay/rollback после первой сотни во всех трёх
  платформах, все native buttons до 1000, JSON restore и MAX send retry проверены.
  Node suites отдельно не запускались: JavaScript source не менялся.
- Реализация/валидация — detached sandbox .tmp/01-278-r152, затем guarded source
  apply с exact 27 files/content hashes. Generated Sass CSS исключён. Baseline
  51e89ff2e19df855f397448cc9569fe7a37a27c7, parent
  8cf6d3fb181b2c10616065e2d5e75ce6bfd378ac; R151 actual 21 files проверен.
- Рабочий Docker и проект находятся на другой машине, в другой сессии агента.
  Здесь только source и isolated tests. Рабочие revision/DB/flags неизвестны;
  deploy/restart/рабочие migration/backfill/flags/provider sends не выполнялись.
  Нужны совместимые backend/SDK/боты с ACK=4 и отдельная приёмка на рабочей машине.
- Общий статус задачи 🟡. Далее scoped media/edit/feedback выбранных потоков,
  provider/Redis/proxy, rolling-upgrade/retention, прочие S3–S5, S2B и S6 gates.

Commit/push выполняются отдельным оператором после GREEN, проверки exact scope,
content guards, actual remote и текущей даты. Исторические changelog не изменялись.
