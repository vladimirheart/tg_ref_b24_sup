# 2026-06-25 13:15:16 - dialogs open pagination avatar hardening

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-flow-runtime.js`,
  `spring-panel/src/main/resources/templates/fragments/navbar.html`
- Промты пользователя:
```text
давай следующий шаг
```
- Что сделано:
  dialog-open flow переведён на единый `openDialogSurface`, который при
  включённом workspace runtime открывает `openDialogWithWorkspaceFallback`, а
  не остаётся на legacy modal-only ветке.
- Что сделано:
  page-level init в `dialogs.js` получил `runDialogsInitStep(...)`, чтобы
  сбой одного из новых `bind*` шагов не обрывал оставшиеся бинды страницы,
  включая pagination и соседнюю orchestration-логику.
- Что сделано:
  pagination handlers усилены явным numeric parsing, а sidebar avatar получил
  server-side `is-loaded` fallback, чтобы изображение авторизованного
  пользователя не зависело только от post-load JS-класса.
