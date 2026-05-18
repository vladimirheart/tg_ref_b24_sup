# 2026-05-18 17:45:20 - Public form validation error-code hardening

## Промт пользователя

- `хорошо, возьми следующую небольшую задачу`

## Что сделано

- В `RestExceptionHandler` уточнён mapping для bean-validation required
  ошибок: `NotBlank/NotEmpty/NotNull` теперь возвращают
  `VALIDATION_REQUIRED` вместо generic `VALIDATION_ERROR`.
- Добавлен web-test для `PublicFormApiController`, который фиксирует
  contract на пустом `message` body и подтверждает, что request
  отсекается до вызова `PublicFormService`.
- Актуализированы `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` под этот маленький
  post-split hardening шаг.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormApiControllerWebMvcTest" test`

## Что дальше

- Следующим таким же маленьким шагом можно добрать ещё один
  `public-form` boundary contract вокруг malformed-body или session/runtime
  fallback, не открывая новый refactor-проход.
