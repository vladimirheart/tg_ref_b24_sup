# 01-278 R255: effective credentials прямых отправок панели

Дата: 2026-11-01, Europe/Moscow.
Промпт пользователя: «давай дальше».

## Изменения

- ChannelTransportService diagnostic/configured Telegram send и recipient preview
  используют общий BotChannelCredentialResolver вместо channels.token.
  Broken explicit binding не даёт fallback, active canonical channel обязателен.
- Повторная проверка credential snapshot/captured channel ID после network setup
  блокирует проверенные late revoke/rotate/rebind/local disable/ID changes.
  Ошибки каталога deny; новый send log не раскрывает exception text/token URL.
- Unknown/null/empty configured target не становится support chat.
  Provider failure не считается успехом и не запускает retry.
- Удаление credentials не очищает channel references и не оживляет старый inline
  token. Новый ID учитывает catalog и все channel refs, включая inactive; BIGINT
  exact, Long.MAX_VALUE409 до write. Concurrent JSON writers не сериализованы.
- Добавлены3 новых test suites/157 cases. Final GREEN598/12 (441 прежний test),
  no failures/errors/skips. Sequential RED132/92 baseline,20/20 intermediate
  late gate,5/4 intermediate ID allocator. Frozen source/bytecode; старые9 suites
  не редактировались, включая channel authorities/page/CSRF и sender/HMAC/launcher.
- Incremental compile1 main source/1433 classes:3 main family changed/
  1430 unrelated exact. Native repo wrapper offline, heaps96/128MiB.
  Tested class archive и input/frozen proofs сохранены; не full web build/release.
- Обновлены task/S0/BOT_RUNTIME_CONTRACT и фактический остаток. R253 rollout и
  R254 diagnostic acceptance сохранены, задача🟡, S2B снятR190.

## Границы

Provider/network/repository/catalog doubles, не live PostgreSQL/provider delivery.
Captured channel object + fresh shared catalog не являются persisted DB lease или
atomic provider authorization. External-chat participant policy, прочие direct/
metadata/getMe paths, strict fresh channel/recipient snapshots и остальные
GENERAL/AI/policies/fleet/flags/S6 остаются. Продуктовый пакет не выполняет deploy,
миграции/backfill/flags/provider messages и не меняет внешние настройки.

## Scope и evidence

1 main file ChannelTransportService.java;3 new tests:
ChannelTransportCredentialBoundaryTest, ChannelTransportLateCredentialChangeTest,
ChannelCredentialIdAllocationTest; docs/BOT_RUNTIME_CONTRACT.md,
ai-context/tasks/task-details/01-278.md,
ai-context/rules/03-multibusiness-s0-contract.md и этот changelog — всего8 files.
Evidence .tmp/01-278-r255-evidence/; exact baselinea379ac13a/HEAD/origin/remote,
detached source/replay, input/blob/scope/diff guards до apply.
Рабочий R252 image d9c597493338 и rollback/backup R253 не обновлялись.
No stage/commit/push/deploy; старые changelog не переписывались.
