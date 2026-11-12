-- Correct the original seed labels only. Business identity, ownership and bindings stay unchanged.
-- Administrator-defined names are deliberately preserved; no location-name inference is performed.
UPDATE businesses AS business
SET display_name = names.display_name, updated_at = CURRENT_TIMESTAMP
FROM (VALUES
    ('sushi', 'Суши', 'СушиВёсла'),
    ('bliny', 'Блины', 'Блинбери')
) AS names(code, original_name, display_name)
WHERE business.code = names.code AND business.display_name = names.original_name;
