# 2026-04-22 21:53:00 — public shell ui-head and template contract pass

## Что сделано

- добавлен explicit `data-ui-page="public"` в `auth/login.html`,
  `error/403.html`, `error/404.html` и `error/500.html`;
- `error/403.html` и `error/500.html` приведены к общему
  `fragments/ui-head` bootstrap и получили базовые `style.css/app.css`,
  чтобы public shell не выпадал из общего UI runtime foundation;
- добавлен explicit `data-ui-page="passports"` в `passports/detail.html`;
- добавлен lightweight `PublicShellTemplateContractTest`, который проверяет
  public shell templates на `fragments/ui-head` и `data-ui-page="public"`.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=PublicShellTemplateContractTest,DialogsControllerWebMvcTest,ManagementControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Эффект

- `Phase 6` safety net расширен с authenticated/detail страниц и на
  public-shell runtime contract;
- `403/500` больше не живут как частично отдельный legacy shell и лучше
  соответствуют общему UI bootstrap-потоку проекта.
