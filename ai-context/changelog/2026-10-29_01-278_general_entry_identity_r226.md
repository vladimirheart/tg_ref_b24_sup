# 01-278 R226 — GENERAL read/access/page entry по ID сессии

Время: 2026-10-29, 12:59 +03:00; Windows/Docker-сессия тестового экземпляра.
Промт пользователя:

> делай

Контекст: подтверждение следующего пакета GENERAL entry identity после R225.
Это source scope, не новое развёртывание panel-web.

## Фактическое изменение

Свежие HEAD/origin/main/actual remote 5eeb95e1bc35a57220bbb9759ab885d25e1fa8ef,
parent d6273481ea7854e2060441f0ba0d2e6f071d06fa, чистый checkout. Проверены
subject,22 paths и содержимое предыдущего валидированного R225; всё сохранено.

GeneralTicketAccessService.requireAuthenticatedUserId теперь принимает только
Authentication. Нужны authenticated non-anonymous PanelUserPrincipal, positive
ID/exact raw name/valid principal status. Один SQL проверяет собственный ID/name,
enabled и прежние COALESCE(is_blocked,FALSE)/SQL-fold uniqueness. Login policy,
raw names и GENERAL grant SQL не менялись; нет String overload, Java fold/trim,
повторного username lookup и заимствования identity замещающего аккаунта.

GeneralTicketReadController передаёт Authentication во все5 REST reads;
GeneralTicketPageController — в queue и admin page. Все5 admin access APIs
перепроверяют ту же bound identity, в том числе accounts/READ grant/revoke,
ранее полагавшиеся лишь на filter/authority. Всего12 входов. PAGE_DIALOGS,
ROLE_ADMIN+PAGE_SETTINGS, flags, CSRF и READ/OPERATE правила прежние. Entry guard
не требует GENERAL READ: admin без собственного READ управляет чужими grants,
но не получает data bypass. Body readers/writers, media bytes и гранты прежние.

## Валидация и обнаруженный тестовый долг

237 targeted tests/7 suites GREEN, failures/errors/skips0. Новые148 frozen:
SQL34 и real form-login/R189/session/CSRF HTTP114. Baseline на неизменном product
дал60 assertion failures/errors0; после исправления те же3 source hashes,
5 class hashes/148 case names прошли. Проверены raw names/Unicode/BIGINT,
invalid/unbound/anonymous identity, поздние rename/delete+reuse/case/status/
ambiguity после refresh, отсутствие downstream reads/storage/grant writes,
admin без READ, authorities/flags. Прежние89:GENERAL MVC10/read SQL7/media8,
BUSINESS entry SQL41/HTTP23. Три старых fixtures меняют лишь6 inputs+2 imports;
inverse baseline exact, result assertions не менялись. В первой candidate
компиляции исправлена отсутствующая ссылка argThat в адаптированном старом
MVC fixture; frozen tests/assertions не менялись, лог сохранён.

Расширенный прогон779 tests/14 suites НЕ GREEN:89 failures/errors0/skips0,
690 cases прошли. Все89 отдельно воспроизведены на исходном product:
135 cases/4 suites, те же failing cases. GeneralTicketWorkHttpPostgresTest:
86 устаревших unbound sessions, R189 redirect на login. Ещё по1 migration
snapshot failure в GeneralManualTicketCreationPostgresTest,
GeneralTicketDocumentReplyPostgresTest, GeneralTicketReplyChangePostgresTest:
SELECT * включает новые колонки, отсутствующие в старом снимке. Эти4 файла
и assertions не менялись. Это не GREEN общего набора; тестовый долг вынесен
в актуальный остаток задачи, не исправлялся ослаблением проверок.

Полный compile JDK17 main731/test637;1368 Java/83 SQL/1847 inputs совпали,
4 generated CSS EOL-only. Отдельный baseline compile main731/test634,
1365 Java/83 SQL/1844 inputs также проверен. PostgreSQL16/Testcontainers,
fresh schemas; без working datasource. JS/UI/dependencies/schema не менялись.
Fresh logs/XML/source/class/input hashes, exact scope, deterministic replay,
content guards и diff checks сохранены в .tmp/01-278-r226-evidence/.

## Граница и runtime

Scope13:4 main,3 adapted fixtures,3 frozen test/fixture, task/S0/этот changelog.
Гарантия — current account SQL на entry. Не заявляются response-wide lock,
атомарная admin-grant authorization после entry, full GENERAL work/write/SSE
identity, child/media/provenance или delivery acceptance. Они остаются вместе
с policy/AI/fleet/controlled flags/S6 и тестовым долгом. Historical S2B снятR190,
задача🟡. Source apply отдельно от deployment, без staging/commit/push.

RuntimeR223 остаётся webR222/workerR198/foundationOFF. Readiness200UP;
health503DOWN из-за прежнего inbound DLQ1, очередь не consume/ack/purge/requeue.
Миграции/backfill/flags/provider не запускались и не менялись, V83 не повторялась.
Worker/bots/инфраструктура не обновлялись, rollback/backup сохранены.
