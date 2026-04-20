# Sliced controller WebMvc safety net and UI preferences ownership

- добавлены отдельные WebMvc tests для `DialogReadController`, `DialogTriagePreferencesController`, `SettingsParametersController` и `SettingsItEquipmentController`;
- новые tests покрывают базовые read/update contracts, auth-sensitive triage flow и actor propagation в settings equipment endpoints;
- ownership источников UI preferences задокументирован в `docs/UI_PREFERENCES_OWNERSHIP.md`;
- roadmap обновлён: ownership-хвост `Phase 2` закрыт документацией, а `Phase 6` теперь явно учитывает sliced controller WebMvc coverage.
