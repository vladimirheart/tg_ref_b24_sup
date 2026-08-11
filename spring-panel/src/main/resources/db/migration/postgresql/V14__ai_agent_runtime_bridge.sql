CREATE TABLE IF NOT EXISTS ticket_ai_agent_state (
    ticket_id VARCHAR(255) PRIMARY KEY,
    is_processing INTEGER NOT NULL DEFAULT 0,
    mode TEXT,
    last_action TEXT,
    last_error TEXT,
    last_source TEXT,
    last_score DOUBLE PRECISION,
    last_suggested_reply TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decision_type TEXT,
    decision_reason TEXT,
    source_hits TEXT
);

CREATE INDEX IF NOT EXISTS idx_ticket_ai_agent_state_processing
    ON ticket_ai_agent_state(is_processing);

CREATE TABLE IF NOT EXISTS ticket_ai_agent_dialog_control (
    ticket_id TEXT PRIMARY KEY,
    ai_disabled INTEGER NOT NULL DEFAULT 0,
    auto_reply_blocked INTEGER NOT NULL DEFAULT 0,
    reason TEXT,
    updated_by TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_agent_solution_memory (
    query_key TEXT PRIMARY KEY,
    query_text TEXT NOT NULL,
    solution_text TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'operator',
    times_used INTEGER NOT NULL DEFAULT 0,
    times_confirmed INTEGER NOT NULL DEFAULT 0,
    times_corrected INTEGER NOT NULL DEFAULT 0,
    review_required INTEGER NOT NULL DEFAULT 0,
    pending_solution_text TEXT,
    last_operator TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_ticket_id TEXT,
    last_client_message TEXT,
    status TEXT DEFAULT 'draft',
    trust_level TEXT DEFAULT 'low',
    intent_key TEXT,
    slot_signature TEXT,
    scope_channel TEXT,
    scope_business TEXT,
    scope_location TEXT,
    safety_level TEXT DEFAULT 'normal',
    source_type TEXT DEFAULT 'operator',
    last_verified_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    verified_by TEXT,
    slots_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_solution_memory_review
    ON ai_agent_solution_memory(review_required, updated_at);

CREATE INDEX IF NOT EXISTS idx_ai_agent_solution_memory_ticket
    ON ai_agent_solution_memory(last_ticket_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_ai_agent_solution_memory_status_trust
    ON ai_agent_solution_memory(status, trust_level, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_agent_solution_memory_scope
    ON ai_agent_solution_memory(scope_channel, scope_business, scope_location);

CREATE INDEX IF NOT EXISTS idx_ai_agent_solution_memory_intent_slot
    ON ai_agent_solution_memory(intent_key, slot_signature, status, trust_level, updated_at DESC);

CREATE TABLE IF NOT EXISTS ai_agent_solution_memory_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    query_key TEXT NOT NULL,
    changed_by TEXT,
    change_source TEXT NOT NULL DEFAULT 'manual',
    change_action TEXT NOT NULL DEFAULT 'update',
    old_query_text TEXT,
    old_solution_text TEXT,
    old_review_required INTEGER NOT NULL DEFAULT 0,
    new_query_text TEXT,
    new_solution_text TEXT,
    new_review_required INTEGER NOT NULL DEFAULT 0,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_solution_memory_history_key_created
    ON ai_agent_solution_memory_history(query_key, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_agent_suggestion_feedback (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id TEXT NOT NULL,
    decision TEXT NOT NULL,
    source TEXT,
    title TEXT,
    snippet TEXT,
    suggested_reply TEXT,
    actor TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_suggestion_feedback_ticket_created
    ON ai_agent_suggestion_feedback(ticket_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_agent_event_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id TEXT,
    event_type TEXT NOT NULL,
    actor TEXT,
    decision_type TEXT,
    decision_reason TEXT,
    source TEXT,
    score DOUBLE PRECISION,
    detail TEXT,
    payload_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    policy_stage TEXT,
    policy_outcome TEXT,
    intent_key TEXT,
    sensitive_topic INTEGER DEFAULT 0,
    top_candidate_trust TEXT,
    top_candidate_source_type TEXT
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_event_log_created
    ON ai_agent_event_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_agent_event_log_ticket_created
    ON ai_agent_event_log(ticket_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_agent_event_log_type_created
    ON ai_agent_event_log(event_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_agent_event_log_policy
    ON ai_agent_event_log(policy_stage, policy_outcome, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_agent_intent_policy (
    intent_key TEXT PRIMARY KEY,
    auto_reply_allowed INTEGER NOT NULL DEFAULT 0,
    assist_only INTEGER NOT NULL DEFAULT 1,
    requires_operator INTEGER NOT NULL DEFAULT 0,
    safety_level TEXT NOT NULL DEFAULT 'normal',
    notes TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_agent_sensitive_patterns (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
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

CREATE TABLE IF NOT EXISTS ai_agent_intent_catalog (
    intent_key TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    pattern_hints TEXT NOT NULL DEFAULT '',
    slot_schema_json TEXT NOT NULL DEFAULT '{}',
    enabled INTEGER NOT NULL DEFAULT 1,
    priority INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_intent_catalog_enabled_priority
    ON ai_agent_intent_catalog(enabled, priority, intent_key);

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'general_support', 'Общий вопрос', 'Общее обращение без явного бизнес-сценария.', 'вопрос,подскажите,помогите,как,что', '{"required":[],"allowed":["business","location","channel","order_id","delivery_type","allergen","amount","contact_phone"]}', 1, 900
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'general_support');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'order_status', 'Статус заказа', 'Проверка текущего статуса заказа.', 'статус заказа,где заказ,номер заказа,когда привезут', '{"required":["order_id"],"allowed":["business","location","channel","order_id","delivery_type"]}', 1, 120
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'order_status');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'delivery_delay', 'Задержка доставки', 'Жалоба на опоздание доставки.', 'доставка опоздала,задержка доставки,долго везут,не привезли', '{"required":[],"allowed":["business","location","channel","order_id","delivery_type","time_window"]}', 1, 140
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'delivery_delay');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'payment_issue', 'Проблема оплаты', 'Проблемы со списанием, оплатой, чеком.', 'оплата,списали,карта,чек,платеж', '{"required":[],"allowed":["business","location","channel","order_id","amount","payment_method"]}', 1, 80
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'payment_issue');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'refund_request', 'Запрос возврата', 'Возврат денег, компенсация.', 'возврат,верните деньги,компенсация,refund', '{"required":[],"allowed":["business","location","channel","order_id","amount","payment_method"]}', 1, 60
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'refund_request');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'cancel_order', 'Отмена заказа', 'Отмена уже оформленного заказа.', 'отменить заказ,отмена заказа,cancel', '{"required":[],"allowed":["business","location","channel","order_id"]}', 1, 160
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'cancel_order');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'food_quality', 'Качество блюда', 'Жалобы на качество, вкус и вид блюда.', 'невкусно,качество,холодное,сырое,испорчено', '{"required":[],"allowed":["business","location","channel","order_id","item_name"]}', 1, 150
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'food_quality');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'allergy_question', 'Аллергены и состав', 'Вопросы про аллергены и безопасность блюда.', 'аллерг,состав,глютен,лактоза,орех', '{"required":[],"allowed":["business","location","channel","item_name","allergen"]}', 1, 70
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'allergy_question');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'technical_issue', 'Техническая проблема', 'Проблемы в приложении, на сайте или при оплате.', 'не работает,ошибка,приложение,сайт,баг', '{"required":[],"allowed":["business","location","channel","device","app_version"]}', 1, 200
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'technical_issue');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'loyalty_program', 'Лояльность и бонусы', 'Бонусы, промокоды и программа лояльности.', 'бонус,промокод,скидка,баллы,лояльн', '{"required":[],"allowed":["business","location","channel","promo_code"]}', 1, 220
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'loyalty_program');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'booking_table', 'Бронирование', 'Вопросы и запросы по бронированию.', 'бронь,забронировать,столик,резерв', '{"required":[],"allowed":["business","location","channel","booking_time","guests_count"]}', 1, 210
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'booking_table');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'operator_request', 'Запрос оператора', 'Клиент просит подключить человека.', 'оператор,человек,менеджер,живой', '{"required":[],"allowed":["business","location","channel","order_id"]}', 1, 50
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'operator_request');

INSERT INTO ai_agent_intent_policy(intent_key, auto_reply_allowed, assist_only, requires_operator, safety_level, notes)
SELECT 'general_support', 0, 1, 0, 'normal', 'Базовый режим для общих вопросов'
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_policy WHERE intent_key = 'general_support');

