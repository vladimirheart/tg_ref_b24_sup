# 2026-08-20 12:25:12 - run windows maven retry

## Пользовательский промпт

`падает сборка проекта`

## Что изменено

- в `spring-panel/run-windows.bat` добавлен один автоматический retry для `spring-boot:run`, если первый Maven-запуск завершился ошибкой;
- перед повтором launcher теперь очищает локальные `*.lastUpdated` маркеры внутри `spring-panel/.m2/repository`, чтобы Maven не спотыкался о кэш неудачной загрузки зависимостей после сетевого таймаута;
- повторный запуск выполняется с `-U`, чтобы форсировать повторную проверку и докачку зависимостей из удалённого репозитория.

## Проверка

- `spring-panel\\mvnw.cmd -U -Dmaven.repo.local=.m2\\repository -Dmaven.test.skip=true -q dependency:go-offline`
- `spring-panel\\mvnw.cmd -Dmaven.repo.local=.m2\\repository -Dmaven.test.skip=true -q compile`
