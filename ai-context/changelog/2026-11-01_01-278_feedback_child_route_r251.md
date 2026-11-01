# R251 — привязка feedback consumer к configured child route

Время: 2026-11-01 15:37 +03:00.
Задача: 01-278, статус🟡; S2B исключёнR190, S6 не завершён.
Инициирующий пользовательский промпт: «давай дальше».

## Изменения

- `OutboundFeedbackPromptListener`: constructor принимает прежние Rabbit properties
  и фиксирует route/queue для жизни child. До dispatcher проверяются exact canonical
  event platform/channel, received key, configured route/queue. Positive full-width
  channel ID; foreign/missing/wildcard/padded/noncanonical aliases запрещены.
- `FeedbackPromptChildRouteTest`:162 новых listener boundary cases для Telegram/VK/MAX,
  channel42 и BIGINT>2^53; dispatcher double. Constructor bridge только в test для
  baseline old1/new2 dependencies, без изменения assertions/runtime bypass.
- `docs/BOT_RUNTIME_CONTRACT.md`, task detail и S0: выполненное, проверки и оставшиеся
  credential/principal/TOCTOU gates; runtime checkpoint не подменён source work.
- Этот append-only changelog. Scope ровно6 files:1 main/1 new test/4 documents.

## Проверка

Baseline HEAD/origin/main/actual remote978f6995d7ecbdd31f686feafbe027782d22c8f7,
parentb9700927f/commit message/scope8 R250 проверены; исходное дерево чистое.
Unchanged product RED162/150 assertion failures/12 controls/0 errors/skips;
frozen new source/bytecode/cases не менялись после RED. GREEN312/6 suites:
162 новых+150 прежних R250,0 failures/errors/skips. Старые assertions/fixtures
не менялись. Controls exact dispatch/immutable config/exception propagation.
Это не live Rabbit/provider/fleet/credential acceptance и не backend498 rerun.

Repository Maven wrapper/reactor parent+bot-core,137 main/6 test sources,
205 classes:1 listener changed/204 unrelated exact. Helper512MiB/swap512/1.5CPU,
heap160/meta224/code32/stack512k, sequential, без test DB. Classes archive отдельно
распакован/сверен, SHA25696ECD4F0CF06EA8D1D3111CD27E57BCD1455921CDCF41408210CDB6E96E8A6C3.
Это не executable JAR/image/full platform build. Detached sandbox/replay/exact
baseline/blob/content/scope/frozen hashes/diff guards до apply.
Evidence .tmp/01-278-r251-evidence/: RED/GREEN/frozen/input/class/resource proofs,
tested artifact, runtime/sandbox/replay/apply results. No stage/commit/push/deploy.

## Среда и границы

Working18 IDs/images/start/restarts/ENV/mounts/HostConfig/config прежние; web/worker
R246 healthy/restart0/readinessUP. DB84/settings/users/grants/roots/pending/ratings/
outbox fingerprints прежние,126 published/actionable0/active pending0. Migration/
backfill/flags/working queue/provider writes не выполнялись. Rollback tags/backup
R247 сохранены; restore нового dump/actual rollback не выполнялись. Working services,
WSL/VS Code ради ОЗУ не останавливались. DTO/launcher env/bindings/container requeue/
DLQ policy не менялись. Source R248–R251 не развёрнут.

Configured child route admission не подтверждает provider credential ownership и
не вводит per-child authenticated backend principal. При согласованно неверных
launcher route/queue credential gate ещё нужен. API→provider TOCTOU/revoke/expiry,
external service-chat recipient rights/provider retries/exactly-once/uniqueness/
lifecycle/child-rating/media/AI/прочие S3–S6 и operational gates R247 открыты.
Перед будущими новыми ботами сначала нужен panel-web API R249; этот packet не rollout.
