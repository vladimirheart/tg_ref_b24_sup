# 01-241: template card controls polish

Дата: 2026-09-21

## Контекст

После production UI-проверки compact template workspace остались четыре локальных дефекта: иконка дублирования не поддерживается vendored Bootstrap Icons 1.10.x, action controls растягиваются по высоте из-за button-group поведения, default switch расположен отдельно от action stack, а нижняя кнопка закрытия дублирует крестик в заголовке модалки.

## Изменения

- `bi-copy` заменён на поддерживаемый `bi-files`;
- Bootstrap `btn-group` убран только у template action controls, data hooks сохранены;
- icon-only actions жёстко ограничены квадратом 1.65rem по width/height/min/max;
- default switch перенесён над action icons в правую control-column;
- тот же control-stack применён к auto-close templates;
- нижний footer с кнопкой «Закрыть» удалён из channels modal, header close остаётся;
- backend/lifecycle/DB/data semantics не меняются.

## Проверка

Validate/apply guards, `node --check` двух runtime JS, `git diff --check`, targeted SettingsChannelsRuntimeUiSourceContractTest + /settings render smoke, cleanup только известных generated CSS side-effects, exact stage/commit/push и selective panel-web rollout.
