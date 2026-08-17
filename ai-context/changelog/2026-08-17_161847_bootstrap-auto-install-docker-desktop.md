# 2026-08-17 16:18:47 - bootstrap auto install docker desktop

## Пользовательский промпт

`докера нет. и при первом запуске докер должен подтягиваться`

## Что изменено

- в `scripts/bootstrap-first-run.ps1` first-run bootstrap для Windows больше не ограничивается проверкой уже установленного Docker:
  - ищет `docker.exe` и `Docker Desktop.exe` по стандартным путям;
  - если Docker Desktop уже установлен, но ещё не поднят, запускает его и ждёт readiness;
  - если Docker отсутствует, пытается автоматически установить `Docker Desktop` через `winget`;
  - после установки/старта ждёт `docker compose version` и `docker info`, и только потом поднимает локальные `PostgreSQL` и `RabbitMQ`;
- добавлены bootstrap overrides:
  - `IGUANA_BOOTSTRAP_INSTALL_DOCKER`;
  - `IGUANA_BOOTSTRAP_ALLOW_SQLITE_FALLBACK`;
  - `IGUANA_BOOTSTRAP_DOCKER_READY_TIMEOUT_SECONDS`;
- обновлена документация `docs/windows_setup.md`, `docs/configuration.md` и `docs/environment_variables.md` под новый first-run сценарий Windows.

## Проверка

- `powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\\scripts\\bootstrap-first-run.ps1 -ValidateOnly`
- `git diff --check -- scripts/bootstrap-first-run.ps1 docs/windows_setup.md docs/configuration.md docs/environment_variables.md`
