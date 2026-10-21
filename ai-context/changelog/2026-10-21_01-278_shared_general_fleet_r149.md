# 01-278 — GENERAL из общего каталога во всех ботах (R149)

Дата: 2026-10-21, Europe/Moscow. Реализация и проверки начаты 2026-10-20.
Статус задачи 🟡.

Промты пользователя:

> продолжай. возьми более широкий пакет. напоминаю, что рабочий экземпляр проекта на другой машине.
> коммит и пуш разрешаю сразу после внечения правок без моего подтверждения - разрешение только в течении этих суток

Повторное разрешение на текущие московские сутки:

> продолжай. возьми более широкий пакет.
> коммит и пуш разрешаю сразу после внесения правок без моего подтверждения - разрешение только в течении этих суток

Последующее продолжение: «продолжи».

Пользователь явно заменил прежний ручной git flow разрешением commit/push после
проверок без нового подтверждения только в пределах текущих московских суток
2026-10-21 (до 2026-10-22 00:00 +03:00). Разрешение 2026-10-20 истекло;
commit/push до повторного разрешения не выполнялись.
Рабочий экземпляр находится на другой машине. Docker/image evidence R143–R148
относится к той сессии; здесь рабочий stack/DB/flags не проверялись/не менялись.
Локальный Docker был выключен; запущен для isolated PostgreSQL Testcontainers.
Server 29.7.2; нет deploy/restart рабочего проекта/рабочих migrations/backfill.

Связный пакет включает V75 provenance/immutable owner, v2 INSERT/replay,
writer ACK=2, nullable SDK и GENERAL menu/session/queue retry в MAX/VK/Telegram.

- V75 расширяет только CHECK нового questionnaire catalog: GENERAL/USER_SELECTION
  имеет business_id=NULL и business_selection_source=NULL, свой ID/SHA и общий
  catalog SHA. V73/V58 fingerprints остаются immutable; новый trigger также
  фиксирует выбранные owner/kind/source/channel/composite ID. Старые rows не
  переписываются; GENERAL-only и nullable legacy representation сохраняются.
- Writer заново проверяет flags, platform/active channel, полный current catalog,
  membership и selected ID/SHA до root/children. BUSINESS V1 поддерживается без
  active shared GENERAL reservation; с ней требуется v2 catalog, а не каталог,
  в котором GENERAL отсутствует. USER_SELECTION без version proof не превращает
  shared channel в legacy/GENERAL-only. Inbox, root/composite locator и child
  writes атомарны; inbox проверяет повторный payload и сохраняет статус ошибки.
  Exact replay хранит frozen version и не захватывает новый owner.
- V2 choices/selection forStart выдаёт ACK=2 только при strict writer; preview
  не выдаёт ACK. GENERAL требует general resolution. BUSINESS-only v2 start не
  требует включать GENERAL при отсутствии active GENERAL binding. HMAC покрывает
  path/query/forStart. SDK проверяет обе версии, NULL versus fake owner, duplicate
  scope/template, selected bytes SHA и отсутствие global locations/presets;
  новые starts не откатываются на v1 при ошибке/отсутствии ACK=2.
- MAX/VK/Telegram имеют отдельный GENERAL command/button namespace и выбор до
  первого вопроса, JSON/CAS persistence и exact template flow. ScopeKind + nullable
  business ID сопоставляются с сохранённым меню; version/channel/catalog обязаны
  совпасть. Stale nonce не является ответом. Сессия/ответы переживают rebuild,
  final publication key фиксируется до queue; неизвестный итог повторяет ту же
  frozen command, а не перечитывает текущие bindings. Shared GENERAL использует
  выбранную сохраняемую ветку, не legacy GENERAL-only path. Raw support-chat,
  local/global settings, legacy feedback/media fallback не открываются.


Проверки: 778 Java tests / 77 XML, failures/errors/skips=0.
Backend 263/10, полный fleet 515/67;
Node R117/R134 GREEN. Flyway V75 и upgrade V74→V75 сохраняют прежние rows.
Первый backend-прогон: 263 теста / 10 XML, 10 ошибок новых fixtures:
девять случаев распаковки nullable Long в тернарном выражении и SQL dollar
delimiter, повреждённый при генерации тестового текста. Assertions и production
constraints сохранены; повторены 75 creation + 29 catalog/security тестов,
остальные 159 GREEN-результатов сохранены. HTTP также проверяет BUSINESS-only
v2 start при выключенном GENERAL и отсутствии active GENERAL binding.

В первом fleet-прогоне SDK прошёл 241 тест; Telegram test-compile остановился
на вызове helper с тремя аргументами вместо четырёх. Вызов исправлен, повторены
все provider suites без повторения SDK. Telegram 90 и VK 73 прошли. В MAX один
старый JSON fixture создавал выбранный BUSINESS scope без сохранённого меню;
теперь он использует pending + choose и дополнительно сравнивает меню после
JSON. Все прежние scope/order/answer assertions сохранены; повторены 110 MAX
тестов без изменений production-кода ради fixture.

Финальное review выявило ошибку приоритета null-guard в новом VK detector
команд. Скобки восстановили прежний контракт для отсутствующего текста.
Добавлен regression case для отсутствующего, обычного и questionnaire payload,
включая выбор только через payload. После этой production-правки повторены все
74 VK-теста; остальные актуальные GREEN reports сохранены. Retry helper v4
остановился до Maven из-за предположения о четырёх VK-классах; свежая проверка
нашла ровно три класса, v5 явно перечисляет их все. Логи промежуточных ошибок
и повторных прогонов сохранены в .tmp/r149-*.
Bots transform v1 остановился до tracked writes на неверном VK parser anchor;
v2 использует фактический parser. Unsupported menu version cases используют 3
после добавления поддержки 2, остальные assertions не ослаблялись.

Guarded apply: pinned baseline ccaa0396c3bc1ecb6ec9d0e2dc73f33f4097807c,
parent 405b80996abe03431a78417808bcbe15a391ffc9, actual R148 18 files;
clean main/index, detached validation, exact manifest/hash/report freshness,
generated CSS исключён. Git commit/push выполняются отдельными проверяемыми
шагами с проверкой московской даты и без force; итоговые SHA/state — в git и
.tmp/r149-result.json. Source apply/commit не означают runtime acceptance.

Scoped locations/presets/media/edit/feedback, >100 choices, live providers/
Redis/proxy, TTL/retention/offset/retry и rolling-upgrade остаются PENDING.
Полный остаток — в актуальной сводке 01-278; вся задача не закрыта.
