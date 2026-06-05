# 2026-06-05 11:20:10 — dashboard tooltip layer fix

## Промты пользователя

- `hover/focus на кнопке i на блоках "Команда" и "Активность", отображается под данными самого блока. на остальных блоках такой проблемы нет`

## Что изменено

- в dashboard поднят `z-index` у `panel-head` и `chart-panel-head`, чтобы заголовок с tooltip жил выше слоя внутренних data-блоков;
- у самой кнопки `i` дополнительно поднят `z-index`, чтобы hover/focus-подсказка не перекрывалась heatmap и карточками сотрудников в широких блоках `Команда` и `Активность`;
- изменение сделано точечно в CSS без перестройки HTML-структуры dashboard.

## Проверка

- `./mvnw.cmd -q -DskipTests compile`
- `git diff --check -- ai-context/tasks/task-list.md ai-context/tasks/task-details/01-126.md spring-panel/src/main/resources/templates/dashboard/index.html`
