# 01-281: компактные кнопки, графики и переписка после замечаний владельца

Время: 22.10.2026, 21:08 (Europe/Moscow).

## Промт пользователя:

> кнопка раскрытия несуразна (указал красной стрелкой). слишком высокая. её смело можно делать ниже по размеру
>
> кнопка действий на странице диалогов тоже попадает под описание выше
>
> графики на странице отчётов не такие как на превью
>
> окно диалога тое такое себе

Приложены три снимка: дерево локаций, действия списка диалогов, окно переписки.
Для локального rollout сохраняется ранее данное разрешение пользователя:
«да, перезапускать или пересобирать контейнеры можно».

## Изменения

- Новый scoped SCSS `app/_neutral-ops-feedback.scss`, подключён перед последним
  disclosure-модулем; штатно пересобран `static/css/app.css`.
- Иконки раскрытия и меню действий 24×24 px для мыши, 44×44 px на touch.
- Плоские боковые панели и компактная шапка диалога, исходящие справа,
  системные события по центру, контрастный reply preview, нижняя строка
  инструментов composer и перенос полной ширины на узком экране.
- Новый `dashboard-activity-trend.js`: линейная визуализация существующей
  activity matrix, без API/backend изменений и без демонстрационных данных
  в production. Суммарные обращения и обращения в рабочее время по дням недели.
- Dashboard template подключает модуль/виджет и вызывает обновление при загрузке
  и перерисовке; старые диаграммы получили плоские заливки, тонкую геометрию и
  компактные tooltips. CSS version трёх исправляемых страниц поднята до R8.
- Business template: два последних native details получили canonical class,
  устранив baseline-регрессию; бизнес-контракты R168/R169 сохранены.
- Добавлены четыре unit tests графика и filled-state browser QA; обновлено только
  точное CSS-version assertion в тесте ширин колонок. Обновлены task detail и отчёт.

Точный список 14 файлов и команды фиксации: `docs/ui/neutral-ops-feedback.md`.

## Проверка и operational checkpoint

- Baseline/head/origin_main: `24e95ff6f8402dd63fe30e3cc0991fc1cf7e5ac3`;
  исходный checkout чистый. Detached worktree проверен до применения, все
  11 source/test/tool files после применения совпали с проверенной копией.
- `sandbox_green=true`, `source_applied=true`; Sass, package, 145 MVC/UI-contract
  tests и 9 JS tests прошли, failures/errors/skipped = 0. `git diff --check` GREEN.
- 10 filled-state комбинаций: 2 темы × 5 ширин (320–1920 px), 30 снимков и
  отдельные empty-state проверки графика. Дополнительно 72 palette/theme/touch
  сценария и 63 модальных проверки фокуса. Это fixtures, не authenticated приёмка
  наполненной рабочей системы; реальные записи и внешние запросы запрещены.
- Начальный RED разобран: устаревшее CSS-version assertion, два новых details
  без canonical class и LF/CRLF старого строкового контракта. Тесты не отключались
  и не ослаблялись. Java нормализовалась только в sandbox, production Java не менялась.
- `deployment=true`: локальный `panel-web` переведён на
  `iguana-panel:neutral-ops-r8-20261022`, image ID
  `sha256:3936d8495f132a9a73b2a75cc0ccd1e1cdeb0128a7dfedb8fbb25c26a5b445a0`, healthy.
- UI-only JAR: изменены ровно пять entries; SHA остальных, включая все Java-классы
  и библиотеки, сохранены. Новые backend commit мультибизнеса не развёртывались.
  JAR SHA-256 `4cd6e2f939cbe5fabebcfd82a88bbde1d812fd8f3a9507665394c6c7787a504a`.
- Environment и bind mounts сверены без вывода секретов. HTTP CSS/JS SHA совпадают
  с source, `/login` 200. Alias local обновлён.
- Сохранён непосредственный rollback `iguana-panel:rollback-neutral-ops-r8-20261022`
  (`sha256:2d4d06151468fbe62cb5fcb310f1e9f681cf4fc0fc4bc7399edb0d775df35ca7`),
  прежний rollback также сохранён. Откат не выполнялся.
- Workers не перезапускались; Flyway 81. Migration/backfill/внешние бизнес-записи
  не выполнялись. `staged=false`, `committed=false`, `pushed=false`.
- Task остаётся `🟣` для повторной ручной приёмки; `🟢` не выставлялся.
