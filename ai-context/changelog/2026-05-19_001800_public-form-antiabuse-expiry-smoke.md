# 2026-05-19 00:18:00 - Public form anti-abuse and expiry smoke

## Промт пользователя

- `давай дальше более широким пакетом`

## Что сделано

- Расширен `PublicFormFlowSmokeIntegrationTest` на live HTTP runtime
  contract для `public-form` anti-abuse и expiry веток.
- Добавлены integration-сценарии на idempotency reuse с тем же
  `requestId`, structured `IDEMPOTENCY_CONFLICT` при смене payload,
  burst `RATE_LIMITED` rejection и `public_form_session_ttl_hours`
  expiry после искусственного aging session row.
- Актуализированы `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` под более широкий
  runtime-hardening пакет вокруг `public-form` API/session contract.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormFlowSmokeIntegrationTest,PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Что дальше

- Следующим логичным пакетом можно уже добирать `public-form` polling/history
  lifecycle и visibility/runtime interplay после reply/update сценариев в
  живом приложении, а не только transport/unit boundary.
