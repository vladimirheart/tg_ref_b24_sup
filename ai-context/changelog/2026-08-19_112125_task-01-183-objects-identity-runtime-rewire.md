# 2026-08-19 11:21:25 - task-01-183 objects identity runtime rewire

- User prompt:
  `бери сразу 2 пункта в работу:
  objects.db всё ещё остаётся реальным live split-контуром, потому что ObjectPassportService работает через objectsDataSource. Это сейчас самый заметный оставшийся storage-хвост внутри spring-panel.
  panel_identity.db пока остаётся отдельным identity-контуром. Это не такой срочный хвост, как objects.db, но до единого production contour это тоже ещё не доведено.`
  `юери следующий пакет.`
  `что в целом осталось по задаче?`
- Scope:
  - убрать `objects.db` из live Spring datasource graph;
  - перевести runtime `ObjectPassportService` на primary PostgreSQL datasource;
  - убрать отдельный `usersDataSource` из runtime graph и оставить `panel_identity.db` только как compatibility path;
  - обновить тесты и документацию.

## Что изменено

- В `UsersSqliteDataSourceConfiguration` удалён отдельный bean
  `usersDataSource`.
- `usersJdbcTemplate` теперь:
  - в external PostgreSQL runtime возвращает primary `JdbcTemplate`;
  - в explicit `sqlite` runtime поднимает локальный compatibility template для
    `panel_identity.db`.
- Из `SecondarySqliteDataSourceConfiguration` удалён live bean
  `objectsDataSource`; класс оставлен только как holder для properties.
- `ObjectPassportService` больше не зависит от отдельного Spring bean
  `objectsDataSource`:
  - в external runtime работает через primary datasource;
  - в compatibility path лениво поднимает локальный SQLite datasource по
    `ObjectsSqliteDataSourceProperties`.
- `DatabaseBootstrapService` больше не получает `objectsDataSource` из Spring и
  сам создаёт SQLite datasource для `objects.db` только во время legacy
  bootstrap.
- Добавлены тесты:
  - `UsersSqliteDataSourceConfigurationTest`
  - `ObjectPassportServiceRuntimeDataSourceTest`
- Обновлены:
  - `docs/database_distribution.md`
  - `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`
  - `docs/target-production-architecture-plan.md`
  - `ai-context/tasks/task-details/01-183.md`

## Проверка

- `./mvnw clean compile -DskipTests`
  - `BUILD SUCCESS`
- `./mvnw clean test "-Dtest=UsersSqliteDataSourceConfigurationTest,ObjectPassportServiceRuntimeDataSourceTest,BotDatabaseRegistryTest,ClientsServiceTest,BotRuntimeBlacklistServiceTest,ClientProfileApiControllerTest,MonitoringSqliteDataSourceConfigurationTest,LegacySqliteCompatibilityRunnersTest"`
  - `BUILD SUCCESS`
  - `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`
