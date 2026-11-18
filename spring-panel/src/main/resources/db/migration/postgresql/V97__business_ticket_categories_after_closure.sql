-- Categories are analytical metadata. BUSINESS operators may correct them after the lifecycle is closed,
-- without reopening the request or using a legacy raw-ticket workflow.
ALTER TABLE business_ticket_work_events
    DROP CONSTRAINT business_ticket_work_events_check;

ALTER TABLE business_ticket_work_events
    ADD CONSTRAINT business_ticket_work_events_check CHECK (
        (action = 'close' AND from_status IN ('pending', 'open') AND to_status = 'resolved'
            AND jsonb_array_length(categories) > 0)
        OR (action = 'reopen' AND from_status IN ('closed', 'resolved') AND to_status = 'pending'
            AND previous_categories = categories)
        OR (action = 'categories' AND from_status IN ('pending', 'open', 'closed', 'resolved')
            AND to_status = from_status AND previous_categories <> categories)
    );
