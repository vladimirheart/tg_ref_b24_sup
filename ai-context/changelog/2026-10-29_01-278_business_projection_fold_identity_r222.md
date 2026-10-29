# 01-278 R222 — привязка BUSINESS-проекций к аккаунту

Время: 2026-10-29T07:11:09.754Z (UTC; Europe/Moscow +03:00).
Baseline 1d7f1feaa88da2f328e48454601d6b51796926e7, parent 2cf96266decdbe836031b1b33d416b0ab8da1adc.
HEAD/origin/main/actual remote совпали; subject и 7 previous paths/blobs/content
R221 проверены, новых commits при старте нет, прежние изменения сохранены.

Промт пользователя:

> делай

Контекст: продолжение предложенного пакета четырёх projection read services
после R221. Ранее commit/push разрешены до конца29 октября Europe/Moscow.

## Изменение

По одной SQL-проверке lower(reader.username)=n.user_identity добавлено в
authorized_owner AssignmentRead/CurrentAssignmentRead/ParticipantRead/WorkRead.
Прежние ID/exact raw-name/status checks теперь связаны с именем, по которому
shared READ SQL ищет права. При Java/PG lower U+0130/i+U+0307 соседний аккаунт
другого ID больше не предоставляет READ. Fold mismatch закрывает чтение;
новых правил нормализации/валидации логинов нет.

Охвачены 5 API: assignment history, current assignment, participant roster/
history и work history. READ/ALL_BUSINESSES для VIEWER/OPERATOR/MANAGER/ADMIN,
full owner/public uniqueness, MANUAL/SLA provenance/current diagnostics,
roster bound/eligibility, cursors и ReadUnavailable/503 сохранены. Это
statement-time проверка без response-wide/global lock. API/UI/schema/flags/
dependencies не менялись. Scope8: 4 main, 1 new test, task/S0/этот changelog.

## Проверки

347 tests /10 suites GREEN одним полным selected run, failures/errors/skips0.
150 новых SQL frozen до product edits: baseline15 assertion failures/0 errors,
controls135 GREEN; те же source bytes/3 compiled class hashes/150 case names
после исправления GREEN. Unicode neighbour active/disabled/blocked/no-rights,
отказ без соседа, exact raw names, READ/ALL grants, BIGINT reader/owner IDs,
immutable private snapshots, stale/reused accounts и read-only payload/denial.
20 SQL-boundary cases: поздний status/grant revoke и folded neighbour; внешняя
post-query revocation гарантия не заявляется.

Повторены прежние SQL Assignment29/Current38/Participant35/Work32,
MVC14/9/14/14 и real-login CurrentIdentity12. Старые tests/assertions/fixtures
не менялись. R221774/6 — историческая сводная проверка, за новый run не выдаётся.
1357 baseline Java inputs совпали с проверенным R221 cache; новый test compiled
v1, 4 main compiled v2, Maven wrapper/JDK17. Git replay/inverse, свежие XML,
source/class/XML/log SHA и exact scope проверены до main apply.

Native PostgreSQL16.15/loopback54167/fresh isolated schemas, explicit ignored
R200 supplier с SHA/version/exact directory guards. Tracked JDBC helper/base
прежние (Testcontainers); новый class имеет собственную autocommit connection/
schema с полным row reset. Connection и owned server закрыты, artifacts сохранены.
Docker/Testcontainers не запускались; 83 migration sources прежние, SQL migrations
только test schemas. JS не rerun. Selected source validation, не вся задача или
runtime/provider acceptance.

## Среда и остаток

Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Полные R184/R198/R204/
R207/R216 runtime/rollback/V83 blocks сохранены. Последний документированный R216:
webR215/workerR198/foundationOFF/readinessUP/health503 из-за прежнего inboundDLQ1.
Deploy/restart/working DB/flags/provider calls/queue здесь не затрагивались.
R217–R222 отсутствуют в документированном R215 image: требуется новый panel-web
image, новых migrations нет, V83 не повторять; rollback/backup сохранены.

Четыре projection readers из плана R221 закрыты. Остаются сквозные child/media/
edit/feedback/GENERAL и notification/realtime границы, task/project/system
policies, AI privacy/context/retention/delivery, channel/bot/worker fleet, flags
и S6 acceptance. Перечень не утверждает новые дефекты/полноту аудита consumers.
Historical S2B снятR190, Task🟡. Source apply и Git publisher разделены; разрешение
commit/push действует до конца29 октября. Git-факты: .tmp/r222-result.json и
r222-final.json; проверки: r222-baseline-red/java-green/validation.json и logs.
