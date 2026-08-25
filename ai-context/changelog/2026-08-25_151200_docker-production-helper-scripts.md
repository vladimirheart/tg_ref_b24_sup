# Helper scripts for dockerized production contour

- Время: `2026-08-25 15:12:00`
- Файлы:
  - `scripts/docker-production-up.ps1`
  - `scripts/docker-production-up.sh`
  - `scripts/docker-production-down.ps1`
  - `scripts/docker-production-down.sh`
  - `docs/docker-production-contour.md`
  - `README.md`
- Промты пользователя:

```text
это одно из правил проекта - не забывай о нём. ты езё забыл создать задачу, что тоже есть в правилах проекта.

давай дальше по задаче
```

- Что сделано:
  - добавлены штатные helper-скрипты для запуска и остановки dockerized production contour на PowerShell и shell;
  - в скрипты добавлен `validate-only` режим, чтобы можно было проверять контур даже без фактического запуска контейнеров;
  - документация и README дополнены примерами запуска через новые helper-скрипты.
