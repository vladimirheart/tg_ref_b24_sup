CREATE TABLE IF NOT EXISTS public_ingress_monitors (
    id BIGSERIAL PRIMARY KEY,
    monitor_name TEXT NOT NULL,
    endpoint_url TEXT NOT NULL,
    scheme TEXT NOT NULL,
    host TEXT NOT NULL,
    port INTEGER NOT NULL,
    expected_http_status INTEGER,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_status TEXT,
    last_summary TEXT,
    last_error_message TEXT,
    last_dns_resolved_at TIMESTAMP WITH TIME ZONE,
    last_dns_addresses TEXT,
    last_http_status INTEGER,
    last_http_duration_ms BIGINT,
    last_http_checked_at TIMESTAMP WITH TIME ZONE,
    last_tls_checked_at TIMESTAMP WITH TIME ZONE,
    last_tls_expires_at TIMESTAMP WITH TIME ZONE,
    last_tls_days_left INTEGER,
    last_checked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_public_ingress_monitors_name
    ON public_ingress_monitors (monitor_name);

CREATE UNIQUE INDEX IF NOT EXISTS idx_public_ingress_monitors_endpoint
    ON public_ingress_monitors (endpoint_url);

CREATE INDEX IF NOT EXISTS idx_public_ingress_monitors_enabled
    ON public_ingress_monitors (enabled);
