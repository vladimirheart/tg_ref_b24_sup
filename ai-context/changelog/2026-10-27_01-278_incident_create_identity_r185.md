# 01-278 R185: точные логины при создании инцидента

Дата: 2026-10-27, Europe/Moscow.

Промт пользователя: «давай дальше».
Контекст среды: «рабочий экземпляр проекта на другой машине».
Действующий grant: «продолжай. пуш и коммит подтверждаю автоматически до конца суток»
(27 октября, Europe/Moscow, expiry 2026-10-27 21:00 UTC).
Учтены сообщения «пересобрал и обновил доки» и «проверил на одном боте:
остановка и запуск работают»; checkpoint R184 и его ограниченная приёмка сохранены.

R185 исправляет только выбор ответственного и наблюдателей **при создании**
инцидента. Backend R183 уже сохраняет точные непустые ключи; create UI больше не
обрезает пробелы owner и не разбивает typed watchers по запятым/точкам с запятой.
Owner остаётся scalar string; hidden input сохраняет CR/LF, которые text input
удалял до отправки. Reset явно возвращает первоначальный default owner, поскольку
hidden input отражает присвоенное value в attribute/defaultValue. Watchers —
textarea с явным username-json opt-in общего picker, POST получает string array.
Пустой control означает пустой выбор; malformed JSON/нестроковые/blank элементы
останавливают POST с сообщением и сохраняют черновик. Прежний case folding picker
и backend остаётся. Cache version incidents-workbench обновлена вместе с формой;
существующий R13 contract test ожидает новый tag, сохраняя строгую проверку и
остальные assertions. Несовпадение старого tag отдельно воспроизведено.

Общий employee-picker.js, список /api/panel-users/options, его права, native
select/datalist scope и backend eligibility не менялись. Исторический CSV fallback
для поля без opt-in сохранён. Новые grants, свободный ввод аккаунтов, SQL/schema,
worker/provider behavior не добавляются. Детальная карточка owner, добавление/
удаление watcher и singular user route в UI ещё требуют отдельного исправления
(trim, text input и HTML attribute normalization); этот шаг их не закрывает.

Проверка: реальный headless Edge, template fragment + workbench + общий picker,
fixture fetch и заблокированная сеть. 29 новых сценариев GREEN; те же frozen v3
test bytes на baseline дали 22 failures / 7 passing controls / 0 errors.
В новых fixtures исправлены ожидание завершения actionPending/видимости ошибки
и неоднозначный keyboard search (он штатно выбирает первое совпадение); старые
tests не ослаблялись. Дополнительно 36 assignment + 52 notification browser cases
GREEN: всего 117. Node: 40 scripts / 110 checks GREEN без skips/cancelled. Полный
41-script run: 111 checks, один прежний R53 notes fixture failure; отдельно
повторён и привязан к неизменённым normalized source SHA baseline b92c1aa26.
Это известный долг, а не полный GREEN Node suite. Java/Docker/DB не запускались.

Baseline: clean main, HEAD/origin/main/actual remote
1b796a40af0171f535ee6bc8eb098fbc014fa6d4. Последний metadata commit проверен по
subject/parent/exact files/content; цепочка af5bb3441 → 8310d1f7f подтверждена.
Detached sandbox .tmp/01-278-r185. Source-сессия:
C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр и Docker остаются на
другой машине/в другой сессии, C:\Users\SinicinVV\git_h\tg_ref_b24_sup.
Checkpoint R184 выше и пользовательский start/stop одного бота сохраняются как
подтверждённые ранее факты; новых runtime probes/deploy/restart/миграций/backfill/
изменений flags/config здесь нет. R185 ещё не развёрнут в рабочей среде.
Grant commit/push действует до 2026-10-27 21:00 UTC; source apply и publisher
разделены, состояния фиксируются в ignored r185-result.json после факта.

Дальше: оставшийся incident UI/upstream identity, остальные legacy/global
pickers и permissions; attribution/global/system policy и решение S2B; сквозная
изоляция S3–S5, worker/fleet compatibility и gates рабочего rollout S6.
Ограниченный panel-web rollout R184 и smoke одного бота не закрывают эти gates.
Общий статус 01-278 остаётся 🟡. Исторические исходники/проверки R184 и более
ранних шагов приведены ниже; рабочие факты — в checkpoint над этой сводкой.


Scope: два production files (incidents-workbench.js и incidents/index.html),
новый tools/incident-create-identity-qa.cjs, version assertion R13 test,
task details, S0 contract,
правило выбора сотрудников и этот новый append-only changelog: восемь files.
Исторические changelog, shared picker JS, Java, CSS, рабочие данные не менялись.
Browser QA запускается Node 22+ и BROWSER_BINARY (если Edge/Chrome не найден),
использует отдельный headless profile, CDP и только local fixture fetch.
Non-secret RED/GREEN source/report/log SHA, baseline/file guards и отдельные
apply/publisher results остаются в ignored .tmp/r185-*.
