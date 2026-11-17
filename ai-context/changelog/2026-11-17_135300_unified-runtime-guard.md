# Допуск unified-опросника в runtime бота

## Промт пользователя

> тогда сразу убери и:
>
> Базовый шаблон вопросов — Суши
>
> Базовый шаблон вопросов — Блины

## Выполнено

- Исправлен runtime guard `PanelRuntimeConfigClient`: он принимает проверенную
  конфигурацию единого опросника с источником `QUESTIONNAIRE_FIELD`, наряду с
  general-каналом.
- Добавлен тест primary runtime endpoint для unified-опросника.

## Проверка

- `PanelRuntimeConfigClientTest` и `QuestionnaireScopeTest` проходят успешно.
