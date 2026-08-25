# 2026-08-25 16:09:33 — credential-rotation-network-secrets-and-metadata-import

## Пользовательский промпт

> хорошо. бери следующую задачу - 01-205

## Что сделано

- Credential rotation registry расширен на network-route secrets:
  - `integration_network.project`;
  - `integration_network.bots`;
  - `integration_network_profiles`;
  - `channels.deliverySettings.network_route`.
- Для route contour теперь отслеживаются proxy secrets, которые реально участвуют в runtime contract:
  - `proxy.password`;
  - `proxy.token`.
- Добавлен отдельный сервис `CredentialRotationExternalMetadataImportService` с generic `http_json` import-contract для внешних metadata backends.
- Поддержан settings-driven contract для metadata import:
  - `credential_rotation_external_backends`;
  - `credential_rotation_external_links`;
  - optional `override_manual_metadata`.
- Imported metadata применяется к registry entries без раскрытия raw secret values и не ломает refresh при ошибке внешнего backend: при сбое логируется warning и registry продолжает работать по manual/fallback path.
- Обновлены tests:
  - `CredentialRotationRegistryServiceTest` покрывает network secret discovery и imported metadata application;
  - добавлен `CredentialRotationExternalMetadataImportServiceTest`;
  - повторно проверен API slice `CredentialRotationRegistryApiControllerWebMvcTest`.
- Синхронизирован project task-flow:
  - создан detail-файл `01-205`;
  - задача `01-205` переведена в `🟣` как завершённая AI и ожидающая ручной проверки.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-205.md`
- `spring-panel/src/main/java/com/example/panel/service/CredentialRotationRegistryService.java`
- `spring-panel/src/main/java/com/example/panel/service/CredentialRotationExternalMetadataImportService.java`
- `spring-panel/src/test/java/com/example/panel/service/CredentialRotationRegistryServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/CredentialRotationExternalMetadataImportServiceTest.java`
