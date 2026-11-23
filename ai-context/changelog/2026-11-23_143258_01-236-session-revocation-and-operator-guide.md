# 01-236: подробный инструктаж и принудительный выход пользователей

Время: 2026-11-23 14:32:58 Europe/Moscow.

## Промт пользователя

```text
тогда дай максимально подробный инструктаж как сменить пароли PostgreSQL, RabbitMQ, Redis и grafana(тут вообще непонятно как зайти в админку)
и для пользователей нужна возможность: а) принудительного разлогина конкретного пользователя б) принудительный разлогин ВСЕХ пользователей.
да, и со стартовой страницы нужно убрать инфо о штатных кредах для входа
```

## Выполнено

- Runbook, разделы 11–12: точные PowerShell commands для daemon/project/status/rehearsal и четырёх отдельных apply, источники persisted credentials, проверки новых/старых подключений, внешние клиенты и best-effort rollback. Grafana: отдельный login, loopback address, SSH tunnel, различие server/org admin и восстановление через CLI stdin без удаления volume. Основные механизмы сверены с официальными PostgreSQL/RabbitMQ/Redis/Grafana docs 2026-11-23.
- V100 добавляет users.session_version. Principal хранит snapshot с serialVersionUID=1 и совместимой старой версией 0. HTTP filter отклоняет несовпадение до защищённого действия, удаляет старые session/cookies. БД-ошибка fail closed.
- Remember-me учитывает версию в signing material; старый токен не восстанавливает вход после отзыва. Ранее выпущенный stock cookie совместим при version=0. Login, начатый до increment, не выдаёт cookie новой версии. Signature mismatch не оставляет token material в exception/debug logs.
- Admin API и UI: отозвать все сеансы выбранного numeric user ID либо всех аккаунтов, включая оператора. Только актуальные ROLE_ADMIN/ROLE_PORTAL_ADMIN с bound principal и CSRF; PAGE_USERS или username admin не дают права. Audit содержит только actor/target IDs и affected count. Повторный вход по паролю разрешён.
- UI/GENERAL SSE проверяются до доставки и на heartbeat. Отзыв закрывает старые streams и возвращает браузер на вход. Одна JSON auth probe на серию SSE ошибок; периодического HTTP keep-alive не добавлено. При 503/network error нет ложного redirect по результату probe.
- Удалена подсказка начальных credentials и placeholder admin со страницы входа. Добавлено нейтральное сообщение об окончании сеанса. Правило account credentials дополнено текущим поведением.
- Уже имевшиеся шесть uncommitted файлов предыдущего bcrypt slice сохранены; старый changelog не переписывался.

## Валидация и границы

- baseline=HEAD=origin_main=`68916f492bcbbc8f7853d5b3fc9d12b5d18256cc`; remote main подтверждён read-only.
- Detached sandbox `.tmp/236-session-revocation` включает прежний валидированный bcrypt fix. Maven wrapper собрал 779 main / 735 test sources; основной проход: **198 tests, 0 failures/errors/skips**. Новые test classes добавлены после списка compiler inputs, скомпилированы javac --release 17 с classpath Surefire, затем проверены surefire:test: **62 tests, 0 failures/errors/skips**.
- Дополнительные проверки: migration/default/check constraint; committed version видна из нового service/connection; 24 concurrent single/global revokes не теряют increments; missing schema fail closed; old/new serialized context; stock/new remember-me cookies и login/revoke race; CSRF/roles/404/503; два web stream instances, прекращение доставки private payload и GENERAL heartbeat. GENERAL SQL regression: 50 PostgreSQL tests включены в 62.
- Временный PostgreSQL запущен на loopback/nonstandard port с собственным случайным nonce и отдельным data directory; application DB/config/secrets не использовались. Отдельно проверяется сохранение счётчиков после штатного restart; собственный test server останавливается после проверки.
- `tools/session-revocation-qa.cjs`: 8 headless Edge browser scenarios + 4 production SSE function scenarios, failures=0. Existing auth-credential-input QA: 61 browser scenarios, failures/errors=0. Node syntax и git diff --check GREEN. Build-generated CSS возвращён к baseline и не применяется.
- Логи/отчёты: `.tmp/236-session-tests.log`, `.tmp/236-session-persistence-tests.log`, `.tmp/236-session-browser/report.json`, `.tmp/236-session-credential-browser/report.json`, `.tmp/236-session-persistence-restart.json`.
- Sandbox validation и root application фиксируются отдельно в `.tmp/236-session-application.json`. Применять разрешено только тот же проверенный набор после exact baseline/allowed-dirty/hash guards. Staged=false; committed=false; pushed=false; deployment=false; production_migration=false; credential_rotation=false. Полный repository suite не запускался.
- 01-236 остаётся 🟡: нужен согласованный rollout всех web replicas, V100 на целевом users store и отдельная функциональная приёмка; исходные infrastructure rotation/DR prerequisites не закрыты. При откате не обнулять версии. Смена пароля и явный revoke — отдельные действия. Уже выполняющийся запрос не отменяется.

