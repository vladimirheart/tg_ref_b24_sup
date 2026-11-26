package com.example.panel.service;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Small latest-value projection; caller still checks each recipient's current READ. */
@Service
public class DialogFeedbackPushReader {
    private final JdbcTemplate jdbc;
    public DialogFeedbackPushReader(JdbcTemplate jdbc){this.jdbc=jdbc;}
    Map<String,Object> read(UiEventTicketScope scope) {
        var rows=jdbc.query("""
            SELECT feedback.rating,feedback.id FROM tickets owner
            JOIN LATERAL (SELECT f.id,f.rating FROM feedbacks f WHERE f.ticket_id=owner.ticket_id
                ORDER BY f.timestamp DESC,f.id DESC LIMIT 1) feedback ON TRUE
            WHERE owner.user_id=? AND owner.ticket_id=? AND owner.ticket_public_id=?
              AND owner.business_id IS NOT DISTINCT FROM CAST(? AS BIGINT) AND owner.is_general=FALSE
              AND 1=(SELECT count(*) FROM tickets collision WHERE collision.ticket_id=owner.ticket_id)
            """,(rs,row)->Map.<String,Object>of("rating",rs.getInt("rating"),"feedbackVersion",rs.getString("id"),
                "ticketPublicId",scope.ticketPublicId(),"businessId",scope.businessId()==null?"":scope.businessId().toString()),
            scope.ticketUserId(),scope.ticketId(),scope.ticketPublicId(),scope.businessId());
        return rows.size()==1?rows.get(0):Map.of();
    }
}
