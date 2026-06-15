# 2026-06-15 16:25:21 - settings page shell declarative callbacks

## Промпты пользователя

- `продолжай. но не трогай publicform - они не интересны на текущем этапе`

## Что изменено

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` добавлен общий declarative runtime для `data-settings-click-callback`, `data-settings-change-callback` и `data-settings-input-callback`, включая разбор аргументов вроде `$element`, `$value` и `$checked`;
- `spring-panel/src/main/resources/templates/settings/index.html` переведён с inline `onclick/onchange/oninput` на shell-делегаты для общих save-кнопок, блока locations sync, client statuses и шаблонов автозакрытия;
- динамически рендерящиеся editor-блоки для шаблонов диалогов и iikoServer sources тоже переведены на declarative callbacks, поэтому giant inline script больше не вшивает inline event handlers в создаваемую HTML-разметку;
- блоки `publicform` и `questions_cfg` не менялись: пакет ограничен shell-слоем и вне-current-step обработчиками settings runtime.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `rg -n "onclick=|onchange=|oninput=" spring-panel/src/main/resources/templates/settings/index.html`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`
