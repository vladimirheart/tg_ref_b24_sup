# 01-278 R186: точные логины в детальной карточке инцидента

Дата: 2026-10-27, Europe/Moscow.

Промт пользователя: «делай».
Предшествующее предложение: «Далее — аналогичное исправление детальной карточки
и получателей маршрутов». Контекст среды: рабочий экземпляр на другой машине.
Действующий grant: «продолжай. пуш и коммит подтверждаю автоматически до конца суток»
(27 октября, Europe/Moscow, expiry 2026-10-27 21:00 UTC).

R186 продолжает R185 в **детальной карточке** инцидента: сохранение owner,
добавление/удаление watcher и добавление singular user route передают точные
непустые ключи без trim и потери CR/LF. Trim проверяет только полностью blank.
Owner и watcher input — scalar hidden controls с прежними id; owner после
render присваивается через DOM, а не HTML value attribute. Кнопки удаления
получают точные watcher_identity через DOM attribute после HTML parsing;
DELETE использует прежний однократный encodeURIComponent, без второго decode
или подстановки похожего username. Подписи остаются экранированным plain text.

Существующий incident-workbench-ui создаёт user/users pickers. Только scalar
user picker и исходный incidentRouteTarget при type=user используют hidden
input, сохраняя CR/LF по всей цепочке до POST. Users остаётся legacy CSV string;
webhook/department/all_operators, note trim и прежний сброс target/обоих pickers
при смене типа не меняются. Пустой выбор авторитетен, старое значение не
возвращается. Общий employee-picker.js, каталог/права, native select/datalist,
backend permissions/eligibility и прежний server case folding не менялись.
Новых grants, JSON-формата scalar полей или свободного создания аккаунтов нет.

Существующий механизм draft сохраняет точный owner после watcher/route actions
и переключения карточек. Focus key узнаёт исходное поле по picker wrapper;
после refresh фокус возвращается на trigger, после удаления watcher — на
доступный trigger добавления наблюдателя. Ошибка API сохраняет текущий выбор
и позволяет явный retry; отсутствие ключа/ошибка directory не заменяет owner.
Оба изменённых JS получили согласованные cache versions; R13 test сохраняет
строгий expected workbench tag и дополнительно проверяет tag UI decorator.

Проверка: 86 новых real DOM browser cases GREEN на настоящем page body, общем
picker, UI decorator, descriptions и workbench. Fixture-only fetch, вся сеть
заблокирована. Те же frozen test bytes на baseline: 40 failures / 46 passing
controls / 0 errors; в R186 fixtures после RED не менялись. 13 вариантов ключа:
пробелы, TAB, LF/CR и внутренний CRLF, comma/semicolon, long string ID >2^53,
mixed case, markup и URI symbols. Проверены captured PATCH/POST/DELETE, CSRF,
draft/focus, clear, keyboard, missing/failing directory, backend rejection/retry,
старый route type switch, non-user/CSV и native scoped catalog. Дополнительно
29 R185 create + 36 assignment + 52 notification cases GREEN: всего 203.
Node: 40 scripts / 110 checks GREEN, skips/cancelled=0. Полный 41-script run:
111 checks, один прежний R53 notes fixture failure; отдельно воспроизведён и
связан с неизменёнными normalized inputs baseline b92c1aa26. Это известный долг,
не полный GREEN Node suite. Java/Docker/DB/live HTTP/provider tests не запускались;
browser fixture DELETE не является приёмкой серверного URL decoding/ingress.

Baseline R186: clean main, HEAD/origin/main/actual remote
3e5ceba37efe50e67571c523151dfca5c8b989e2. R185 проверен по parent/subject/exact
8 files/normalized content SHA. Detached sandbox .tmp/01-278-r186. Source/test
checkout: C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочие проект и Docker — на
другой машине/в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup.
Checkpoint panel-web R184 выше и пользовательский start/stop одного бота
сохраняются как ранее подтверждённые факты. Рабочие revision/config/flags/
worker/bot fleet не проверялись и не менялись; R185/R186 здесь не развёрнуты.
Нет restart/deploy/migration/backfill или внешних отправок. Grant commit/push
до 2026-10-27 21:00 UTC сохраняется; source apply и Git publisher разделены,
фактические состояния и exact content фиксируются в ignored r186-result.json.

Закрыт описанный UI identity debt owner/watchers/singular user route в карточке,
а не весь incident/upstream/security scope. Дальше: upstream account identity,
остальные legacy/global pickers/permissions, attribution/global/system policy,
решение S2B, сквозная изоляция S3–S5, worker/fleet compatibility и rollout S6.
Ограниченная рабочая приёмка R184 не закрывает эти gates. Общий статус — 🟡.


Scope: 2 production JS (workbench и UI decorator), incident template,
cache contract test R13, новый tools/incident-detail-identity-qa.cjs,
task details, S0 contract, правило выбора сотрудников и этот новый append-only
changelog: 9 files. Старые changelogs, общий picker JS, Java/CSS/SQL и runtime
не менялись. QA: Node 22+, BROWSER_BINARY при необходимости; отдельный headless
profile/CDP, page source без внешних scripts, только fixture API. Non-secret
RED/GREEN source/report/log SHA и apply/publisher evidence — ignored .tmp/r186-*.
