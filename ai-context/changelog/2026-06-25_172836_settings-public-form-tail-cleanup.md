# 2026-06-25 17:28:36 - settings public form tail cleanup

## Промпты пользователя

- `ты тут писал: "Полный dialog-sla блок сейчас слишком жирный и заденет старые неинтересные хвосты вроде publicform" publicform ведь выпилен из проекта. проверь и удали оставшиеся его хвосты`

## Что изменено

- в `spring-panel/src/main/resources/templates/settings/index.html` удалён
  оставшийся JS-слой `public_form` для settings-страницы: default config keys,
  `getDialogSlaInputs()` bindings, hydration в `initDialogSlaControls()`,
  validation/normalization в `collectDialogSlaConfig()` и serialization
  `dialog_public_form_*` в payload сохранения;
- карточка `01-129` обновлена: зафиксировано, что `public_form` больше не
  остаётся частью `settings` runtime split и его JS-хвосты в шаблоне уже
  вычищены;
- дополнительной проверкой подтверждено, что в `settings/index.html` больше не
  осталось совпадений `publicForm|public_form`; при этом за пределами settings
  в backend/test слое ещё живёт отдельный `PublicForm*` subsystem, который
  требует отдельного removal-пакета, а не этого cleanup-шага.

## Проверка

- `rg -n "publicForm|public_form" spring-panel/src/main/resources/templates/settings/index.html`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-25_172836_settings-public-form-tail-cleanup.md`
