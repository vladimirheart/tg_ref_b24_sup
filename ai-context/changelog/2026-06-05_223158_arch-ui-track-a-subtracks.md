# 2026-06-05 22:31:58 - arch ui track a subtracks

## Промты пользователя

- `давай дальше`

## Что изменено

- в `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` углублён `Track A` по
  `settings/index.html`: зафиксировано, что основной hotspot там уже не только
  giant template markup, но и giant inline runtime script;
- в roadmap добавлены внутренние bounded sub-tracks для `Track A`:
  `parameters/legal entities/partner contacts/IT catalog`,
  `client statuses/business styles`,
  `locations tree/sync/wizard`,
  `dialog templates/auto-close/SLA/workspace governance`,
  `channels/network routes/runtime status/editor`,
  `reporting/manager bindings/modal shell`;
- в `ai-context/tasks/task-details/01-024.md` добавлена синхронизированная
  детализация этих sub-tracks и явное наблюдение, что fragment split по
  settings нужно делать только после runtime split, а не наоборот.

## Проверка

- `git diff --stat -- docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md ai-context/tasks/task-details/01-024.md`
