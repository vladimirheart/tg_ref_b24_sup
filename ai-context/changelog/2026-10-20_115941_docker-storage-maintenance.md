# Обслуживание Docker-хранилища проекта

Дата: 2026-10-20, 11:59:41, Europe/Moscow.
Задача: 01-279. Baseline: 4cc81f8a799f748063655762fa3ef00f0a9563f8.

## Промты пользователя

> всё прошло ок. но докер пришлось запуска дважды, по какой причине - непонятно.как-то в проект эту очистку можно заложить?

После предложения scoped image cleanup, ротации логов и maintenance script:

> давай

## Что изменено

- PowerShell/Bash maintenance: report-only по умолчанию; явная очистка только
  dangling-образов с меткой io.iguana.project и age retention.
- Build labels учитывают фактическое Compose project name во всех собственных
  образах; именованные release/rollback images не удаляются.
- Автоочистка после успешного detached build/start: недельный интервал,
  отключение через окружение/.env, lock и state на daemon/project.
- Ошибка обслуживания не ломает успешный запуск приложения.
- Во всех шести Compose-файлах Docker stdout/stderr ограничены тремя файлами
  по 10 МБ по умолчанию, с настройками через окружение/.env.
- Документация, project-specific policy и task metadata обновлены.

## Проверка

- 32 behavioral tests с fake Docker: PowerShell 5.1 и Git Bash; после
  дополнительной проверки повреждённых stamps повторены четыре targeted tests.
- 14 Compose config вариантов, 206 service checks, default/custom project names
  и log option overrides.
- PowerShell parser, bash -n, git diff --check.
- Валидация выполнена в отдельном detached worktree перед переносом изменений.

Реальный Docker prune, build, up/down, restart, WSL shutdown, VHDX compact,
изменение Docker Engine GC, deploy, migration, commit и push не выполнялись.

## Затронутые файлы

- .env.example
- .gitignore
- README.md
- ai-context/changelog/2026-10-20_115941_docker-storage-maintenance.md
- ai-context/rules/04-docker-storage-maintenance.md
- ai-context/tasks/task-details/01-279.md
- ai-context/tasks/task-list.md
- docker-compose.local-postgres.yml
- docker-compose.production-backup.yml
- docker-compose.production-contour.yml
- docker-compose.production-edge.yml
- docker-compose.production-legacy-bots.yml
- docker-compose.production-observability.yml
- docs/docker-production-contour.md
- scripts/docker-maintenance.ps1
- scripts/docker-maintenance.sh
- scripts/docker-production-up.ps1
- scripts/docker-production-up.sh
- tools/test-docker-maintenance.py
