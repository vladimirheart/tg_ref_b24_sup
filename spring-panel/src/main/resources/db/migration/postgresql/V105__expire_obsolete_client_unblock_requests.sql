-- Preserve historical requests while removing pending actions for unblocked clients.
UPDATE client_unblock_requests request
SET status = 'expired',
    decided_at = CURRENT_TIMESTAMP,
    decided_by = 'system',
    decision_comment = concat_ws(E'\n', nullif(request.decision_comment, ''),
        'Клиент уже разблокирован; заявка утратила актуальность')
WHERE request.status = 'pending'
  AND NOT EXISTS (
      SELECT 1 FROM client_blacklist blacklist
      WHERE blacklist.user_id = request.user_id AND blacklist.is_blacklisted IS TRUE
  );

UPDATE client_blacklist
SET unblock_requested = FALSE, unblock_requested_at = NULL
WHERE is_blacklisted IS NOT TRUE
  AND (unblock_requested IS TRUE OR unblock_requested_at IS NOT NULL);
