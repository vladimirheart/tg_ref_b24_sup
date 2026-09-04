# Locations tree progressive disclosure and theme-aware pills

## Пользовательский запрос

> отлично. правда ты пропустил цветовые гаммы под разные темы. сейчас вижу просто белые плашки.
> продолжим теперь по второй вкладке - Структура.
> 1. при открытии, бизнес свёрнут - это ок, но при раскрытии, всё его содержимое тоже раскрыто - не логично.
> 2. дерево не совсем корректно представляется: бизнес-тип сети-город идут ок, но внутри города, сами локации уходят левее, чем плашка города.

## Что изменено

- Default collapse seed расширен с business-only до business/type/city.
- Раскрытие дерева стало последовательным по уровням.
- Leaf-локации получили desktop compensation offset, равный отсутствующей колонке toggle+gap.
- На mobile дополнительный offset отключается.
- Metadata pills переведены с Bootstrap text-bg-light на theme-aware Iguana tokens.
- Badge сохранённого iikoServer secret также переведён на theme-aware tokens.
- Backend/tree payload/save contract не менялись.
- Добавлен targeted source-contract test.

## Проверки

- node --check для обоих locations runtime JS.
- git diff --check.
- Docker Maven: SettingsLocationsTreeHierarchySourceContractTest.
- Проверка, что app.css/settings.css не меняются при targeted test.
