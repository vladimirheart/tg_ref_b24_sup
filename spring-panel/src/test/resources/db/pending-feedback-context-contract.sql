-- Native PostgreSQL contract, also executed by PendingFeedbackContextMigrationPostgresqlTest.
-- Must run in a fresh schema after V1..V83, the before fixture, then V84.
CREATE TEMP TABLE feedback_context_checks(name TEXT PRIMARY KEY);
CREATE FUNCTION feedback_context_assert(label TEXT, ok BOOLEAN) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    IF ok IS DISTINCT FROM TRUE THEN RAISE EXCEPTION 'ASSERTION FAILED: %',label; END IF;
    INSERT INTO feedback_context_checks VALUES(label);
END;
$$;
SELECT feedback_context_assert('storage contract exists',
    (SELECT count(*)=4 FROM information_schema.columns WHERE table_schema=current_schema()
        AND table_name='pending_feedback_requests'
        AND column_name IN ('ticket_context_epoch','ticket_public_id','ticket_business_id','ticket_is_general')));
SELECT feedback_context_assert('historical request remains unbound',
    (SELECT ticket_context_epoch IS NULL AND ticket_public_id IS NULL AND ticket_business_id IS NULL
        AND ticket_is_general IS NULL FROM pending_feedback_requests WHERE source='old'));
SELECT feedback_context_assert('historical fields unchanged',
    (SELECT to_jsonb(p)-ARRAY['ticket_context_epoch','ticket_public_id','ticket_business_id','ticket_is_general']=b.value
       FROM pending_feedback_requests p CROSS JOIN feedback_context_before b WHERE source='old'));

CREATE FUNCTION feedback_context_reject(label TEXT, statement TEXT) RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        EXECUTE statement;
    EXCEPTION WHEN CHECK_VIOLATION THEN
        PERFORM feedback_context_assert(label,TRUE);
        RETURN;
    END;
    RAISE EXCEPTION 'ASSERTION FAILED: expected 23514: %',label;
END;
$$;

DO $$
DECLARE
    raw TEXT;
    kind TEXT;
    channel BIGINT := (SELECT id FROM channels WHERE token='feedback-context-test');
    business BIGINT := (SELECT id FROM businesses WHERE code='sushi');
    epoch UUID;
    next_epoch UUID;
    old_pending JSONB;
    n INTEGER := 0;
