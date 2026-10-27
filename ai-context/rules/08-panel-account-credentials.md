# Учётные данные панели

Решение пользователя от 2026-10-27, задача 01-278, R187.

## Логин при создании и явном изменении

- Разрешены только латинские буквы A–Z/a–z, цифры и `. _ - @`.
- Обычные пробелы U+0020 по краям удаляются перед проверкой и записью.
  Внутренние пробелы, TAB/CR/LF, NBSP и прочие символы отклоняются.
- Формы показывают постоянную подсказку, tooltip и ошибку возле поля.
  После обрезки на blur показывается уведомление об удалении пробелов.
- Проверка обязательна и на сервере. Конфликт с другим логином проверяется
  без учёта регистра, в том числе со старым логином с пробелами по краям.
  SQL `lower(trim(username))` используется только для консервативного отказа
  в новой записи/переименовании, с исключением текущего numeric user ID.
  Это не способ поиска аккаунта или определения его прав.

Существующие логины не исправляются массово. Сохранение других полей профиля
не отправляет изменённый браузером legacy username; явно переданное неизменённое
значение тоже не переименовывается. Явное переименование следует новой политике.
Ссылки на аккаунт и миграция зависимых данных требуют отдельного scope.

## Пароль при создании и смене

Пароли не обрезаются: пробелы, регистр, Unicode и знаки сохраняются точно.
Подтверждение сравнивается точно, включая пробелы. Пустые значения и строки
только из пробельных символов запрещены. Запрещены управляющие C0/C1 и
некорректные Unicode surrogate sequences. Максимум — 72 байта UTF-8, согласно
границе существующего bcrypt encoder; кириллица и emoji занимают несколько байт.
Новый произвольный минимум длины не вводится; прежний native minlength=8
в форме собственного пароля сохранён.

Одна серверная CredentialPasswordPolicy применяется в управлении пользователями
и в смене собственного пароля. Текущий пароль проверяется как введён, без новой
политики длины для старого credential. Пустой password при legacy profile update
сохраняет прежний смысл «не менять пароль».

Вставка управляющих символов в password/text controls останавливается до
нативного удаления CR/LF браузером. Ошибка не позволяет незаметно сохранить
прежнее содержимое поля. Подсказки и ошибки не выводят пароль.

## Граница identity и прав

При входе (R188) логин и пароль передаются в AuthenticationManager точно,
без trim. Это относится и к старым логинам: если точного аккаунта нет, вход
не подставляет логин без крайних пробелов, даже при совпадении пароля.
Disabled/blocked аккаунт не заменяется похожим включённым аккаунтом.
Прежний case-insensitive SQL lookup и возврат stored username сохранены.

ExactFormLoginConfigurer меняет только извлечение credentials в POST /login.
Стандартный AbstractAuthenticationFilterConfigurer связывает фильтр с DAO,
security context repository, session strategy, success/failure handlers и
remember-me. Сохраняются /login?error, /post-login с прежним always-use-default,
CSRF cookie/form token, смена session ID, очистка credentials, logout и срок
remember-me 14 суток. Фильтр не регистрируется отдельным servlet bean.

Политика очистки логина на записи не применяется ко входу и не разрешает
искать похожий аккаунт. Новые обычные аккаунты вводят логин без добавочных
пробелов. До R189 права открытой сессии автоматически не перечитывались;
проверка сессий описана ниже. R190 закрывает неоднозначность первичного lookup:
SQL с прежним lower(username) = lower(?) и raw параметром читает LIMIT 2.
Допускается ровно одна строка; ноль/несколько совпадений дают общий отказ
до загрузки прав и выдачи remember-me cookie. Точный регистр и порядок строк
не выбирают аккаунт. Remember-me restore проходит тот же lookup; уже открытая
ID-bound сессия проверяется по собственному numeric ID и не переходит к соседу.
Новая схема/unique index и массовое исправление аккаунтов в R190 не добавлены.
MockMvc с настоящей security chain не заменяет working PostgreSQL collation,
JDBC session/fleet acceptance или проверку native browser input sanitization.

Нормализация нового логина не распространяется на существующие authenticated
account keys. UserLastActivityFilter в R193 использует positive numeric ID из
authenticated PanelUserPrincipal и exact raw username в UPDATE. Нет trim,
case-fold fan-out или unbound username fallback. Blank/authentication guards
и best-effort обработка отсутствующей колонки/недоступной БД сохранены.

