# 2026-08-23 22:30 - settings save scope + platform startup logging

## Промпт пользователя

- `теперь всё залилось. репо апнул. проверяй и давай дальше`

## Что проверено

- HEAD после UX/logging v28 проверен как `de2f9dcd650800057d49de9b1c798cd5a330655c`;
- profile save feedback, compact locations tree, incident quick-status и directional logging присутствуют в репозитории;
- `SettingsUpdateService`, `SettingsTopLevelUpdateService`, dialog и locations update services подтверждают partial-update semantics: отсутствующие top-level keys не перезаписываются.

## Что изменено

- оставшиеся общие `data-save-settings` получили явные scope:
  - `dialogs`;
  - `locations`;
  - `auto-close`;
- `settings-save-runtime.js` валидирует и отправляет только текущий settings-domain вместо обязательной сборки всех настроек;
- вызов без scope оставляет прежнее full-payload поведение как compatibility fallback;
- save-кнопка на время запроса блокируется и показывает `Сохраняем…`;
- `startup.log` дополнительно получает logger families RabbitMQ/AMQP, Redis/Lettuce и MinIO, при этом `spring-panel.log` остаётся полным canonical log.

## Почему это безопасно

Backend `/settings` уже применяет payload частично: существующий settings map загружается перед update, а отдельные update services изменяют только присутствующие keys. Locations не обновляются без `locations`, dialog config не трогается без dialog keys.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-save-runtime.js`;
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`;
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`;
- `git diff --check`;
- `spring-panel/.mvnw.cmd -q -DskipTests test-compile` на Windows после применения patch.