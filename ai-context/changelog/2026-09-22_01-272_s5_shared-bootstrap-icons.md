# 2026-09-22 — task 01-272 — shared Bootstrap Icons follow-up

## Пользовательский запрос

- После S5 preview пользователь принял compact Knowledge Base layout, но отметил, что action-icons отображаются пустыми и shared info 'i' выглядит пустым кружком на страницах без локального Bootstrap Icons stylesheet.
- Исправление должно быть системным, а принятые изменения нужно запушить.

## Причина

- Shared runtime уже использует классы 'bi bi-*'.
- Bootstrap Icons 1.10.5 уже хранится локально в static/vendor, но stylesheet подключался только отдельными page templates и отсутствовал в shared ui-head.

## Исправление

- Vendored '/vendor/bootstrap-icons/1.10.5/bootstrap-icons.css' подключается через shared 'fragments/ui-head.html'.
- Source contract закрепляет global dependency для shared info affordances.
- Локальные page-specific icon links пока не удаляются: cleanup дублей не нужен для этого corrective slice.

## Safety

- DB mutation=false;
- queue mutation=false;
- source apply не меняет runtime;
- preview пересоздаёт только panel-web;
- stage/commit/push выполняются отдельной финальной фазой после GREEN source/runtime validation и fresh remote guard.
