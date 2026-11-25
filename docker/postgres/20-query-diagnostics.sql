-- Infrastructure diagnostics, independent of application Flyway migrations.
-- Runs automatically on a fresh POSTGRES_DB; apply explicitly to existing databases.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
-- Diagnostic metadata is administrative, not part of a restricted child datasource.
-- PostgreSQL's default PUBLIC view/function grants would violate child startup checks.
REVOKE ALL ON TABLE public.pg_stat_statements, public.pg_stat_statements_info FROM PUBLIC;
REVOKE ALL ON FUNCTION public.pg_stat_statements(boolean),
    public.pg_stat_statements_info(),
    public.pg_stat_statements_reset(oid, oid, bigint) FROM PUBLIC;