Права создания/редактирования, field permissions и проверка текущего пароля
остаются обязательными. R187 не даёт новых grants и не выполняет миграцию,
backfill, переименование данных или rollout рабочего экземпляра.

## Проверка открытых сессий — R189

При DB-backed входе principal содержит положительный numeric user ID и точный
stored username. Перед каждым защищённым запросом SessionAuthorityFilter
повторно читает аккаунт **по ID**, затем прямые authorities и текущую роль.
Нет trim, lookup по имени, переноса сессии на похожий/повторно созданный аккаунт
или TTL-кэша старых прав. Case folding первичного входа из R188 не меняется.

Новые права применяются в текущем запросе, до method security и controller/service.
Меняется весь набор authorities. Новый SecurityContext сохраняется через общий
request-attribute + HttpSession repository, чтобы ON_SET_ATTRIBUTE Spring Session
увидел изменение. При одинаковом наборе прав context не перезаписывается.
Тип remember-me сохраняется: обновление прав не делает такую сессию fully authenticated.
Credentials стираются; в serialized principal остаются ID/username и права,
без password/hash. Сериализация проверена изолированно, не на рабочем JDBC store.

Удалённый, disabled/blocked или переименованный аккаунт, в том числе изменение
регистра stored username, прекращает старую сессию. То же относится к legacy
principal без numeric ID. Context очищается, сессия инвалидируется, cookies
JSESSIONID/remember-me удаляются, далее действует прежний login entry point.

Ошибка чтения аккаунта/любого источника прав возвращает 503 до защищённого
действия; старые права не используются. Сессия сохраняется для повторной проверки
после восстановления хранилища. Strict error propagation применяется к session
refresh; прежние login authority-loading fallbacks не расширяются этим шагом.

Фильтр зарегистрирован только в security chain, после remember-me и до anonymous/
activity/authorization. Public routes используют тот же MVC/static/error matcher,
что permitAll, и не зависят от refresh. Anonymous requests и прежний fallback
без настроенного users datasource сохраняются; недоступная настроенная БД не
считается fallback. CSRF остаётся перед refresh.

После будущей выкатки legacy сессиям понадобится повторный вход. Новый serialized
класс PanelUserPrincipal должен присутствовать во всех web images, читающих
JDBC sessions; старый image не прочитает новый payload. Rollout/rollback требует
согласования версий web-реплик и обработки несовместимых сессий. Эти операции
здесь не выполнялись; checkpoint/rollback R184 сохраняются как исторические факты.

Это проверка на следующем защищённом запросе, не принудительное удаление всех
сессий в кластере. Уже выполняющиеся запросы, открытые async/SSE соединения,
password-change revocation и token policy остаются отдельным scope. Schema,
миграции, backfill и рабочая среда не меняются.

## Смена собственного пароля по ID — R191

POST /profile/password требует authenticated PanelUserPrincipal с положительным
numeric ID. Контроллер читает только этот ID и проверяет точный stored username,
enabled/blocked status. У unbound/legacy principal нет fallback по логину;
требуется повторный вход. Отсутствующая запись возвращает 404, изменившаяся
identity/status — 409. UI preferences и admin user-management не изменены.

Текущий password проверяется по snapshot собственного аккаунта. Новый hash
записывается одним UPDATE с ID + exact username + исходными password и всеми
присутствующими password_hash/enabled/is_blocked values. PostgreSQL
IS NOT DISTINCT FROM сравнивает и NULL. Concurrent credential/hash, rename,
delete/disable/block дают 409 вместо success и не перезаписываются. Соседние
case-insensitive accounts не участвуют в чтении/записи.

Наличие password_hash определяется колонками результата SELECT *, без
metadata fallback; при наличии оба credential поля обновляются вместе.
Нужна ровно одна изменённая строка. Политики raw password/confirmation, текущий
пароль и CSRF остаются обязательными. Новая схема/unique index не добавлены.

PanelUserPrincipal доступен typed consumers; serialized fields/serialVersionUID
не изменены. R189 rollout/session compatibility всё ещё применима. Принудительный
выход всех сессий и изменение общей password-change/token revocation policy
не входят в R191. Security chain проверена MockMvc с stateful mocked JDBC,
без working PostgreSQL locking/driver, JDBC session store или browser acceptance.

## Identity управления пользователями и полями — R192

