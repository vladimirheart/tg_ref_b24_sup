# 2026-04-15 13:08:39

## Заголовок

Второй проход по теме 01-023: обновить chart-layer дашборда под Catppuccin, Amber Minimal и штатную тему

## Что изменено

- обновлён `task-flow` по задаче `01-023`: расширен scope на theme-aware графики дашборда;
- в `dashboard/index.html` добавлен chart profile слой для `neo`, `catppuccin` и `amber-minimal`;
- графики переведены на palette-aware fills, type-specific tooltip/legend styling и автоматическую перерисовку при `theme:change`;
- круговые диаграммы `business` и `network` обновлены до `doughnut`;
- `chartByChannel` и `cityChart` получили вертикальный bar-режим, остальные bar-графики сохранили горизонтальную подачу;
- mixed chart нагрузки сотрудников обновлён градиентными столбцами и theme-aware line overlay.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-023.md`
- `spring-panel/src/main/resources/templates/dashboard/index.html`

## Проверка

- `spring-panel/.\\mvnw.cmd -q -DskipTests compile`
