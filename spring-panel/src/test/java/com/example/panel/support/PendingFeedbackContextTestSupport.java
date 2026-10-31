package com.example.panel.support;

import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.repository.PendingFeedbackContext;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import java.util.*;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/** Plumbing for pre-provenance unit/owning-SQL fixtures ONLY; not persisted context acceptance. */
public final class PendingFeedbackContextTestSupport {
    private PendingFeedbackContextTestSupport() {}
    public static PendingFeedbackContext context(PendingFeedbackRequest request,String publicId,Long business,Boolean general) {
        var fields=new HashMap<String,Object>();
        fields.put("requestId",request.getId());fields.put("userId",request.getUserId());
        fields.put("channelId",request.getChannel()!=null?request.getChannel().getId():null);
        fields.put("ticketId",request.getTicketId());fields.put("publicId",publicId);
        fields.put("businessId",business);fields.put("general",general);
        fields.put("epoch",UUID.fromString("00000000-0000-4000-8000-000000000244"));
        fields.put("expiresAt",request.getExpiresAt()!=null?request.getExpiresAt().toInstant():null);
        fields.put("sentAt",request.getSentAt()!=null?request.getSentAt().toInstant():null);
        return new SpelAwareProxyProjectionFactory().createProjection(PendingFeedbackContext.class,fields);
    }
    public static void stubLockedContextFromOwnerFixture(PendingFeedbackRequestRepository repository,JdbcTemplate jdbc) {
        doAnswer(call -> {
            var request=repository.findByIdForUpdate(call.getArgument(0)).orElse(null);
            if(request==null)return Optional.empty();
            var owners=jdbc.queryForList("SELECT ticket_public_id,business_id,is_general FROM tickets WHERE ticket_id=?",request.getTicketId());
            if(owners.size()!=1)return Optional.empty();
            var owner=owners.get(0);
            return Optional.of(context(request,(String)owner.get("ticket_public_id"),(Long)owner.get("business_id"),(Boolean)owner.get("is_general")));
        }).when(repository).lockCurrentFeedbackContext(any());
    }
}