## Набор файлов и команды после review

Включён прежний uncommitted bcrypt slice, чтобы commit охватывал все зависимые изменения.
Команды ниже не выполнялись автоматически:

```powershell
$files = @(
  'ai-context/rules/08-panel-account-credentials.md',
  'ai-context/tasks/task-details/01-236.md',
  'ai-context/changelog/2026-11-23_124638_01-236-auth-and-secret-lifecycle.md',
  'ai-context/changelog/2026-11-23_143258_01-236-session-revocation-and-operator-guide.md',
  'docs/runbooks/persisted-credential-rotation-apply.md',
  'spring-panel/src/main/java/com/example/panel/controller/AuthManagementApiController.java',
  'spring-panel/src/main/java/com/example/panel/security/LegacyCompatiblePasswordEncoder.java',
  'spring-panel/src/main/java/com/example/panel/security/PanelSessionVersionService.java',
  'spring-panel/src/main/java/com/example/panel/security/PanelUserPrincipal.java',
  'spring-panel/src/main/java/com/example/panel/security/SecurityConfig.java',
  'spring-panel/src/main/java/com/example/panel/security/SessionAuthorityFilter.java',
  'spring-panel/src/main/java/com/example/panel/security/UserRepositoryUserDetailsService.java',
  'spring-panel/src/main/java/com/example/panel/security/VersionedRememberMeServices.java',
  'spring-panel/src/main/java/com/example/panel/service/GeneralTicketActor.java',
  'spring-panel/src/main/java/com/example/panel/service/GeneralTicketRealtimeService.java',
  'spring-panel/src/main/java/com/example/panel/service/UiEventRecipient.java',
  'spring-panel/src/main/java/com/example/panel/service/UiEventStreamService.java',
  'spring-panel/src/main/resources/db/migration/postgresql/V100__user_session_revocation.sql',
  'spring-panel/src/main/resources/static/js/auth-management.js',
  'spring-panel/src/main/resources/static/js/general-dialogs.js',
  'spring-panel/src/main/resources/static/js/sidebar.js',
  'spring-panel/src/main/resources/templates/auth/login.html',
  'spring-panel/src/main/resources/templates/fragments/navbar.html',
  'spring-panel/src/main/resources/templates/general/dialogs.html',
  'spring-panel/src/main/resources/templates/settings/fragments/users-access.html',
  'spring-panel/src/main/resources/templates/settings/index.html',
  'spring-panel/src/test/java/com/example/panel/security/FormLoginIdentityWebMvcTest.java',
  'spring-panel/src/test/java/com/example/panel/security/LegacyCompatiblePasswordEncoderTest.java',
  'spring-panel/src/test/java/com/example/panel/security/SessionAuthorityWebMvcTest.java',
  'spring-panel/src/test/java/com/example/panel/security/SessionRevocationPostgresTest.java',
  'spring-panel/src/test/java/com/example/panel/security/VersionedRememberMeServicesTest.java',
  'spring-panel/src/test/java/com/example/panel/service/SessionRevocationStreamTest.java',
  'tools/session-revocation-qa.cjs'
)
git status --short
if (@(git diff --cached --name-only).Count -ne 0) { throw 'Сначала проверить ранее staged файлы' }
git add -- $files
git diff --cached --check
git diff --cached --name-only
# Список должен точно совпасть с $files, а diff --check пройти без ошибок.
git commit -m 'fix(security): отзыв сеансов пользователей и инструкции ротации #ai'
git push
```