AuthManagementApiController определяет self/current user только по positive
numeric ID из authenticated PanelUserPrincipal. Username lookup/fallback для
этого удалён. Current user ID/is_self, собственные profile/password/photo paths
и запрет удалить/заблокировать себя используют один bound ID. Проверка текущей
DB-backed сессии остаётся обязанностью SessionAuthorityFilter R189.

Для non-superuser field permissions читаются join users/roles по тому же ID.
Требуются ровно одна строка, совпадающие ID/exact username и допустимый
enabled/blocked status; missing/changed/ambiguous rows или invalid permissions
не дают прав. Ошибка DB-чтения не превращается в поиск соседнего аккаунта.
Точный raw username не обрезается. Новые grants, page/global admin/portal bypass
и смысл существующей field matrix этим шагом не меняются.

Удаление avatar files после обновления требует photo в разрешённом UPDATE;
одного неавторизованного photo в payload недостаточно, даже если другая часть
payload разрешена. Legitimate self/admin upload/clear сохраняются.

R192 не добавляет CAS/транзакционную авторизацию всем admin CRUD, не меняет
общую session/token revocation policy и не закрывает остальные global/business
paths. Schema, сериализуемый principal и runtime не меняются. Проверка — MockMvc
с real security и изолированными JDBC/config/file adapters, без рабочей БД/файлов.

## Личные UI/triage preferences по ID — R195

UiPreferenceService принимает Authentication с authenticated PanelUserPrincipal,
positive numeric user ID, непустым exact raw username и допустимым status.
Хранилище settings_parameters использует param_type ui_preferences.account.v2
и канонический decimal user ID в value. Username не является ключом:
нет trim/case-fold lookup, fallback к v1 или auto-import из shared config.
Переиспользованный логин не наследует чужие preferences. Старые name-keyed
данные не удаляются; без отдельно разрешённого переноса новый namespace
начинается с defaults, что допустимо для тестовых данных согласно R190.

Перед storage operation читается собственный users row по ID, проверяются
exact username и enabled/not blocked. FOR SHARE удерживает account snapshot
при чтении, FOR UPDATE сериализует cooperating writers; REQUIRES_NEW transaction
охватывает account guard, чтение, merge и запись. UI и triage сохраняют общую
строку без потерянных полей/двойного первого INSERT. UPDATE ограничен исходным
JSON, ID/type/key/active status; нужен ровно один affected row.
Duplicate/corrupt rows дают 409 без overwrite, ошибки SQL-чтения/записи — 503 с rollback.
Нормализация значений, UTC timestamps, partial merge и unknown fields сохранены.

Profile API, triage controller, global model bootstrap и workspace snapshot
передают typed Authentication. Profile API и saves отвергают unbound actors;
optional bootstrap/workflow возвращает пустые defaults без доступа к хранилищу.
Global bootstrap определяет anonymous по principal type, а не строке логина.
Это не исправляет все username-based workspace participants/candidates.

Весь namespace ui_preferences.* зарезервирован: generic settings parameters
create/update/delete запрещены, grouped list и /settings model его не выдают,
в том числе admin. Личный API не принимает user ID/username от клиента.
Новых grants, schema/migrations, serialized-principal или feature-flag изменений нет.
Проверка включает real PostgreSQL row locking/rollback и security/login/session/
CSRF fixtures; не заменяет browser/fleet/JDBC session-store или live acceptance.

## Собственный check-in смены по ID — R194

WorkforceService получает Authentication от post-login/check-in controllers.
Допускается только authenticated PanelUserPrincipal с positive numeric ID,
непустым точным username и допустимым principal status. Username не используется
для повторного поиска; нет trim/case folding или legacy/unbound fallback.
Account/workforce join читается по ID: ровно одна строка, exact stored username,
enabled и not blocked. Сбой чтения не превращается в пропуск обязательной смены.

Создание workforce_shift_sessions использует conditional INSERT ... SELECT
с тем же ID/exact username, account enabled/not blocked и workforce enabled.
Изменение, зафиксированное до начала statement, предотвращает создание записи
и отправку уведомления. ON CONFLICT shift_key сохраняет идемпотентность.
Raw legacy names, schedule/late/unscheduled, user overrides и opt-in сохраняются.

Это не полная атомарная авторизация доставки относительно всех будущих
конкурентных изменений после snapshot INSERT, не делегирование WorkforceAdminApi
и не утверждение общей business/global recipient policy. Schema, grants,
serialized principal и feature flags не меняются. Проверка — реальные
security/login/session/CSRF MVC fixtures плюс отдельный PostgreSQL Testcontainers,
не приёмка R194 на тестовом стенде или production.

