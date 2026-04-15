# 2026-04-15 14:59:09 — dialog list controller and read service split

## Что сделано

- добавлен отдельный `DialogListController`, который теперь владеет route
  `/api/dialogs`;
- добавлен `DialogListReadService`, который собирает payload списка диалогов:
  `summary`, `dialogs`, `sla_orchestration`, `success`;
- SLA orchestration списка вынесен из `DialogApiController` в новый read-only
  сервис вместе с нужной read-model логикой и чтением `dialog_config`;
- из `DialogApiController` удалён list endpoint и соответствующая list-specific
  orchestration-логика.

## Зачем

Это следующий bounded-context шаг после triage preferences:

- `DialogApiController` перестаёт быть единственной точкой для всех сценариев
  диалогового домена;
- read-only list slice получает собственный controller/service слой;
- дальнейшее вынесение workspace/history/SLA сценариев становится более
  механическим и безопасным.

## Проверка

- `spring-panel/mvnw.cmd -q -DskipTests compile`
