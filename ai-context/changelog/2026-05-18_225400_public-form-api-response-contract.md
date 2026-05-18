# 2026-05-18 22:54:00 - Public form API response contract

## Промт пользователя

- `хорошо, давай дальше, но более широким пакетом`

## Что сделано

- Добавлен `PublicFormApiResponseService`, который забрал из
  `PublicFormApiController` success payload assembly для `config/create/session`
  и сборку structured error response.
- `PublicFormApiController` переведён на новый response-layer: manual error
  ветки теперь возвращают более явный contract с
  `success/error/errorCode/path/timestamp`, а controller стал тоньше и
  ровнее по API shape.
- Добавлен `PublicFormApiResponseServiceTest` и расширен
  `PublicFormApiControllerWebMvcTest`: зафиксированы `path/timestamp` для
  missing/disabled/session/internal/rate-limited веток.
- Актуализированы `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` под этот более широкий
  post-split API-consistency пакет.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Что дальше

- Следующим пакетом можно продолжить уже не giant split, а
  integration/e2e/runtime contract hardening вокруг `public-form` и соседних
  launcher/runtime boundaries.
