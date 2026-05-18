# 2026-05-18 10:01:31 - Public form anti-abuse split

## Промт пользователя

- `проверь файлы аудита C:\Users\sushi\Git_H\tg_ref_b24_sup\docs\ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- `и`
- `C:\Users\sushi\Git_H\tg_ref_b24_sup\docs\ARCHITECTURE_AUDIT_2026-04-08.md`
- `продолжи по ним работу`
- `давай дальше`

## Что сделано

- Из `PublicFormService` вынесен anti-abuse/request identity bounded slice в
  новый `PublicFormAntiAbuseService`.
- Новый сервис забрал requester fingerprint key, requestId normalization,
  payload hash, idempotency cache и rate-limit policy.
- `PublicFormService` сжат примерно с `1020` до `889` строк и перестал
  одновременно держать submit orchestration и anti-abuse state.
- Добавлен `PublicFormAntiAbuseServiceTest`.
- Актуализированы `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и
  `docs/ARCHITECTURE_AUDIT_2026-04-08.md`: следующий practical focus по
  `PublicFormService` смещён на submit/captcha/validation и config-parser tail.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormAntiAbuseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest,PublicFormLocationIntegrationTest" test`

## Что дальше

- Следующим bounded пакетом продолжать `PublicFormService` по
  submit/captcha/validation orchestration.
- Отдельным соседним проходом можно выносить public-form config parser, если
  `parseSettings/parseQuestions` снова начнут расти как mixed helper layer.
