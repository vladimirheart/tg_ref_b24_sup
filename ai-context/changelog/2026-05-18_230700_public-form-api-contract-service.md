# 2026-05-18 23:07:00 - Public form API contract service

## Промт пользователя

- `хорошо, давай дальше`

## Что сделано

- Добавлен `PublicFormApiContractService`, который забрал из
  `PublicFormApiController` disabled-status fallback, requester-context
  resolution, error-code mapping и token masking.
- `PublicFormApiController` сжат примерно до `156` строк и стал тоньше ещё
  на один bounded helper layer поверх уже вынесенного
  `PublicFormApiResponseService`.
- Добавлен `PublicFormApiContractServiceTest` и расширен
  `PublicFormApiControllerWebMvcTest`: отдельно зафиксирован malformed-body
  transport contract с `MALFORMED_BODY` и structured `path/timestamp`.
- Актуализированы `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` под следующий шаг
  public-form API consistency hardening.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Что дальше

- Следующим пакетом логично идти уже в integration/e2e/runtime contract
  вокруг `public-form`, а не продолжать локальные controller-helper split-ы.
