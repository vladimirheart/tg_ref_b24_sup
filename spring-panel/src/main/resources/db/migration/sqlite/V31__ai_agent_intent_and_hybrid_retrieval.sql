ALTER TABLE ai_agent_solution_memory ADD COLUMN slots_json TEXT;

CREATE INDEX IF NOT EXISTS idx_ai_agent_solution_memory_intent_slot
    ON ai_agent_solution_memory(intent_key, slot_signature, status, trust_level, updated_at DESC);

CREATE TABLE IF NOT EXISTS ai_agent_intent_catalog (
    intent_key TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    pattern_hints TEXT NOT NULL DEFAULT '',
    slot_schema_json TEXT NOT NULL DEFAULT '{}',
    enabled INTEGER NOT NULL DEFAULT 1,
    priority INTEGER NOT NULL DEFAULT 100,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
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
SELECT 'food_quality', 'Качество блюда', 'Жалобы на качество, вкус, вид блюда.', 'невкусно,качество,холодное,сырое,испорчено', '{"required":[],"allowed":["business","location","channel","order_id","item_name"]}', 1, 150
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'food_quality');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'allergy_question', 'Аллергены и состав', 'Вопросы про аллергены и безопасность блюда.', 'аллерг,состав,глютен,лактоз,орех', '{"required":[],"allowed":["business","location","channel","item_name","allergen"]}', 1, 70
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'allergy_question');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'technical_issue', 'Техническая проблема', 'Проблемы в приложении/сайте/оплате.', 'не работает,ошибка,приложение,сайт,баг', '{"required":[],"allowed":["business","location","channel","device","app_version"]}', 1, 200
WHERE NOT EXISTS (SELECT 1 FROM ai_agent_intent_catalog WHERE intent_key = 'technical_issue');

INSERT INTO ai_agent_intent_catalog(intent_key, title, description, pattern_hints, slot_schema_json, enabled, priority)
SELECT 'loyalty_program', 'Лояльность и бонусы', 'Бонусы, промокоды, программа лояльности.', 'бонус,промокод,скидка,баллы,лояльн', '{"required":[],"allowed":["business","location","channel","promo_code"]}', 1, 220
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
    id INTEGER PRIMARY KEY AUTOINCREMENT,
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
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_knowledge_unit_lookup
    ON ai_agent_knowledge_unit(status, intent_key, business, location, channel, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_agent_knowledge_unit_slot
    ON ai_agent_knowledge_unit(slot_signature, status);

CREATE TABLE IF NOT EXISTS ai_agent_memory_link (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    query_key TEXT NOT NULL,
    knowledge_unit_id INTEGER NOT NULL REFERENCES ai_agent_knowledge_unit(id) ON DELETE CASCADE,
    link_type TEXT NOT NULL DEFAULT 'supports',
    weight REAL NOT NULL DEFAULT 1.0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(query_key, knowledge_unit_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_agent_memory_link_query
    ON ai_agent_memory_link(query_key, weight DESC);

CREATE INDEX IF NOT EXISTS idx_ai_agent_memory_link_unit
    ON ai_agent_memory_link(knowledge_unit_id);

