# 2026-11-13 15:27 MSK — Конфигурация и диагностика назначенных вопросников

Задача: 01-289. Baseline HEAD/origin/main:
`da33156190caa37cfe5aaaa062f5810ae5703765`.

Промт пользователя:

```text
не пойму в чём проблема. вопросники назначены, но при попытке написать например боту @tst_int_SV_bot , возвращает:
[Пт.13.11.2026 11:58] test_its: Вопросник недоступен или изменился. Для нового выбора отправьте /start.
[Пт.13.11.2026 12:00] Vladimir: /start
[Пт.13.11.2026 12:00] test_its: Вопросник недоступен или изменился. Для нового выбора отправьте /start.

при обращении к боту @Flisoff_bot ловлю:
[Пт.13.11.2026 14:59] Vladimir: эй
[Пт.13.11.2026 14:59] Flisoff: Вопросник недоступен или изменился. Для нового выбора отправьте /start.
[Пт.13.11.2026 14:59] Vladimir: /start

при отправке боту мах, возвращается то же самое
```

Значимые уточнения: «На другом сервере в Docker»; перечислены три panel-сервиса
с tag `iguana-panel:local`. Успешный SQL показал сохранённые active
EXPLICIT_SELECTION business/template bindings канала1, channel templates у2/3.
`MULTIBUSINESS_*` пусто; `SPRING_APPLICATION_JSON отсутствует`.
Live GENERAL bindings/hashes, effective Spring flags и image IDs не получены.

Изменены области: `.env.example`, production Compose, panel application.yml/
MultibusinessFeatureFlagsTest; bot-core runtime client/SharedQuestionnaireClientTest;
Telegram handler/selection tests, MAX controller/selection tests, VK handler;
docs/environment_variables.md, новый runbook questionnaire-runtime-recovery.md;
task-list и новая task-details/01-289.md. Точный список16 файлов — в карточке.

Добавлена постоянная передача8 APP-prefixed rollout inputs из .env в три panel
сервиса с canonical mapping и прежними false defaults. Source сам flags не
включает. Для отказов API добавлены безопасные QNR-коды и WARN; stale selection
сохраняет restart flow, auth/scope/hash/writer checks и запрет fallback сохранены.
MAX /start и /cancel исправлены для старого legacy draft, который застревал на
runtime precheck до обработки команды.

Проверки:493 bot cases/6 suites и9 panel cases/2 suites GREEN; Compose48 assertions
для8 flags/3 сервисов/true+false; compile и diff check GREEN. Initial test import
ошибка исправлена; финальный MAX143 cases GREEN. Полный backend suite и live
Docker/provider acceptance не выполнялись; Docker engine здесь недоступен.
Evidence — task-details/01-289.md.

Изменения проверены в detached worktree и перенесены exact baseline/blob/source
SHA/scope guarded copy в основной checkout. Staging/commit/push/deployment нет.
Рабочие настройки, назначения, сессии и процессы не менялись, миграций/backfill нет.
Для восстановления сервера нужны явные flags из runbook, новый общий image и
пересоздание panel-web/ops-worker/bot-runner, затем functional acceptance.
