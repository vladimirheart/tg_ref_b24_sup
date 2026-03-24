# Аудит страницы настроек: покрытие функций

Дата: 2026-03-24

## Что проверяли

- Контроллер сохранения настроек (`SettingsBridgeController`) и перечень ключей, которые backend принимает в payload `/settings`.
- Шаблон страницы настроек (`templates/settings/index.html`) и наличие/отсутствие соответствующих ключей в UI.

## Вывод

Основная часть настроек покрыта интерфейсом. При автоматической сверке найдено **13 ключей**, которые обрабатываются backend, но не встречаются в шаблоне страницы настроек.

### 1) Ключи governance для macro catalog (не представлены в UI)

- `dialog_macro_governance_require_owner`
- `dialog_macro_governance_require_namespace`
- `dialog_macro_governance_require_review`
- `dialog_macro_governance_review_ttl_hours`
- `dialog_macro_governance_deprecation_requires_reason`
- `dialog_macro_governance_unused_days`

Комментарий: ключи принимаются контроллером и мапятся в `dialog_config`, но полей на странице для управления ими не найдено.

### 2) Ключи governance для workspace rollout (не представлены в UI)

- `dialog_workspace_rollout_governance_review_decision_required`
- `dialog_workspace_rollout_governance_incident_followup_required`
- `dialog_workspace_rollout_governance_review_decision_action`
- `dialog_workspace_rollout_governance_review_incident_followup`

Комментарий: backend поддерживает расширенный governance-контур, но в текущем шаблоне страницы прямых элементов для этих полей не обнаружено.

### 3) Ключи каталога IT-оборудования (API-алиасы, не обязательный разрыв)

- `accessories`
- `additional_equipment`
- `serial_number`

Комментарий: это ключи API для CRUD оборудования (`/api/settings/it-equipment`), где `additional_equipment` выступает совместимым алиасом для `accessories`. Отсутствие буквального упоминания этих ключей в шаблоне не обязательно означает функциональный пробел UI, но требует ручной проверки формы раздела IT-оборудования в рантайме.

## Рекомендации

1. Для governance-блоков добавить явные элементы управления в разделах Dialog/Macro, если эти параметры предполагается изменять из UI.
2. Если параметры должны оставаться «скрытыми»/операционными, зафиксировать это в `docs/configuration.md` как intentional backend-only.
3. Для IT-оборудования оставить алиас `additional_equipment` только на уровне API-совместимости и документировать canonical-поле `accessories`.
