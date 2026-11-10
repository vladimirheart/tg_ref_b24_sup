-- Общие работы: неизменяемый аудит и frozen numeric получатель уведомления.
CREATE TABLE shared_work_access_audit (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 kind TEXT NOT NULL CHECK(kind IN ('TASK','PROJECT','GRANT')),
 object_id BIGINT NOT NULL CHECK(object_id>0),
 action TEXT NOT NULL CHECK(action IN ('CREATED','SYSTEM_CREATED','GRANT_SET','MEMBER_SET','MEMBER_REMOVED','OWNER_TRANSFERRED')),
 actor_user_id BIGINT CHECK(actor_user_id>0),
 actor_name TEXT NOT NULL,
 target_user_id BIGINT CHECK(target_user_id>0),
 before_state JSONB NOT NULL,
 after_state JSONB NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX shared_work_audit_object ON shared_work_access_audit(kind,object_id,id DESC);
CREATE FUNCTION shared_work_audit_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'shared work audit is append-only'; END;
$$;
CREATE TRIGGER shared_work_audit_immutable BEFORE UPDATE OR DELETE ON shared_work_access_audit
 FOR EACH ROW EXECUTE FUNCTION shared_work_audit_immutable();
REVOKE ALL ON shared_work_access_audit FROM PUBLIC;

ALTER TABLE notifications ADD COLUMN shared_work_kind TEXT;
ALTER TABLE notifications ADD COLUMN shared_work_id BIGINT;
ALTER TABLE notifications ADD COLUMN shared_recipient_user_id BIGINT REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE notifications ADD CONSTRAINT notification_shared_work_scope CHECK(
 (shared_work_kind IS NULL AND shared_work_id IS NULL AND shared_recipient_user_id IS NULL)
 OR (shared_work_kind IN ('TASK','PROJECT') AND shared_work_id>0 AND shared_recipient_user_id>0
     AND shared_work_kind IS NOT NULL AND shared_work_id IS NOT NULL AND shared_recipient_user_id IS NOT NULL
     AND ticket_scope_kind IS NULL AND ticket_user_id IS NULL AND ticket_id IS NULL
     AND ticket_public_id IS NULL AND ticket_business_id IS NULL));
CREATE INDEX notification_shared_work_recipient ON notifications(shared_recipient_user_id,created_at DESC,id DESC)
 WHERE shared_work_kind IS NOT NULL;
