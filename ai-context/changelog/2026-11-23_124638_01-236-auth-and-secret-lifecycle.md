# 2026-11-23 — 01-236: password verifier и расширенная карта секретов

Время: 2026-11-23 12:46:38 Europe/Moscow.

## Промт пользователя

```text
хорошо. мinio разберусь. давай по безопасности чуть дальше и расширеннее - что гед когда и как менять?
```

## Выполнено

- В LegacyCompatiblePasswordEncoder bcrypt распознаётся до plaintext compatibility path. Сохранённый bcrypt hash больше нельзя предъявить как пароль. Legacy plaintext compatibility для остальных stored values сохранена; существующие accounts не переписывались.
- Добавлены регрессии для bcrypt 2a/2b/2y, допустимого bcrypt-looking пользовательского password и реального Spring Security form login с remember-me и mocked JDBC.
- В раздел 10 credential runbook добавлены карта хранения, порядок и условия смены infra/admin/provider/integration/Alertmanager/remember-me/master/AES/TLS/host secrets. MinIO оставлен пользователю.
- Зафиксированы открытые вопросы: session revocation после password reset; инвентаризация legacy plaintext; защита JSON-конфигурации и backup; ротация производных child credentials и AES данных; MFA/rate limiting и разделение runtime/admin grants.
- Политика календарной смены пользовательских паролей сверена с актуальным NIST; lifecycle/backup/key management — с официальными OWASP Cheat Sheets. 90 дней для независимых служебных static secrets указаны как предлагаемая локальная политика после rehearsal, не универсальный норматив.

## Проверки

- Baseline / HEAD / origin/main: `68916f492bcbbc8f7853d5b3fc9d12b5d18256cc`; remote main и предыдущий commit проверены read-only.
- Detached temporary worktree: Maven wrapper собрал 777 main / 735 test sources. На неисправленном encoder регрессии дали 5 failures / 45 tests, включая успешный HTTP login по synthetic stored hash.
- Изменённый encoder скомпилирован `javac --release 17` с classpath baseline Surefire; Maven wrapper `surefire:test` выполнил encoder/form-login/profile-password/user-details suites: **87 tests, 0 failures, 0 errors, 0 skipped**.
- Новый функциональный source сначала проверен в sandbox. Build-generated CSS там возвращён к baseline и не входит в применяемый patch.
- `git diff --check` GREEN. Полный repository suite не запускался.
- Логи: `.tmp/236-expanded-baseline-tests.log`, `.tmp/236-expanded-fixed-tests.log`; данные только synthetic, JDBC mocked. Рабочая БД/сервисы/volumes и реальные secrets не использовались и не менялись.
- Deploy, DB migration, staging, commit и push не выполнялись. Для активации исправления нужен отдельный выпуск нового web image. `01-236` остаётся `🟡`.

## Файлы и команды фиксации после review

```powershell
git status --short
$files = @(
    'spring-panel/src/main/java/com/example/panel/security/LegacyCompatiblePasswordEncoder.java',
    'spring-panel/src/test/java/com/example/panel/security/LegacyCompatiblePasswordEncoderTest.java',
    'spring-panel/src/test/java/com/example/panel/security/FormLoginIdentityWebMvcTest.java',
    'docs/runbooks/persisted-credential-rotation-apply.md',
    'ai-context/tasks/task-details/01-236.md',
    'ai-context/changelog/2026-11-23_124638_01-236-auth-and-secret-lifecycle.md'
)
if (@(git diff --cached --name-only).Count -ne 0) { throw 'Сначала проверьте ранее staged файлы' }
git add -- $files
git diff --cached --check
git diff --cached --name-only
# Перед commit список должен точно совпасть с $files, а diff --check пройти без ошибок.
git commit -m 'fix(security): запретить вход по bcrypt-хешу и описать ротацию секретов #ai'
git push
```
