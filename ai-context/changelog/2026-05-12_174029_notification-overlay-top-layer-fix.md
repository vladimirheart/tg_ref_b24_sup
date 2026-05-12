# 2026-05-12 17:40:29 — Notification overlay top-layer fix

## Затронутые файлы

- `spring-panel/src/main/resources/static/js/common.js`

## Пользовательский промт

1. `окно с предупреждением скрывается где-то за самой страницей и появляется только если страницу обновить.`
2. Значимое уточнение: проблема проявляется при предупреждениях на странице настроек ботов и маршрутов.

## Что сделано

- Переведён `showNotification/showPopup` на отдельный overlay-root, который монтируется прямо в `document.documentElement`, а не в произвольный DOM-контекст страницы.
- Добавлены принудительные top-layer inline styles через `style.setProperty(..., 'important')` для:
  - `position: fixed`
  - `inset: 0`
  - `z-index: 2147483647`
  - `visibility: visible`
  - `opacity: 1`
  - `isolation: isolate`
- Добавлена защита от reuse overlay, если DOM-узел с тем же id оказался не в корневом слое.
- Переключение с `innerHTML = ''` на `replaceChildren()` убрало лишнюю зависимость от старого содержимого узла.

## Проверка

- Локальная проверка кода правки в `common.js`.
- Автотестов на popup-layer в проекте нет.
