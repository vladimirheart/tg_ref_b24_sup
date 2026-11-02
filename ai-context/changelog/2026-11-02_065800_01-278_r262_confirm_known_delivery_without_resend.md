# 01-278 R262: подтверждение известного успеха без повторного provider I/O

Время: 2026-11-02 06:58 MSK.

Промпты пользователя:

- «делай».
- «продолжи».

## Изменения

- Baseline clean HEAD/origin/main/actual remote
  a3e9348824cafd2f56d75905e3aa881c6ab5d416, parent5731c6d48 и scope4 R261 проверены.
- Единственный production class change — OutboundTransportDeliveryLedgerService.
  markDelivered ограниченно повторяет только запись уже известного provider true:
  максимум3 независимые REQUIRES_NEW/READ_COMMITTED/timeout10s transactions при
  DataAccessException/TransactionException. SDK, авторизация и захват не повторяются;
  interrupt/ошибка состояния/программирования не дают цикл retry.
- Потерянный ответ уже завершённого commit распознаётся по committed delivered
  с delivered_at; проверка read-only, timestamps/metadata не переписываются.
  sending → delivered остаётся единственным новым переходом. Нет разрешения
  unknown outcome, uncertain, stale processing/sending или старого failed.
- Добавлен FeedbackResultConfirmationTest:32 real PostgreSQL executions,
 3 маршрута,1/2 временных сбоя, потеря commit acknowledgement после настоящего
  commit, исчерпание3 попыток, non-persistence error, false/exception провайдера,
  raw Unicode/bigint, replay на новом ledger instance, запрет недоказанных статусов,
  отсутствующая запись и идемпотентность delivered. Provider/admission ровно1.
- Frozen test RED на baseline32/3 assertion failures/10 ожидаемых runtime errors/
  0skips; без изменения теста GREEN32/0/0. GREEN625 executions/12 suites:
  419 core (387 прежних +32 новых),206 PG/Rabbit/HTTP. Source/replay независимы,
  повторный прогон не суммируется.202 inputs frozen/208 core classes проверены,
  206 unrelated exact.1435 panel classes reused с provenance; новый reactor core
  использован в acceptance, не старый installed dependency JAR.
- Обновлены task detail, S0 и BOT_RUNTIME_CONTRACT: новый source-only scope,
  сохранён R261 runtime checkpoint и точная граница оставшейся reconciliation.
- Изолированные PG192/Rabbit256MiB/1CPU с loopback/fake credentials; Maven96MiB/
  test JVM128MiB. Собственные контейнеры/одноразовые данные удалены.

## Границы

Это восстановление записи известного успеха в живом dispatcher, не receipt
reconciliation неизвестного исхода после потери памяти и не exactly-once.
Не реализованы live SDK/customer rating callback, in-flight full process restart,
оставшиеся recipient/GENERAL/questionnaire/task/project/system/AI policies и S6.
Новый image не собирался и не выкладывался: runtime R261 (runnerR260/image904f,
web/workerR257/image90cd) прежний. Рабочие18 containers/config/БД/flags/ledger17
delivered и readinessUP сохранены; прежний healthDOWN/inboundDLQ1 не исправлялись.
Нет schema/DTO/API changes, migrations/backfill/flags, внешних provider messages,
staging/commit/push. S2B снятR190, задача🟡.

## Файлы

- java-bot/bot-core/src/main/java/com/example/supportbot/service/OutboundTransportDeliveryLedgerService.java
- java-bot/bot-core/src/test/java/com/example/supportbot/service/FeedbackResultConfirmationTest.java
- ai-context/tasks/task-details/01-278.md
- ai-context/rules/03-multibusiness-s0-contract.md
- docs/BOT_RUNTIME_CONTRACT.md
- Эта append-only запись.

Локальные operators/proofs: .tmp/01-278-r262-evidence/ (не входят в git).
