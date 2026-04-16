ALTER TABLE ai_agent_solution_memory ADD COLUMN status TEXT DEFAULT 'draft';
ALTER TABLE ai_agent_solution_memory ADD COLUMN trust_level TEXT DEFAULT 'low';
ALTER TABLE ai_agent_solution_memory ADD COLUMN intent_key TEXT;
ALTER TABLE ai_agent_solution_memory ADD COLUMN slot_signature TEXT;
ALTER TABLE ai_agent_solution_memory ADD COLUMN scope_channel TEXT;
ALTER TABLE ai_agent_solution_memory ADD COLUMN scope_business TEXT;
ALTER TABLE ai_agent_solution_memory ADD COLUMN scope_location TEXT;
ALTER TABLE ai_agent_solution_memory ADD COLUMN safety_level TEXT DEFAULT 'normal';
ALTER TABLE ai_agent_solution_memory ADD COLUMN source_type TEXT DEFAULT 'operator';
ALTER TABLE ai_agent_solution_memory ADD COLUMN last_verified_at TEXT;
ALTER TABLE ai_agent_solution_memory ADD COLUMN expires_at TEXT;
ALTER TABLE ai_agent_solution_memory ADD COLUMN verified_by TEXT;

CREATE INDEX IF NOT EXISTS idx_ai_agent_solution_memory_status_trust
    ON ai_agent_solution_memory(status, trust_level, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_agent_solution_memory_scope
    ON ai_agent_solution_memory(scope_channel, scope_business, scope_location);

ALTER TABLE ai_agent_event_log ADD COLUMN policy_stage TEXT;
ALTER TABLE ai_agent_event_log ADD COLUMN policy_outcome TEXT;
ALTER TABLE ai_agent_event_log ADD COLUMN intent_key TEXT;
ALTER TABLE ai_agent_event_log ADD COLUMN sensitive_topic INTEGER DEFAULT 0;
ALTER TABLE ai_agent_event_log ADD COLUMN top_candidate_trust TEXT;
ALTER TABLE ai_agent_event_log ADD COLUMN top_candidate_source_type TEXT;

CREATE INDEX IF NOT EXISTS idx_ai_agent_event_log_policy
    ON ai_agent_event_log(policy_stage, policy_outcome, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_agent_intent_policy (
    intent_key TEXT PRIMARY KEY,
    auto_reply_allowed INTEGER NOT NULL DEFAULT 0,
    assist_only INTEGER NOT NULL DEFAULT 1,
    requires_operator INTEGER NOT NULL DEFAULT 0,
    safety_level TEXT NOT NULL DEFAULT 'normal',
    notes TEXT,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_agent_sensitive_patterns (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pattern TEXT NOT NULL,
    topic_key TEXT NOT NULL,
    severity TEXT NOT NULL,
    action TEXT NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_sensitive_patterns_enabled
    ON ai_agent_sensitive_patterns(enabled, topic_key);

INSERT INTO ai_agent_sensitive_patterns(pattern, topic_key, severity, action, enabled)
SELECT 'возврат', 'refund_money', 'high', 'escalate_only', 1
WHERE NOT EXISTS (
    SELECT 1 FROM ai_agent_sensitive_patterns WHERE lower(pattern) = 'возврат'
);

INSERT INTO ai_agent_sensitive_patterns(pattern, topic_key, severity, action, enabled)
SELECT 'refund', 'refund_money', 'high', 'escalate_only', 1
WHERE NOT EXISTS (
    SELECT 1 FROM ai_agent_sensitive_patterns WHERE lower(pattern) = 'refund'
);

INSERT INTO ai_agent_sensitive_patterns(pattern, topic_key, severity, action, enabled)
SELECT 'персональные данные', 'personal_data', 'high', 'escalate_only', 1
WHERE NOT EXISTS (
    SELECT 1 FROM ai_agent_sensitive_patterns WHERE lower(pattern) = 'персональные данные'
);

INSERT INTO ai_agent_sensitive_patterns(pattern, topic_key, severity, action, enabled)
SELECT 'аллерг', 'food_safety', 'high', 'escalate_only', 1
WHERE NOT EXISTS (
    SELECT 1 FROM ai_agent_sensitive_patterns WHERE lower(pattern) = 'аллерг'
);

INSERT INTO ai_agent_sensitive_patterns(pattern, topic_key, severity, action, enabled)
SELECT 'доставка опоздала', 'delivery_dispute', 'medium', 'assist_only', 1
WHERE NOT EXISTS (
    SELECT 1 FROM ai_agent_sensitive_patterns WHERE lower(pattern) = 'доставка опоздала'
);

