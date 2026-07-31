# 2026-07-31 15:04:33 - admin-storage-inventory-modal

## Затронутые области

- `spring-panel/src/main/java/com/example/panel/service/AdminStorageInventoryService.java`
- `spring-panel/src/main/java/com/example/panel/controller/AdminStorageInventoryApiController.java`
- `spring-panel/src/main/java/com/example/panel/controller/ManagementController.java`
- `spring-panel/src/main/resources/application.yml`
- `spring-panel/src/main/resources/templates/settings/index.html`
- `spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js`
- `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `docs/environment_variables.md`
- `docs/IGUANA_PROJECT_GUIDE.md`
- `README.md`

## Пользовательский промпт

> хорошо, но возможность запуска скрипта добавь в админку. и продолжи по задаче

## Что сделано

- В админке `Настройки` добавлена отдельная tile и modal `Storage inventory Iguana` для запуска `scripts/report-iguana-storage.py` без консоли.
- Добавлен backend-сервис `AdminStorageInventoryService`, который:
  - находит repo root;
  - запускает inventory-скрипт через Python;
  - принудительно задаёт `PYTHONUTF8=1` и `PYTHONIOENCODING=UTF-8`;
  - сохраняет timestamped markdown/json snapshots в `run/storage-inventory/`;
  - обновляет `latest.md` и `latest.json`.
- Добавлен защищённый endpoint `POST /api/admin/storage-inventory/run`, доступный только superuser/admin внутри контура `PAGE_SETTINGS`.
- В `settings-admin-shell-runtime.js` реализован клиентский runtime для modal:
  - запуск inventory по кнопке;
  - отображение длительности и путей snapshot-файлов;
  - summary по storage roots / SQLite / missing references / risk count;
  - вывод raw markdown report прямо в интерфейсе.
- В lifecycle shell страницы настроек добавлены `shown/hidden` callbacks и URL-alias `storage-inventory` для нового modal.
- В `ManagementController` добавлен флаг `canRunStorageInventory`, чтобы tile и modal показывались только superuser/admin.
- В документации обновлены:
  - `README.md`;
  - `docs/IGUANA_PROJECT_GUIDE.md`;
  - `docs/environment_variables.md` с новыми env-переменными `APP_ADMIN_PYTHON_EXECUTABLE`, `APP_ADMIN_REPOSITORY_ROOT`, `APP_ADMIN_STORAGE_INVENTORY_TIMEOUT`.

## Проверка

- `python -m py_compile scripts/report-iguana-storage.py`
- `spring-panel\\mvnw.cmd -q -DskipTests compile`
- `git diff --check -- ...`

## Остаточный риск

- UI и endpoint уже позволяют безопасно запускать inventory и видеть текущие storage-риски, но это ещё не remediation-слой: missing attachment references и migration к metadata-first / storage abstraction пока только диагностируются, а не исправляются автоматически.
