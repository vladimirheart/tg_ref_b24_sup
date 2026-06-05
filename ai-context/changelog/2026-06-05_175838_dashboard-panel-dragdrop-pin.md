# 2026-06-05 17:58:38 - dashboard panel dragdrop pin

## Промпты пользователя

- `сделай такие-же возможности с drag&drop и pin как в примере:`
- `продолжи`

## Что изменено

- в `dashboard/index.html` аналитическим панелям добавлены стабильные `data-panel-id`, чтобы хранить и восстанавливать пользовательский layout независимо от перерисовки данных;
- для заголовков панелей добавлены компактные контролы `pin` и drag-handle, а закреплённые панели визуально выделяются и всегда поднимаются в верхнюю часть сетки;
- на фронте реализованы `drag&drop` с placeholder, ограничением перестановки внутри pinned/unpinned-групп и сохранением порядка в `localStorage`;
- после pin/drop-перестановок dashboard автоматически перераскладывает DOM панелей и заново ресайзит графики через существующий `rerenderDashboardCharts()`.

## Проверка

- `./mvnw.cmd -q -DskipTests compile`
- `git diff --check -- spring-panel/src/main/resources/templates/dashboard/index.html`
