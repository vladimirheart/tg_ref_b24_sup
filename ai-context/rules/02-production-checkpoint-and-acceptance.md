# Правило: production checkpoint, rollback и functional acceptance

## Статус

Действует для production/deploy/post-deploy работы и для продолжения задачи после сообщения пользователя о фактическом состоянии production.

## Главный принцип

Сообщение пользователя о фактическом состоянии production является operational checkpoint. Следующий AI-агент не должен «додумывать» выполненные действия и не должен повторять deploy/migration/backfill только потому, что они логично следуют из технического плана.

## 1. Что фиксировать из production checkpoint

Если пользователь сообщает состояние после rollout/deploy, агент должен сохранить как минимум применимые факты:

- commit/revision сервиса;
- image/digest/container identity, если указаны;
- состояние связанных сервисов, если они намеренно не менялись;
- rollback tag/revision или другой точный rollback pointer;
- какие DB migrations выполнялись или не выполнялись;
- выполнялся ли backfill;
- затрагивались ли внешние системы (например NetBox) или нет;
- какие проверки уже выполнены;
- какая functional acceptance еще требуется.

Отсутствие действия и отрицательный факт (`migration не выполнялась`, `NetBox не трогали`) так же важны, как выполненное действие.

Типовой checkpoint-паттерн, уже встречавшийся в этом проекте:

- зафиксирована revision сервиса (например `panel-web`) и digest фактического image;
- сохранён точный rollback tag/revision;
- отдельно отмечено, что связанный сервис (например `ops-worker`) остался на прежней revision;
- отдельно отмечено, что DB migration, backfill и NetBox не выполнялись;
- следующий шаг — functional acceptance изменённого behavior, причём для media-изменения проверяется не только отображение, но и целостность media/metadata.

Это пример формы checkpoint, а не указание считать исторические значения текущим production-состоянием.

## 2. Не повторять mutation без явного scope

Если checkpoint говорит, что migration/backfill/external-system mutation не выполнялись, это не приглашение выполнить их автоматически.

До отдельного явного scope запрещено:

- запускать DB migration;
- запускать backfill;
- изменять NetBox или другую external source-of-truth систему;
- повторно deploy'ить уже подтвержденную revision;
- удалять rollback path.

## 3. Сначала acceptance измененного поведения

После production rollout следующая работа начинается с functional acceptance именно того behavior/integrity, которое изменялось.

Примеры категорий acceptance:

- media upload/download/rendering и целостность attachment metadata;
- двусторонняя доставка сообщения;
- duplicate suppression/idempotence;
- persisted state после restart;
- permissions/auth/session boundary;
- UI action -> backend mutation -> persisted result -> reload;
- rollback readiness.

Если change затрагивал не только отображение, но и целостность данных/media, визуальный smoke сам по себе недостаточен.

## 4. Не начинать следующий refactor до понимания acceptance result

Если пользователь передал checklist post-deploy приемки, агент должен сначала помочь пройти этот checklist или разобрать его результаты.

Нельзя автоматически переходить к следующему structural refactor, пока неизвестно, исправлен ли production behavior, ради которого выполнялся rollout.

## 5. Rollback pointer сохраняется до завершения приемки

Пока functional acceptance не завершена:

- rollback revision/tag не удалять и не перезаписывать;
- при следующем изменении clearly distinguish предыдущий known-good rollback и новый rollback point;
- не утверждать, что rollback проверен, если проверялась только его доступность/наличие.

## 6. Deploy отделяется от refactor

Structural refactor, source-layout cleanup, metadata closeout и review tooling не должны сами выполнять production deployment.

`deployment=false` является нормальным ожидаемым состоянием для таких phases.

Deploy выполняется только когда он является явным scope текущего шага и пользователь понимает, какая revision/image идет в production и какой rollback предусмотрен.

## 7. Fresh verification после пользовательского действия

Если пользователь сообщает, что commit/push/deploy выполнен:

- сначала проверить свежую remote/repository/production state доступным authoritative способом;
- не использовать старый SHA из предыдущего сообщения как доказательство нового состояния;
- только после проверки строить следующий operator/acceptance step.

## Практический смысл

Это правило позволяет новому AI-агенту безопасно подхватить работу даже после смены чата: он видит не только «что планировалось», но и точную границу того, что реально уже было сделано в production и что намеренно оставалось нетронутым.
