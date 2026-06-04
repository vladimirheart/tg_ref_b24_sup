# 2026-06-04 16:53:17 — dashboard hourly load heatmap

## Промты пользователя

- `добавь ещё одну метрику: нагрузку по часам, т.е. количество обращений в час. пример в скрине`

## Что изменено

- в `DashboardAnalyticsService` добавлен расчёт `activity_stats`: средняя нагрузка в активный час, пиковый слот и матрица `день недели x час`;
- в KPI-блоке dashboard карточка `Ритм обзора` заменена на реальную метрику `Нагрузка в час`;
- в аналитическую сетку dashboard добавлен широкий heatmap-блок `Нагрузка по дням и часам` с hover-фокусом по ячейкам;
- heatmap и почасовая метрика подключены к текущему `/api/dashboard/data` и обновляются вместе с фильтрами;
- в `ai-context/tasks` добавлена и завершена задача `01-120`.

## Проверка

- `./mvnw.cmd -q -DskipTests compile`
- `git diff --check -- spring-panel/src/main/java/com/example/panel/service/DashboardAnalyticsService.java spring-panel/src/main/resources/templates/dashboard/index.html ai-context/tasks/task-list.md ai-context/tasks/task-details/01-120.md`
