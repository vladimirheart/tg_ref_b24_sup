# R308 — ручная очистка содержимого AI-материалов

Промты пользователя:

- «следующий шаг».
- «Всё хранить до ручного удаления, без автоочистки».

Дата: 2026-11-10_113552, Europe/Moscow. Задача01-278 остаётся🟡.

Baseline HEAD/origin/main/actual remote3e894783e3d2c84f3cd4575fbbedba0b6ddada82,
parent9cf506526d9ca8ed5d44297579015a00effa024f; проверены subject и12 файлов R307.
R306/R307 committed/pushed пользователем. Исходный main/index чистые.
Изменения сначала в detached .tmp/01-278-r308-source, source apply только после
GREEN и exact baseline/raw SHA/blob/file-set guards.

## Изменения

- Зафиксировано отсутствие автоматической AI-очистки; срок больше не PENDING.
- Отдельная форма/POST erase-content подтверждает очистку title/body KNOWLEDGE/
  MEMORY. Фиксированные заглушки, ARCHIVED,revision+1, metadata audit одной SQL.
- PAGE_KNOWLEDGE_BASE/CSRF, live business CONFIGURE, bound numeric/raw account,
  active business, immutable ownership и frozen source ID/revision проверяются.
  Старые/подменённые формы и старые AI candidates/evidence отвергаются.
- MEMORY можно очистить из frozen формы после утраты origin без чтения чужого
  обращения. READ/editor/retrieval сохраняют прежний current-origin gate.
- Служебные ID/key/origin/events/fingerprints, chat history, backup не удаляются.
  Это content redaction, не полный purge; обычный archive сохраняет исходный текст.
- Обновлены task/S0/runtime contract и AI runbook с приёмкой на машине проекта.

## Проверка и фактический статус

312 tests/6 suites GREEN, failures/errors/skips=0. Real native PostgreSQL и полный Spring HTTP/Thymeleaf/CSRF,
KNOWLEDGE/MEMORY, role matrix, stale/forged CAS, late revocation/rename-reuse,
atomic audit rollback, orphan origin, handoff invalidation, сохранность чужого
бизнеса/чатов/evidence. В существующие prepared identity/page-revocation HTTP
матрицы включён новый endpoint без ослабления прежних assertions.
Первый прогон RED: новый тест ожидал прямой403 на отсутствующий CSRF, тогда как
existing panel handler перенаправляет на /error/403. Ожидание исправлено на точный
redirect с неизменным source/event snapshot; production security не менялась.
Расширенный V1 остановлен после3 полных прошедших regression suites и известного
source HTTP RED. Неполные create/read/revise matrices не считаются успешными.
Итог объединяет3 прошедших suites V1, полный source HTTP V2 и новые erase-cases
source/workbench V2; hashes всех входов совпадают, кроме исправленной строки теста.
Проверен cache R3071459 Java sources,2 main/3 test источника пересобраны; HTML
в input hash manifest. Логи/XML: .tmp/r308-validation-*, итог .tmp/r308-green.json.
Полный suite/image build не заявляется GREEN; прежние8 channel failures вне scope.
Sass CSS исключён только после сверки Git filtered blob с baseline.

Рабочий экземпляр/Docker находится на другой машине и в другой сессии. Native
PostgreSQL здесь — собственная nonce-owned disposable fixture, после проверок
остановлена. Рабочие DB/Redis/Rabbit/provider/config не использовались. Новых
migrations/flags нет; live очистка пользовательских данных не выполнялась.
sandbox_green=true; source_applied записывается отдельно .tmp/r308-applied.json;
staged=false,committed=false,pushed=false,deployment=false. Пуш делает пользователь.
R305/V89 — последний документированный runtime, rollback pointers сохранены;
активация R306–R308 требует нового backend image panel-web/ops-worker.

Остаются общая ручная очистка scoped state/events, обнаружение orphan материалов,
media/LLM context, scoped доставка/auto-reply policy и runtime/fleet/S6 приёмка.
Старые данные тестовые; S2B/backfill не возвращать в обязательный план.
