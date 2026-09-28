# 2026-09-28 — 01-275 S6: PostgreSQL timestamp binding for Channel save

## Контекст

После R19 ручная приёмка подтвердила sidebar и визуальный MAX inbound, но сохранение любого бота из channel editor возвращало внутреннюю ошибку сервера.

R20 read-only диагностика зафиксировала production stack trace: `ChannelTransportService.updateChannel` падает на `ChannelRepository.save`, потому что `channels.created_at` имеет тип PostgreSQL `timestamp with time zone`, а explicit `LenientOffsetDateTimeConverter<OffsetDateTime,String>` передаёт character varying.

## Изменения

- `Channel.createdAt` и `Channel.updatedAt` переведены на native Hibernate/JDBC `OffsetDateTime` binding.
- Явные column names `created_at` / `updated_at` сохранены.
- Остальные legacy lenient temporal mappings не меняются.
- Добавлен `ChannelPostgresMappingContractTest`, фиксирующий отсутствие `@Convert` на этих двух полях.
- DB migration не требуется.

## Validation target

- isolated `spring-panel` compile;
- `ChannelPostgresMappingContractTest`;
- `ChannelApiControllerWebMvcTest`;
- `git -c core.safecrlf=false diff --check`.