## Отметка активности своего аккаунта — R193

UserLastActivityFilter пишет UTC last_portal_activity_at по ID + exact username
из authenticated PanelUserPrincipal. Legacy/unbound, zero/negative ID, blank,
anonymous и unauthenticated principals не пишут. Тип remember-me сохраняется;
сессия перед activity проходит проверку R189. Отказ session refresh не допускает
activity touch, тогда как ошибка самого touch остаётся best-effort.

Появление case-fold коллизии не отмечает другой аккаунт online. Rename/delete
между refresh и записью даёт no-op по прежнему ID/name вместо fallback.
Прежний one-failure cache отсутствующей колонки и retry прочих DB failures
сохранены. Schema, principal serialization и grants не меняются. Это не полная
recipient/business policy и не исправление остальных username-based consumers.
Real filter/security/remember-me проверены MockMvc с mocked JDBC; working DB,
браузер и fleet здесь не проверялись.

## Личные automation credentials по ID — R196

EmployeeDiscountAutomationCredentialService принимает authenticated
PanelUserPrincipal с positive numeric ID, exact raw username и допустимым status.
Storage: settings_parameters, employee_discount_automation_credentials.account.v2,
value = canonical decimal Long ID. Username не ключ; нет trim/lower lookup,
fallback/auto-import из v1. Новые/reused-name accounts не наследуют чужие секреты.
Старые тестовые данные оставлены; новые credentials сохраняются заново согласно R190.

Account ID/exact username/enabled/not blocked проверяются под FOR SHARE при read
и FOR UPDATE при write, в REQUIRES_NEW read/merge/write transaction. Cooperating
writers сериализуются по account row. UPDATE сравнивает исходный JSON, row ID,
type/key/active status и affected count. Ambiguous/corrupt/changed storage — 409;
SQL/transaction infrastructure failures — 503. Merge и secret masking сохранены.

Credential API, orchestration и provider adapters передают Authentication, не
строковый actor. Весь employee_discount_automation_credentials.* зарезервирован
от generic settings CRUD/list и page model, в том числе admin; ui_preferences.*
защита сохраняется. Registry читает v2 canonical numeric keys без duplicate choice,
entry keys включают account ID, SHA-256 полного iiko URL-key; секреты не выдаются.

Run audit display/history пока name-based: ActorIgnoreCase/trim остаются отдельным
подтверждённым identity gap. Shared automation settings и global/business policy
этим шагом не изменены. Schema/migrations/grants/serialized principal не меняются.
Проверены real security и loopback HTTP с isolated stateful JDBC/transaction adapters;
настоящие PostgreSQL locking/concurrency/rollback и runtime acceptance ещё нужны.

## Personal automation run history по ID — R197

AutomationRun.actorUserId / automation_runs.actor_user_id — ключ personal history.
Authenticated PanelUserPrincipal с positive ID/exact raw username/status приходит
в controller/service как Authentication. ID записывается до initial audit save;
actor остаётся display snapshot, не lookup/fallback. List использует automation
key + actor user ID, top 20 started_at DESC/id DESC. Detail дополнительно требует
run ID; child items загружаются только после собственного parent, иначе 404.
Rename/reused names и case-fold соседи не передают ownership. Client actor/ID не
определяет владельца. PAGE_DIALOGS, session refresh/revoke, CSRF и JSON/UI сохранены.
Repository read errors не дают name fallback; новых global/business/admin grants нет.

Source migration V83 добавляет nullable BIGINT с positive-or-NULL check и индекс.
NULL legacy rows не видны в personal API, без auto-backfill по actor; новый код
пишет bound ID. FK/cascade к users нет, чтобы audit переживал удаление аккаунта.
Старые migrations/index сохранены. V83 нужно отдельно проверить на PostgreSQL
и применить до rollout; текущий source step не выполняет migration/deploy.
Mixed old/new writers и полный fleet не считаются принятыми. Historical S2B не
возвращается. R189 serialized-session/fleet compatibility остаётся применимой.

Проверены real security + isolated stateful repository/JDBC и Spring Data parser.
AutomationRunOwnershipPostgresqlTest (8 cases) только скомпилирован: настоящий
JPA/SQL/Flyway upgrade acceptance, R195/R196 locks/rollback и live checks открыты.
Это не атомарное завершение всех in-flight запросов после account revoke.