INSERT INTO ai_agent_intent_policy(intent_key, auto_reply_allowed, assist_only, requires_operator, safety_level, notes)
SELECT 'order_status', 1, 0, 0, 'normal', 'Можно auto-reply при достаточном evidence'
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_policy WHERE intent_key = 'order_status');

INSERT INTO ai_agent_intent_policy(intent_key, auto_reply_allowed, assist_only, requires_operator, safety_level, notes)
SELECT 'delivery_delay', 0, 1, 0, 'normal', 'Требует проверки SLA и контекста'
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_policy WHERE intent_key = 'delivery_delay');

INSERT INTO ai_agent_intent_policy(intent_key, auto_reply_allowed, assist_only, requires_operator, safety_level, notes)
SELECT 'payment_issue', 0, 0, 1, 'high_risk', 'Финансовый риск, обязательный оператор'
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_policy WHERE intent_key = 'payment_issue');

INSERT INTO ai_agent_intent_policy(intent_key, auto_reply_allowed, assist_only, requires_operator, safety_level, notes)
SELECT 'refund_request', 0, 0, 1, 'high_risk', 'Возвраты только через оператора'
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_policy WHERE intent_key = 'refund_request');

INSERT INTO ai_agent_intent_policy(intent_key, auto_reply_allowed, assist_only, requires_operator, safety_level, notes)
SELECT 'allergy_question', 0, 0, 1, 'high_risk', 'Пищевая безопасность и аллергены'
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_policy WHERE intent_key = 'allergy_question');

