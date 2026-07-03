# 2026-07-03 11:37:07 — Dialogs modal only my dialogs on right fix

## Связанные задачи

- `01-139` — Перенести список «Мои диалоги» вправо и показывать новые неназначенные обращения в открытом диалоге

## Пользовательский промпт

> ты не совсем корректно сделал перенос.  
> слева должно было остаться всё, кроме диалогов - только диалоги должны быть перенесены вправую часть

## Затронутые файлы

- `spring-panel/src/main/resources/templates/dialogs/index.html`
- `spring-panel/src/main/resources/scss/app/_dialogs.scss`
- `spring-panel/src/main/resources/static/css/app.css`
- `ai-context/changelog/2026-07-03_113707_dialogs-modal-only-my-dialogs-on-right-fix.md`

## Что сделано

- Исправлена структура legacy-модалки диалога: левая колонка со сводкой, проблемой, метриками, AI и шаблонами возвращена на исходную сторону.
- Блок `Мои диалоги` вынесен в отдельную правую колонку модалки, чтобы вправо переносился только он, а не весь sidebar целиком.
- Адаптированы responsive-стили для новой правой колонки и пересобран итоговый `app.css`.

## Проверки

- `spring-panel\\mvnw.cmd -q generate-resources`
  - результат: SCSS успешно пересобран.
- `git diff --check -- spring-panel/src/main/resources/templates/dialogs/index.html spring-panel/src/main/resources/scss/app/_dialogs.scss spring-panel/src/main/resources/static/css/app.css`
  - результат: ошибок diff-formatting нет; Git выводит только стандартные предупреждения LF/CRLF для Windows-рабочей копии.
