# 01-278 — актуальные права открытых сессий, R189

Промт пользователя: «давай» — продолжение предложенного пункта «обновление и
отзыв прав уже открытых сессий» после R188.

Сохраняются ранее подтверждённые границы: рабочий проект и Docker на другой
машине/в другой сессии; commit/push разрешены до конца 2026-10-27 Moscow,
то есть 21:00 UTC. Это разрешение не включает рабочие deploy/DB/config действия.

## Изменение

Раньше ROLE/PAGE authorities оставались snapshot момента входа. Новый
SessionAuthorityFilter перед защищённым действием читает аккаунт по immutable
numeric user ID и его текущие direct authorities/роль. При изменении набор прав
заменяется в текущем Authentication до method security/controller/service.
Новый SecurityContext явно сохраняется в общий request-attribute + HttpSession
repository для ON_SET_ATTRIBUTE; при одинаковых правах context не заменяется.

UserRepositoryUserDetailsService теперь возвращает serializable PanelUserPrincipal
с ID и точным stored username; новый strict ID read не использует username/trim
и не подавляет ошибки источников прав. Прежний primary login SQL/case folding,
порядок authority merging и login read fallbacks сохранены. Numeric ID и username
проверяются перед использованием прав. Deleted/disabled/blocked/renamed аккаунт
и legacy principal без ID инвалидируют сессию, context и JSESSIONID/remember-me.
Reuse логина другим ID не переносит сессию на новый аккаунт.

Read failure даёт 503 без вызова защищённого действия и без старых прав; сессия
сохраняется для retry. Remember-me тип сохраняется, credentials стираются.
Фильтр только в security chain после remember-me и до anonymous/activity/
authorization. Public MVC/static/error matcher общий с permitAll; прежние public
paths и CSRF сохраняются, public/anonymous не зависят от users store refresh.
Ненастроенный users datasource остаётся прежним fallback; ошибка настроенной
БД не превращается в fallback. Grants/schema/config/данные не менялись.

## Проверки

Новый SessionAuthorityWebMvcTest: 32 MockMvc cases с настоящими SecurityConfig,
DAO/encoder/session/remember-me и ChannelApiController/method security. Только
JDBC mocked; реального datasource подключения нет. Frozen meaningful RED v2
до production edits: 24 failures / 8 passing controls / 0 errors; те же bytes
GREEN в v3. Сценарии login → mock DB change → тот же session охватывают revoke/
grant direct/role/page rights, raw /whoami и persisted context, disabled/blocked/
delete/rename/case rename/reuse, два сеанса, read failure каждого источника и retry,
remembered-vs-full auth, legacy principal, JDK serialization без credential hash,
неизменённый context, public/anonymous/CSRF и свежий remember-me cookie login.

Первый новый fixture v1 не скомпилировался: UserBuilder.build возвращает UserDetails,
а eraseCredentials доступен через CredentialsContainer; исправлен только новый
fixture. До frozen RED также уточнено штатное требование повторного входа для
remember-me при isFullyAuthenticated: redirect, не 403. После v2 новые test bytes
не менялись. В существующий R188 login fixture добавлены только три строки mock
ответа на новый numeric-ID lookup; прежние assertions/SQL count guards сохранены.

Maven wrapper v3: 13 suites / 582 tests, failures/errors/skips=0. Прежние 550
login/channel/credential/profile/permission/activity/UserDetails/encoder cases
GREEN, включая 281 channel security/page и 37 R188 login cases. Java cache R188
использован после normalized SHA сверки всех baseline Java; четыре изменённых/
новых production Java скомпилированы. CSS сохраняет прежние Git blobs и не
входит в scope. Browser/Node/Docker/live DB/HTTP/provider tests не запускались.
MockHttpSession serialization не объявляется приёмкой рабочего JDBC session store.

## Совместимость и состояние

После будущего rollout старым сессиям без ID потребуется повторный вход.
Serialized payload с новым PanelUserPrincipal требует наличия класса во всех
читающих web images; старый image не прочитает новый payload. Mixed web versions
и rollback требуют отдельной session compatibility процедуры. Здесь рабочие
sessions не отзывались/не удалялись, rollout/rollback не запускались.
Следующий запрос проверяется заново; ongoing/async/SSE, cluster-wide bulk deletion,
password-change revocation и расширение token policy не входят в R189.

Baseline: clean main, HEAD/origin/main/actual remote
1ccdfc4c5d0d34f76004d3f47b21f4d20a82c91a. R188 проверен по parent/subject/exact
7 files/normalized content. Detached sandbox .tmp/01-278-r189. Source checkout
C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочие проект и Docker — на другой машине/
в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Source apply и отдельный
expiry-guarded publisher проверяют exact baseline/remote/files/content/test/report
hashes; факты сохраняются в ignored R189 artifacts. Task/S0/rule 08 обновлены,
старые changelog не менялись. R184 runtime checkpoint и пользовательский start/stop
одного бота сохранены точно, включая rollback pointer; новых working probes,
restart/deploy/migration/backfill/config/flags/отправок или изменений данных нет.
R189 здесь не развёрнут. Остальные legacy identity, primary account ambiguity,
S2B, сквозные S3–S5 и worker/fleet/rollout S6 gates открыты. 01-278 остаётся 🟡.
