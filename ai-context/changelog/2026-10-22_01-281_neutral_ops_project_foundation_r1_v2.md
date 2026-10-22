# 2026-10-22 — 01-281 R1: общий Neutral Ops foundation для UI Iguana

## Промт пользователя

> продолжаем работу по проекту. в целом правил ведения диалога в сессиях в приложенном файле. сейчас задача - расширить таск 01-281 на весь проект

## Основание

- На исходном commit `615474977fe0e49c4b1ef1ebf920012545a88082` задача `01-281` имела статус `🟣` и была ограничена диалогами и dashboard.
- При read-only инвентаризации подтверждены 73 HTML-шаблона, из них 46 самостоятельных страниц: 45 с `app.css` и `data-ui-page`, один автономный workforce check-in.
- Это первый small-scope шаг **расширенной** задачи, не завершение полного редизайна.

## Изменённые файлы

- `spring-panel/src/main/resources/scss/app/_neutral-ops.scss` — общий селектор и cross-page neutral foundation; первые два screen-specific слоя сохранены.
- `spring-panel/src/main/resources/static/css/app.css` — регенерирован из SCSS в изолированном Maven worktree.
- `spring-panel/src/test/java/com/example/panel/controller/ClientsControllerWebMvcTest.java` -- test-only missing `@MockBean MultibusinessFeatureFlags` in an imported `NavigationService` MVC slice.
- `ai-context/tasks/task-list.md` — переименование охвата и перевод `01-281` из `🟣` в `🟡`, поскольку задача снова в работе.
- `ai-context/tasks/task-details/01-281.md` — расширенный проектный scope, доказанный инвентарь, R1–R7, QA и exit criteria.
- `ai-context/changelog/2026-10-22_01-281_neutral_ops_project_foundation_r1_v2.md` — эта новая append-only запись.

## Ограничения

- Изменены SCSS, производный CSS, тестовый MVC-мок и метаданные задачи; production Java/JavaScript/HTML/DB/API и другие исходники не менялись.
- `workforce/check-in.html` и точечные layout-проблемы остальных экранов остаются следующими этапами.
- Runner требует неизменный Git baseline и реальную локальную `--validate`/`--apply` проверку; эта запись **не** утверждает, что production rollout или ручная приёмка выполнены.

## R1 validation failure and R1-v2 reason

- Previous R1 `--self-test=GREEN`, Sass resource generation and Java compile passed in a detached worktree.
- Previous R1 `--validate=RED`: 43 MVC tests ran; one test-slice ApplicationContext error. `ClientsControllerWebMvcTest` imports `NavigationService` but did not provide `MultibusinessFeatureFlags`. `DialogsControllerWebMvcTest` already provides this mock.
- Previous R1 `SOURCE_APPLIED_FINAL=false`; no main checkout files, production runtime or database were changed.
- R1-v2 repairs only the missing MVC test mock; it does not skip or relax any assertions or remove tests. Full R1-v2 validation and local apply must still be confirmed by the operator output.

## Проверки шага

- `node --check`, `--self-test` проверяют runner/transform.
- `--validate` обязан в temporary detached worktree выполнить Sass generation, компиляцию, targeted MVC tests и `git diff --check`.
- `--apply` допустим только после повторной проверки guards и sandbox GREEN. Полученные на машине пользователя логи составляют фактический evidence; не подменять их утверждением без исполнения.