BEGIN
    FOREACH raw IN ARRAY ARRAY[' padded/ID%? ', 'Обращение-ёж', 'quote''ID', 'null'] LOOP
        FOREACH kind IN ARRAY ARRAY['BUSINESS','GENERAL','UNRESOLVED'] LOOP
            n := n+1;
            INSERT INTO tickets(user_id,ticket_id,channel_id,ticket_public_id,business_id,is_general,general_selection_source)
            VALUES (9007199254740993,raw,channel,CASE WHEN kind='UNRESOLVED' THEN NULL ELSE lpad(n::text,32,'0') END,
                CASE WHEN kind='BUSINESS' THEN business ELSE NULL END,kind='GENERAL',
                CASE WHEN kind='GENERAL' THEN 'USER_SELECTION' ELSE NULL END);
            SELECT feedback_context_epoch INTO epoch FROM tickets WHERE ticket_id=raw;
            INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at)
            VALUES (9007199254740993,channel,raw,'new',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 day');
            PERFORM feedback_context_assert(n||' exact frozen tuple',
                (SELECT p.user_id=9007199254740993 AND p.ticket_id=raw AND p.channel_id=channel
                    AND p.ticket_context_epoch=t.feedback_context_epoch
                    AND p.ticket_public_id IS NOT DISTINCT FROM t.ticket_public_id
                    AND p.ticket_business_id IS NOT DISTINCT FROM t.business_id
                    AND p.ticket_is_general IS NOT DISTINCT FROM t.is_general
                   FROM pending_feedback_requests p JOIN tickets t USING(ticket_id) WHERE p.ticket_id=raw));
            SELECT to_jsonb(p) INTO old_pending FROM pending_feedback_requests p WHERE ticket_id=raw;
            UPDATE tickets SET status='resolved' WHERE ticket_id=raw;
            PERFORM feedback_context_assert(n||' ordinary status preserves epoch',
                (SELECT feedback_context_epoch=epoch FROM tickets WHERE ticket_id=raw));
            UPDATE pending_feedback_requests SET expires_at=expires_at+INTERVAL '1 minute',source='renewed',sent_at=CURRENT_TIMESTAMP
                WHERE ticket_id=raw;
            PERFORM feedback_context_assert(n||' lifecycle writes preserve provenance',
                (SELECT to_jsonb(p)-ARRAY['source','sent_at','expires_at']=old_pending-ARRAY['source','sent_at','expires_at']
                    FROM pending_feedback_requests p WHERE ticket_id=raw));
            PERFORM feedback_context_reject(n||' cannot rewrite pending epoch',
                format('UPDATE pending_feedback_requests SET ticket_context_epoch=gen_random_uuid() WHERE ticket_id=%L',raw));
            PERFORM feedback_context_reject(n||' cannot rebind pending identity',
                format('UPDATE pending_feedback_requests SET user_id=42 WHERE ticket_id=%L',raw));
            PERFORM feedback_context_reject(n||' cannot rewrite root epoch',
                format('UPDATE tickets SET feedback_context_epoch=gen_random_uuid() WHERE ticket_id=%L',raw));
            -- Change away and back without changing any externally visible identity (ABA).
            UPDATE tickets SET channel_id=NULL WHERE ticket_id=raw;
            SELECT feedback_context_epoch INTO next_epoch FROM tickets WHERE ticket_id=raw;
            PERFORM feedback_context_assert(n||' channel change rotates epoch',next_epoch<>epoch);
            UPDATE tickets SET channel_id=channel WHERE ticket_id=raw;
            PERFORM feedback_context_assert(n||' ABA cannot revive old epoch',
                (SELECT feedback_context_epoch<>epoch AND feedback_context_epoch<>next_epoch FROM tickets WHERE ticket_id=raw));
            PERFORM feedback_context_assert(n||' pending snapshot survives root change',
                (SELECT ticket_context_epoch=epoch FROM pending_feedback_requests WHERE ticket_id=raw));
            DELETE FROM tickets WHERE ticket_id=raw;
            INSERT INTO tickets(user_id,ticket_id,channel_id,feedback_context_epoch)
                VALUES (9007199254740993,raw,channel,epoch);
            PERFORM feedback_context_assert(n||' recreate cannot reuse supplied epoch',
                (SELECT feedback_context_epoch<>epoch FROM tickets WHERE ticket_id=raw));
            DELETE FROM pending_feedback_requests WHERE ticket_id=raw;
            DELETE FROM tickets WHERE ticket_id=raw;
        END LOOP;
    END LOOP;
END;
$$;

DO $$
DECLARE
    channel BIGINT := (SELECT id FROM channels WHERE token='feedback-context-test');
    business BIGINT := (SELECT id FROM businesses WHERE code='sushi');
    epoch UUID;
    col TEXT;
    rotated BOOLEAN;
BEGIN
    INSERT INTO tickets(user_id,ticket_id,channel_id) VALUES (42,'target',channel);
    SELECT feedback_context_epoch INTO epoch FROM tickets WHERE ticket_id='target';
    FOREACH col IN ARRAY ARRAY['user_id','ticket_id','channel_id','ticket_public_id','business_id','is_general'] LOOP
        -- Each mutation is rolled back in a subtransaction; capture must observe the owning SQL statement.
        BEGIN
            EXECUTE CASE col
                WHEN 'user_id' THEN 'UPDATE tickets SET user_id=43 WHERE ticket_id=''target'''
                WHEN 'ticket_id' THEN 'UPDATE tickets SET ticket_id=''renamed'' WHERE ticket_id=''target'''
                WHEN 'channel_id' THEN 'UPDATE tickets SET channel_id=NULL WHERE ticket_id=''target'''
                WHEN 'ticket_public_id' THEN 'UPDATE tickets SET ticket_public_id=repeat(''a'',32) WHERE ticket_id=''target'''
                WHEN 'business_id' THEN format('UPDATE tickets SET business_id=%s WHERE ticket_id=''target''',business)
                WHEN 'is_general' THEN 'UPDATE tickets SET is_general=TRUE,general_selection_source=''USER_SELECTION'' WHERE ticket_id=''target'''
            END;
            rotated := (SELECT feedback_context_epoch<>epoch FROM tickets WHERE user_id IN (42,43));
            RAISE SQLSTATE 'P0002'; -- rollback mutation; record the assertion outside this block
        EXCEPTION WHEN NO_DATA_FOUND THEN NULL;
        END;
        PERFORM feedback_context_assert(col||' rotates epoch',rotated);
        PERFORM feedback_context_assert(col||' rollback restores epoch',
            (SELECT feedback_context_epoch=epoch FROM tickets WHERE ticket_id='target'));
    END LOOP;
    PERFORM feedback_context_reject('missing root rejected',format(
        'INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at) VALUES(42,%s,''missing'',''x'',now(),now())',channel));
    PERFORM feedback_context_reject('foreign user rejected',format(
        'INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at) VALUES(43,%s,''target'',''x'',now(),now())',channel));
    PERFORM feedback_context_reject('foreign channel rejected',
        'INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at) VALUES(42,999,''target'',''x'',now(),now())');
    PERFORM feedback_context_reject('partial snapshot rejected',format(
        'INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at,ticket_is_general) VALUES(42,%s,''target'',''x'',now(),now(),FALSE)',channel));
    PERFORM feedback_context_reject('spoofed epoch rejected',format(
        'INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at,ticket_context_epoch,ticket_is_general) VALUES(42,%s,''target'',''x'',now(),now(),gen_random_uuid(),FALSE)',channel));
    INSERT INTO tickets(user_id,ticket_id) VALUES (43,'target');
    PERFORM feedback_context_reject('duplicate raw id rejected across owners/channels',format(
        'INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at) VALUES(42,%s,''target'',''x'',now(),now())',channel));
    DELETE FROM tickets WHERE user_id=43 AND ticket_id='target';
    PERFORM feedback_context_reject('legacy provenance cannot be upgraded',
        'UPDATE pending_feedback_requests SET ticket_context_epoch=gen_random_uuid(),ticket_is_general=FALSE WHERE source=''old''');
    UPDATE pending_feedback_requests SET expires_at=expires_at+INTERVAL '1 minute' WHERE source='old';
    PERFORM feedback_context_assert('legacy expiry renewal does not bind context',
        (SELECT ticket_context_epoch IS NULL AND ticket_is_general IS NULL FROM pending_feedback_requests WHERE source='old'));
    INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at)
        VALUES(42,channel,'target','valid',now(),now()+INTERVAL '1 day');
    FOREACH col IN ARRAY ARRAY['channel_id','ticket_id','created_at','ticket_public_id','ticket_business_id','ticket_is_general'] LOOP
        PERFORM feedback_context_reject(col||' pending mutation rejected',
            format('UPDATE pending_feedback_requests SET %I=%s WHERE source=''valid''',col,CASE col
                WHEN 'channel_id' THEN 'NULL' WHEN 'ticket_id' THEN '''missing'''
                WHEN 'created_at' THEN 'created_at+INTERVAL ''1 second'''
                WHEN 'ticket_public_id' THEN 'repeat(''b'',32)' WHEN 'ticket_business_id' THEN business::text
                ELSE 'TRUE' END));
    END LOOP;
    INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at,
        ticket_context_epoch,ticket_public_id,ticket_business_id,ticket_is_general)
        SELECT user_id,channel_id,ticket_id,'explicit',now(),now()+INTERVAL '1 day',
               feedback_context_epoch,ticket_public_id,business_id,is_general FROM tickets WHERE ticket_id='target';
    PERFORM feedback_context_assert('exact explicit snapshot accepted',
        (SELECT ticket_context_epoch=epoch FROM pending_feedback_requests WHERE source='explicit'));
    FOREACH col IN ARRAY ARRAY['ticket_public_id','ticket_business_id','ticket_is_general'] LOOP
        PERFORM feedback_context_reject(col||' explicit spoof rejected',format(
            'INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at,
                ticket_context_epoch,ticket_public_id,ticket_business_id,ticket_is_general)
             SELECT user_id,channel_id,ticket_id,''spoof'',now(),now(),feedback_context_epoch,%s,%s,%s FROM tickets WHERE ticket_id=''target''',
                CASE WHEN col='ticket_public_id' THEN 'repeat(''c'',32)' ELSE 'ticket_public_id' END,
                CASE WHEN col='ticket_business_id' THEN business::text ELSE 'business_id' END,
                CASE WHEN col='ticket_is_general' THEN 'TRUE' ELSE 'is_general' END));
    END LOOP;
    UPDATE tickets SET user_id=user_id,channel_id=channel_id,status=status WHERE ticket_id='target';
    PERFORM feedback_context_assert('no-op root update preserves epoch',
        (SELECT feedback_context_epoch=epoch FROM tickets WHERE ticket_id='target'));
    INSERT INTO tickets(user_id,ticket_id) VALUES (44,'other'),(45,'other');
    INSERT INTO pending_feedback_requests(user_id,channel_id,ticket_id,source,created_at,expires_at)
        VALUES(42,channel,'target','unrelated',now(),now()+INTERVAL '1 day');
    PERFORM feedback_context_assert('unrelated duplicate does not deny target',
        (SELECT ticket_context_epoch=epoch FROM pending_feedback_requests WHERE source='unrelated'));
    PERFORM feedback_context_assert('denied inserts leave only intended requests',
        (SELECT count(*)=4 FROM pending_feedback_requests));
END;
$$;
SELECT count(*) AS passed_assertions FROM feedback_context_checks;
