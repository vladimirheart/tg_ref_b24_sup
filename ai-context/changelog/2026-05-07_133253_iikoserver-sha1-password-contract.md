# 2026-05-07 13:32:53 - iikoserver sha1 password contract

## Промпт пользователя

```text
теперь ругается так: Не удалось загрузить департаменты из iiko https://bb-chain.iiko.it: HTTP 401 при получении access token
мне не нравится что нужно укащывать API secret. это поле должно быть полем ввода пароля в формате sha1, и вот уже с этими кредами мой проект долден получать токен и уже с этим токеном выполнять запрос департаментов
```

## Что сделано

- Контракт настроек источников `iikoServer` для локаций переведён на явный `SHA-1 пароль` вместо абстрактного `API secret`.
- В UI страницы настроек:
  - `API login` переименован в `Логин iikoServer`;
  - `API secret` переименован в `SHA-1 пароль`;
  - добавлены подсказки, что нужно вводить уже готовый 40-символьный hex `SHA-1`.
- В `LocationsIikoServerSourceSettingsService` добавлена backend-валидация нового значения пароля:
  - принимается только 40-символьный hex `SHA-1`;
  - при неверном формате отдаётся понятная `IllegalArgumentException`.
- Валидация применяется только к реально введённому новому значению, чтобы не ломать открытие страницы и не блокировать редактирование legacy-конфигураций, пока пользователь не заменил старый secret.
- В `IikoDepartmentLocationCatalogService.HttpIikoDepartmentGateway` убрано автозахэширование пароля:
  - runtime больше ничего не преобразует;
  - в `pass` уходит ровно то `SHA-1` значение, которое сохранено в настройках.
- Обновлены тесты:
  - `LocationsIikoServerSourceSettingsServiceTest` теперь проверяет сохранение валидного `SHA-1` и отказ при невалидном формате;
  - `IikoDepartmentLocationCatalogServiceTest` теперь проверяет, что gateway использует уже переданный `SHA-1` как есть.
- Задача `01-076` переведена в статус `🟣`.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=LocationsIikoServerSourceSettingsServiceTest,IikoDepartmentLocationCatalogServiceTest,IikoDepartmentLocationsSyncServiceTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Итог

- Вручную сохраняемые креды для `iikoServer` теперь заданы прозрачно: `login + SHA-1 пароль`.
- Если после этого ручная sync отдаёт `401`, причина уже не в контракте поля и не в автоконверсии, а в том, что на стороне настроек указан неверный логин или неверный `SHA-1` пароль.
