# Local launcher production guard

## Пользовательский запрос

`давай делать всё правильно. проект хочу уже запустить в prod, но остаётся ещё что?`

## Фактические изменения

- Windows launcher проверяет Docker labels активных `panel-web`, `ops-worker` и `bot-runner` ролей.
- При найденном production runtime локальный `spring-boot:run` блокируется до preflight, Maven, backup runner и выбора альтернативного HTTP-порта.
- Для изолированной диагностики сохранён только явный override `IGUANA_ALLOW_LOCAL_PANEL_RUN=true`.
- Production runbooks обновлены: Docker edge является production entrypoint, Windows launcher — local/dev инструмент.
- Добавлен source-contract регрессионный тест и задача `01-246` переведена в ожидание ручной проверки.

