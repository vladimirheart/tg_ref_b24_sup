# 2026-07-31 13:02:46 - iguana-docs-and-portable-package

## Затронутые области

- `README.md`
- `docs/IGUANA_PROJECT_GUIDE.md`
- `docs/IGUANA_TRANSFER_WINDOWS.md`
- `scripts/export-iguana-portable.ps1`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-159.md`

## Пользовательский промпт

> обнови документацию к проекту iguana максимально подробно.
>
> скопируй файлы проекта в каталог C:\Intel, для запуска на другой машине вместе с документацией к проекту iguana

## Что сделано

- Полностью обновлён `README.md` как главная входная точка по Iguana: добавлены структура системы, quick start, карта каталогов, БД, конфигурация, ссылки на ключевые документы и отдельный раздел про перенос на другую машину.
- Добавлен подробный эксплуатационный документ `docs/IGUANA_PROJECT_GUIDE.md` с описанием архитектуры, runtime-контуров, прикладных сущностей, запуска, данных, question flow, operational workflow и troubleshooting.
- Добавлен отдельный Windows-runbook `docs/IGUANA_TRANSFER_WINDOWS.md` с пошаговым переносом, перечнем обязательных файлов, prerequisites, проверками после первого старта и типовыми ошибками переноса.
- Добавлен воспроизводимый `PowerShell`-скрипт `scripts/export-iguana-portable.ps1`, который собирает переносимый пакет проекта в `C:\Intel\iguana` и исключает локальные кэши, `target`, логи и transient runtime-файлы.
- Скрипт экспорта фактически выполнен через `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\export-iguana-portable.ps1 -CleanTarget`, после чего создан каталог `C:\Intel\iguana` с документацией, исходниками, SQLite-базами, `attachments`, `bot_databases` и shared-конфигами.

## Остаточный риск

- Для запуска на другой машине всё ещё нужен `JDK 17`, а также корректные внешние токены и сетевой доступ для тех каналов и интеграций, которые планируется использовать.
- В переносимый пакет сознательно не включены `logs/`, `target/`, `.git/`, `.venv/`, `node_modules/`, `run/` и transient-файлы `*.db-wal`/`*.db-shm`, поэтому для глубокой ретроспективной диагностики старой машины может потребоваться отдельный перенос логов.
