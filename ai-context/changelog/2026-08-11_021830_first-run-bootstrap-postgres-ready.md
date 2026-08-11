# 2026-08-11 02:18:30 - first run bootstrap postgres ready

## Промт пользователя
- `давай дальше, но создай возможность установки всего, что требуется при первом запуске проекта`

## Что сделано
- добавлен шаблон `.env.example` для first-run конфигурации;
- добавлен `docker-compose.local-postgres.yml` с локальным PostgreSQL-контуром для fresh PostgreSQL-first запуска;
- добавлены bootstrap-скрипты `scripts/bootstrap-first-run.ps1` и `scripts/bootstrap-first-run.sh`, которые создают `.env`, подготавливают локальные каталоги и при наличии Docker поднимают PostgreSQL;
- bootstrap переведён на безопасный `auto`-режим: при отсутствии Docker он не блокирует запуск и оставляет проект в `APP_DB_MODE=sqlite`;
- `spring-panel/run-windows.bat` и `spring-panel/run-linux.sh` теперь автоматически запускают bootstrap при отсутствии корневого `.env`;
- обновлены `README.md`, `docs/windows_setup.md`, `docs/configuration.md` и `docs/environment_variables.md` под новый first-run сценарий.

## Затронутые файлы
- `.env.example`
- `docker-compose.local-postgres.yml`
- `scripts/bootstrap-first-run.ps1`
- `scripts/bootstrap-first-run.sh`
- `spring-panel/run-windows.bat`
- `spring-panel/run-linux.sh`
- `README.md`
- `docs/windows_setup.md`
- `docs/configuration.md`
- `docs/environment_variables.md`
- `ai-context/tasks/task-details/01-181.md`

## Проверка
- `powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File scripts/bootstrap-first-run.ps1 -ValidateOnly` - passed
- `git diff --check -- README.md docs/windows_setup.md docs/configuration.md docs/environment_variables.md spring-panel/run-windows.bat spring-panel/run-linux.sh scripts/bootstrap-first-run.ps1 scripts/bootstrap-first-run.sh .env.example docker-compose.local-postgres.yml` - only CRLF/LF warnings, no diff formatting errors
- проверка `bash`-скрипта не выполнена в текущем окружении, потому что `bash` отсутствует
