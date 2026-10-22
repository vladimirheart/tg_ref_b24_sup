# 2026-10-22 18:17:22 — 01-281: проектный Neutral Ops и локальное обновление панели

## Промт пользователя

> задача 01-281 изменена - нужны изменения на проект. начало уже положено, заодно проверь результат начала. выполни её.
> да, перезапускать или пересобирать контейнеры можно

## Фактический scope

- Проверены R1/R2 на чистом baseline `f96ba725dc3e2fa4b0b2671b927bf2a51a50c474`; `HEAD` и `origin/main` совпадали.
- В detached worktree завершён визуальный слой R3–R6 и проверен R7. После GREEN 74 source-файла перенесены в рабочий checkout и сверены с проверенной копией.
- Созданы `scss/style/_neutral-ops.scss` и пять `scss/app/_neutral-ops-{common,conversations,analytics,work,public}.scss`; обновлены imports, sidebar/settings overlays и четыре производных CSS.
- Во всех 46 самостоятельных HTML-страницах обновлены версии собственных CSS; четыре scoped-страницы получили недостающий theme stylesheet, workforce — общий ui-head/app слой. Вход получил связи label/input.
- Исправлены дефекты начала: `--surface-soft`, контраст KPI после удаления тёмного фона, порядок disclosure imports, плитки настроек, наложение mobile toggle, скрываемые общими стилями invalid/semantic состояния и touch-targets.
- Восемь существующих тестовых файлов изменены только для недостающего navigation mock и актуальных cache-version assertions. Добавлены три test-only visual fixture/export classes, test spring.factories и `tools/neutral-ops-qa.cjs`.
- Добавлен `docs/ui/neutral-ops-qa.md`; задача переведена в `🟣` для ручной приёмки владельцем. Старые changelog не переписывались.

## Проверки и ограничения

- Sass generation, compile/package, 145 MVC/UI-contract tests, `node --check`, source equality guard и `git diff --check` прошли.
- Инвентарь: 73 шаблона, 46 страниц, 27 фрагментов. Получены 460 full-matrix снимков и 72 palette/system/touch проверки; 63 модальных проверки с клавиатурным фокусом. Просмотрены обзорные листы обеих тем.
- Проверялись настоящие Thymeleaf templates на тестовых моделях. API в browser fixture замоканы/недоступны по дизайну; бизнес-действия в рабочих данных не выполнялись. Подробности и артефакты — в QA-документе.
- Production Java, JavaScript, SQL, permission contracts, API и данные не изменялись. Пользовательская приёмка не объявляется выполненной.

## Runtime checkpoint по явному разрешению

- Только `panel-web` пересоздан на `iguana-panel:neutral-ops-r7-20261022-v2`, digest `sha256:2d4d06151468fbe62cb5fcb310f1e9f681cf4fc0fc4bc7399edb0d775df35ca7`, статус `healthy`.
- `iguana-panel:local` теперь указывает на новый образ. JAR собран локально поверх сохранённой runtime-основы, без запросов Docker Hub.
- Rollback: `iguana-panel:rollback-neutral-ops-20261022`, digest `sha256:97ccb6fc7a34ae77af2eb7f264c49d8665393571060ad0fa46664ea528a9ce62`.
- Worker/bot-runner и инфраструктура не перезапускались; Flyway version 81 до и после. Миграции, backfill и внешние бизнес-системы не затрагивались.
- Проверены настоящий `/login` light/dark и совпадение HTTP CSS bytes с исходниками. Git staging, commit и push не выполнялись.
