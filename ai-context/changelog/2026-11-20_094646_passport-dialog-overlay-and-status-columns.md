# Диалог поверх паспорта и общая колонка статусов

Время: 2026-11-20 09:46:46 Europe/Moscow. Задача: 01-299.
Baseline HEAD/origin/main: 46594c0a357c0a9b0586d402e297a1bef3284496.

Промт пользователя:

```text
1. что за шахматы в отображении статусов? отметил стрелками на скрине.
2. при клике на диалог, он открывается, но сразу переносит на страницу диалогов, то есть при закрытии окна диалога я остаюсь на странице диалога а не в том-же паспорте
```

Приложен скриншот с разными горизонтальными позициями closed/resolved.

- Passport workspace SCSS/compiled app.css: одинаковые колонки описания, статуса
  и открытия, перенос длинных подписей. Известные case statuses переведены.
- Passport preview runtime/template/page runtime: native modal и существующая
  карточка диалога в same-origin frame, сохранение URL/DOM/вкладки, закрытие через
  крестик, Escape, Back и «К паспорту»; обновление обращений и возврат фокуса.
- Dialog embedded runtime/SCSS и templates: интерфейс карточки без оболочки
  страницы, GENERAL layout, проверенные origin/source; обычное открытие прежнее.
- Dialog flow callback сохраняет category suppression guard. Details runtime
  сохраняет канал route, когда обращения нет на текущей странице списка.
- SecurityHeadersFilter: SAMEORIGIN и CSP self для двух видов GET карточек;
  остальные страницы/API/mutations сохраняют DENY, document preview прежний.
- Targeted Java/UI fixtures, browser QA и asset/source expectations обновлены.
  45 tests / 7 suites, 8 browser cases, Node/source audit/diff и package GREEN.
  775 unchanged Java sources/cache подтверждены, изменённый filter/tests
  скомпилированы javac17; 14 JAR resources и 1531 classes сверены.
- Перед GREEN исправлены GENERAL wrapper layout, channel route fallback и
  test harness (-parameters, focus вместо клика клиента). Assertions защиты
  не ослаблялись. Полная чистая Java сборка не выполнялась; working DB/Docker,
  migration/backfill, staging/commit/push/deploy не выполнялись.
- Task metadata обновлены. Факт source apply и точные файлы фиксирует guarded
  operator в ignored .tmp/01-299-applied.json; детали — task-details/01-299.md.
