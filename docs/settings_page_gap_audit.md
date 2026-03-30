# Аудит страницы настроек: status after closure

Дата актуализации: 2026-03-30

## Итог

Задачи из этого аудита закрыты.

На 2026-03-30 страница настроек покрывает:

- governance-поля для `workspace rollout`;
- governance-поля для `macro catalog`;
- UI и backend для `IT equipment`, включая `serial_number` и canonical-поле `accessories`;
- обратную совместимость по старому API-алиасу `additional_equipment`.

Если новые настройки отсутствуют, поведение системы не меняется: backend продолжает использовать существующие default-значения и already-stored config.

## Что было проверено

- `SettingsBridgeController` принимает и сохраняет нужные ключи в `dialog_config`.
- `templates/settings/index.html` содержит элементы управления для полей, которые раньше считались missing.
- `IT equipment` форма и inline-редактирование теперь поддерживают:
  - `serial_number`
  - `accessories`
  - старый alias `additional_equipment` на API-уровне

## Что закрыто

### 1. Macro governance

В UI и payload страницы настроек присутствуют и сохраняются:

- `dialog_macro_governance_require_owner`
- `dialog_macro_governance_require_namespace`
- `dialog_macro_governance_require_review`
- `dialog_macro_governance_review_ttl_hours`
- `dialog_macro_governance_deprecation_requires_reason`
- `dialog_macro_governance_unused_days`

Дополнительно сохранены и остальные поля macro quality loop, которые уже использовались audit/analytics-контуром.

### 2. Workspace rollout governance

В UI и payload страницы настроек присутствуют и сохраняются:

- `dialog_workspace_rollout_governance_review_decision_required`
- `dialog_workspace_rollout_governance_incident_followup_required`
- `dialog_workspace_rollout_governance_review_decision_action`
- `dialog_workspace_rollout_governance_review_incident_followup`

Это означает, что extended governance path больше не является backend-only.

### 3. IT equipment catalog

Реальный gap был только здесь: backend давно принимал поля каталога оборудования, но settings UI не давал редактировать часть из них.

Сейчас закрыто:

- в таблицу оборудования добавлены `Серийный номер` и `Комплектация`;
- в modal добавлены `serial_number` и `accessories`;
- inline-save и create-flow отправляют canonical-поле `accessories`;
- backend по-прежнему принимает `additional_equipment` как compatibility alias и сохраняет его в `accessories`.

Canonical naming:

- UI и новые клиенты: `accessories`
- compatibility alias: `additional_equipment`

## Подтверждение тестами

Документ опирается не только на ручную сверку, но и на тесты:

- template coverage:
  - governance fields in settings template
  - IT equipment fields for `serial_number` and `accessories`
- integration coverage:
  - `SettingsBridgeController` сохраняет macro/workspace governance keys
  - `IT equipment` update path сохраняет `serial_number`
  - `additional_equipment` корректно мапится в `accessories`

## Остаточный operational note

Отдельных открытых задач по `settings_page_gap_audit.md` не осталось.

Дальше это обычная поддержка:

- держать template coverage tests зелёными при новых изменениях settings page;
- не использовать `additional_equipment` как новое canonical-имя;
- при расширении `IT equipment` формы добавлять поля сразу и в template coverage, и в integration tests.
