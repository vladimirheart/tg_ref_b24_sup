-- Run against the canonical V83 schema BEFORE V84. No inferred historical context.
INSERT INTO channels(token, channel_name) VALUES ('feedback-context-test', 'feedback-context-test');
INSERT INTO tickets(user_id,ticket_id,channel_id)
    SELECT 9007199254740993,'historical-context',id FROM channels WHERE token='feedback-context-test';
INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at)
    SELECT user_id,channel_id,ticket_id,'old',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 day'
      FROM tickets WHERE ticket_id='historical-context';
CREATE TABLE feedback_context_before AS SELECT to_jsonb(p) AS value FROM pending_feedback_requests p;
