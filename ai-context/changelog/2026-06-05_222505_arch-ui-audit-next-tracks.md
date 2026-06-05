# 2026-06-05 22:25:05 - arch ui audit next tracks

## Промты пользователя

- `погнали дальше`

## Что изменено

- в `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` после checkpoint
  `2026-06-05` добавлена детализация следующего прохода по трём отдельным
  трекам:
  `Track A. Settings Page Shell Decomposition`,
  `Track B. Dialogs Runtime Module Split` и
  `Track C. Secondary Transport/Runtime Bounded Split`;
- в roadmap зафиксированы конкретные audit-наблюдения по
  `settings/index.html` (`~24659` строк, inline handlers, inline style/script),
  `dialogs.js` (`~10426` строк) и secondary backend boundaries вроде
  `ChannelApiController`, `AnalyticsController` и
  `DialogWorkspaceTelemetryAnalyticsService`;
- в `ai-context/tasks/task-details/01-024.md` добавлена синхронизированная
  детализация этих next-step треков и рекомендуемый порядок следующего
  реального прохода.

## Проверка

- `git diff --stat -- docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md ai-context/tasks/task-details/01-024.md`
