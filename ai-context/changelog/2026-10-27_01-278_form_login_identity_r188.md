# 01-278 — точный аккаунт при входе, R188

Промт пользователя: «давай дальше по задаче».

Применяются ранее подтверждённые границы: рабочие проект и Docker на другой
машине/в другой сессии; автоматические commit/push разрешены до конца
2026-10-27 Moscow (21:00 UTC). Политика новых/явно изменяемых логинов из R187
сохраняется; нормализация записи не распространяется на поиск старого аккаунта.

## Дефект и изменение

В локальном bytecode используемого spring-security-web 6.2.4 подтверждён
username.trim() в стандартном UsernamePasswordAuthenticationFilter.
UserRepositoryUserDetailsService уже принимал точный ключ, но получал обрезанный
username. При одинаковом пароле у spaced и обычного аккаунта форма входа
могла авторизовать соседний аккаунт и выдать его authorities. Отсутствие точного
аккаунта или blocked/disabled spaced аккаунт также не останавливали этот alias.

Добавлен ExactFormLoginConfigurer с маленьким внутренним наследником фильтра:
POST username/password сохраняются точно, null по-прежнему превращается в
пустую строку, details устанавливаются и AuthenticationManager вызывается как
прежде. В SecurityConfig заменено только подключение formLogin configurer.
Стандартный AbstractAuthenticationFilterConfigurer выполняет остальную связку
с DAO, security context repository, session strategy, handlers и remember-me.
Фильтр не регистрируется отдельным servlet bean; двойного login processing нет.

Сохранены существующие /login, /login?error, /post-login с always-use-default,
CSRF cookie/form token, remember-me 14 суток, session fixation protection,
credentials erasure и logout. DAO SQL/case folding, encoder, authority loading,
аккаунты/grants/schema/config и password write policy R187 не менялись.
Если у обычного аккаунта добавить пробелы при входе, вход теперь отклоняется,
пока не указан существующий точный логин.

## Проверки

Новый FormLoginIdentityWebMvcTest: 37 cases, реальный SecurityConfig/DAO/encoder,
MockHttpSession, remember-me и рендер настоящей Thymeleaf формы. Только JDBC
mocked, без подключения к БД. RED v2 до production edits:
16 failures / 21 controls / 0 errors; финальный frozen RED повторён в v4,
те же v4 test bytes GREEN в общем v5 прогоне.
13 вариантов ключа: обычный/spaces/TAB/CR/LF/CRLF, delimiter/markup/URI/Unicode,
BIGINT-like string и mixed case. Проверены own /whoami и authorities, wrong-account
success с одним паролем, missing exact account, disabled/blocked, точный пароль,
missing/blank input, CSRF до DAO, настоящие XSRF cookie и masked token из HTML,
remember-me restore/disable/tamper, session ID/attributes, logout и saved request.

В первом новом fixture v1 один случай зависел от изменения CSRF repository
тестовым csrf() postprocessor. До production edits harness переведён целиком
на настоящие GET /login cookie/token без csrf() helper. RED v2 подтверждает
неправильный principal/успешный вход вместо отказа. В v3 поведение исправлено,
но пять новых assertions ожидали один lookup вместо существующего local/parent
AuthenticationManager retry. Только это ожидание уточнено до точных двух raw
lookups, запрет lookup соседнего аккаунта сохранён. Production в sandbox временно
возвращён к baseline, повторён RED v4 (16/21/0), затем восстановлены те же
production bytes. Frozen v4 tests и существующие tests больше не менялись.

Maven wrapper v5: 12 suites / 550 tests, failures/errors/skips=0. Помимо новых
37 — 281 channel management authority/page cases, credential/profile/controller,
permission/activity и UserDetails/encoder регрессия. Java cache R187 применён
только после normalized SHA сверки всех baseline Java sources; два изменённых
production Java скомпилированы. Generated CSS имеет прежние Git blobs и не
включается в scope. Browser/Node/Docker/live DB/HTTP/provider tests не запускались.

## Состояние и границы

Baseline: clean main, HEAD/origin/main/actual remote
d8a68ca80866fafb1a6305d6fd1a657136322166. R187 проверен по parent/subject/exact
20 files/normalized content. Detached sandbox .tmp/01-278-r188. Здесь source
checkout C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочие проект и Docker находятся
на другой машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup.
Source apply и отдельный expiry-guarded Git publisher проверяют exact baseline,
remote/files/content/test/report hashes; факты сохраняются в ignored artifacts.
Task detail, S0 и постоянное правило 08 обновлены; прежние changelog не менялись.

Checkpoint R184 и пользовательский start/stop одного бота сохранены точно.
Нет новых working probes, restart/deploy/migration/backfill/config/flags,
переименования данных или отправок провайдерам. R188 здесь не развёрнут.
Это MockMvc security boundary, не приёмка working JDBC sessions, native browser
input sanitization или fleet. Authority refresh/revoke уже открытых сессий,
case-ambiguous account rows, другие legacy identity consumers, S2B, сквозная
изоляция S3–S5 и worker/fleet/rollout S6 остаются открыты. Статус 01-278 — 🟡.
