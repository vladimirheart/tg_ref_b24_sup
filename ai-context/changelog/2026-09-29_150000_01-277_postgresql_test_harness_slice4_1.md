# 01-277 — PostgreSQL-only migration, S4.1

S4.1 introduces the reusable PostgreSQL integration-test harness and migrates the seven Spring integration fixtures deferred by S3 from SQLite-specific test bootstrap to the canonical PostgreSQL Flyway chain.

Scope is test infrastructure/tests plus task documentation only. Production runtime is unchanged. Maven validation may create an ephemeral PostgreSQL Testcontainers container; it does not mutate the production database or production containers.


## S4.1 continuation: PostgreSQL fixture portability

The PostgreSQL harness restores the non-database isolation previously carried by the SQLite DynamicPropertySource: temporary shared-config/storage roots, safe test-only security/bootstrap values, and disabled local bot auto-start. PostgreSQL-specific fixture semantics now use native booleans and OVERRIDING SYSTEM VALUE for explicit GENERATED ALWAYS identity inserts. Docker API compatibility remains confined to test resources.