INSERT INTO ai_agent_intent_policy(intent_key, auto_reply_allowed, assist_only, requires_operator, safety_level, notes)
SELECT 'operator_request', 0, 0, 1, 'normal', 'Явный запрос человека'
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_policy WHERE intent_key = 'operator_request');

CREATE TABLE IF NOT EXISTS ai_agent_knowledge_unit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    unit_key TEXT NOT NULL UNIQUE,
    title TEXT,
    body_text TEXT NOT NULL,
    intent_key TEXT,
    slot_signature TEXT,
    business TEXT,
    location TEXT,
    channel TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    source_ref TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_knowledge_unit_lookup
    ON ai_agent_knowledge_unit(status, intent_key, business, location, channel, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_agent_knowledge_unit_slot
    ON ai_agent_knowledge_unit(slot_signature, status);

CREATE TABLE IF NOT EXISTS ai_agent_memory_link (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    query_key TEXT NOT NULL,
    knowledge_unit_id BIGINT NOT NULL REFERENCES ai_agent_knowledge_unit(id) ON DELETE CASCADE,
    link_type TEXT NOT NULL DEFAULT 'supports',
    weight DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(query_key, knowledge_unit_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_memory_link_query
    ON ai_agent_memory_link(query_key, weight DESC);

CREATE INDEX IF NOT EXISTS idx_ai_agent_memory_link_unit
    ON ai_agent_memory_link(knowledge_unit_id);

CREATE TABLE IF NOT EXISTS ai_agent_offline_eval_run (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dataset_version TEXT NOT NULL,
    actor TEXT,
    cases_total INTEGER NOT NULL DEFAULT 0,
    cases_passed INTEGER NOT NULL DEFAULT 0,
    intent_accuracy DOUBLE PRECISION NOT NULL DEFAULT 0,
    policy_accuracy DOUBLE PRECISION NOT NULL DEFAULT 0,
    retrieval_hit_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
    confirmed_reply_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
    details_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_offline_eval_run_created
    ON ai_agent_offline_eval_run(created_at DESC);
