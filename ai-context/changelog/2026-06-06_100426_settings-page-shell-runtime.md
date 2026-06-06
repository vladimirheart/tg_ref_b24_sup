# 2026-06-06 10:04:26 - settings page shell runtime

## Промпты пользователя

- `давай тепрь по задаче 01-128`

## Что изменено

- создан новый runtime `spring-panel/src/main/resources/static/js/settings-page-shell.js`, куда вынесены:
  theme form sync, keyboard support для settings tiles, modal body lock, z-index elevation,
  sheet expand/collapse controls и page-entry открытие `channels/users` по URL-параметру;
- в `spring-panel/src/main/resources/templates/settings/index.html` добавлен page hook
  `data-settings-page-shell`, подключён новый JS entrypoint в конце страницы и удалены
  соответствующие shell-куски из giant inline script;
- для sheet-модалок `authUserDetailsModal` и `partnerContactEditorModal` добавлен
  `data-settings-sheet`, чтобы shell/runtime и соседние JS-модули работали через явный hook,
  а не через неформальную привязку к CSS-классам;
- задача `01-128` переведена в статус `🟡` в `ai-context/tasks/task-list.md`, потому что по ней
  начата реальная реализация, а не только audit/task decomposition.

## Проверка

- `git diff --check -- ai-context/tasks/task-list.md spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-06_100426_settings-page-shell-runtime.md`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js` не выполнен: `node` отсутствует в текущем окружении.
