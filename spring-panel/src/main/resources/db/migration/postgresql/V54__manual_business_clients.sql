-- 01-278: local client identities occupy the negative user_id namespace.
-- No existing clients or tickets are rewritten by this additive migration.
CREATE SEQUENCE manual_client_user_id_seq
    AS BIGINT
    START WITH -9007199254740991
    INCREMENT BY 1
    MINVALUE -9007199254740991
    MAXVALUE -1
    NO CYCLE;

CREATE TABLE manual_business_clients (
    user_id BIGINT PRIMARY KEY DEFAULT nextval('manual_client_user_id_seq'),
    business_id BIGINT NOT NULL REFERENCES businesses(id),
    display_name VARCHAR(200) NOT NULL,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_manual_business_clients_negative_user_id CHECK (user_id < 0),
    CONSTRAINT ck_manual_business_clients_name CHECK (length(btrim(display_name)) > 0)
);

CREATE INDEX idx_manual_business_clients_business
    ON manual_business_clients(business_id, created_at DESC);
