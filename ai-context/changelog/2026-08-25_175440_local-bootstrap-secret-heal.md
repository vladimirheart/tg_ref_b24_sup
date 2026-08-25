# Changelog

- Для локального PostgreSQL bootstrap обновлены `scripts/bootstrap-first-run.ps1` и `scripts/bootstrap-first-run.sh`: новый `.env` теперь сразу получает безопасные случайные значения `APP_INTERNAL_BOT_API_TOKEN` и `APP_SECURITY_REMEMBER_ME_KEY`.
- Добавлен `scripts/ensure-local-bootstrap-secrets.ps1`, который мягко долечивает старые bootstrap-`.env` без этих ключей или с placeholder-значениями и не трогает явные пользовательские env overrides.
- `spring-panel/run-windows.bat` теперь запускает helper до импорта `.env` в текущий `cmd`-процесс, чтобы секреты попадали в runtime с первого запуска, а не только после ручного редактирования файла.
- Обновлены `.env.example` и `docs/environment_variables.md`, чтобы новый bootstrap secret contract был явно задокументирован.
- На текущем локальном окружении helper успешно добавил недостающие `APP_INTERNAL_BOT_API_TOKEN` и `APP_SECURITY_REMEMBER_ME_KEY` в `.env`, после чего `run-windows.bat` поднял `spring-panel`, а `GET /login` на `http://localhost:18080` вернул `HTTP 200`.

## User Prompt

Промпты пользователя:

- `PS C:\Users\SinicinVV\git_h\tg_ref_b24_sup\spring-panel> .\run-windows.bat`
- Пользователь приложил полный консольный лог этого запуска, по которому было видно падение на `panelSecurityRuntimeGuard`.
