package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.entity.ChatHistory;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.junit.jupiter.api.Test;

class MaxLinkedMediaIngestionTest {
    @ParameterizedTest @CsvSource({"false,forward","false,reply","true,forward","true,reply"})
    void eachOwnedHistoryItemKeepsTheSameTypedContext(boolean general,String type)throws Exception {
        var fixture=new MaxFollowupMediaIngestionTest();fixture.setup();fixture.f.root(general,0);
        var event=fixture.f.event("max",Long.toString(Long.MAX_VALUE),2);
        var link=new MessageSourceLink(type,"mid.original:opaque","9007199254740993","Original <author>");
        event=fixture.f.change(fixture.f.change(event,"messageType",MessageSourceLink.BATCH_TYPE),"sourceLink",link);
        fixture.ingestion.ingest(event,"integration.ticket.max");
        var capture=org.mockito.ArgumentCaptor.forClass(ChatHistory.class);verify(fixture.history,times(2)).save(capture.capture());
        for(var row:capture.getAllValues()) {
            assertThat(MessageSourceLinkJson.read(row.getSourceLinkJson())).isEqualTo(link);
            assertThat(row.getUserId()).isEqualTo(1001L);assertThat(row.getTicketOwnerUserId()).isEqualTo(1001L);assertThat(row.getTicketId()).isEqualTo(fixture.f.ticketId);
            assertThat(row.getTgMessageId()).isEqualTo(Long.MAX_VALUE);assertThat(row.getReplyToTgId()).isNull();
            assertThat(row.getForwardedFrom()).isEqualTo(type.equals("forward")?link.authorName():null);
        }
    }
    @ParameterizedTest @ValueSource(strings={"v1-with-link","version-without-link","foreign-platform","legacy-kind"})
    void mismatchedProtocolFailsBeforeOpeningAnyFile(String kind)throws Exception {
        var fixture=new MaxFollowupMediaIngestionTest();fixture.setup();var event=fixture.f.event("max","33",2);
        if(!kind.equals("version-without-link"))event=fixture.f.change(event,"sourceLink",new MessageSourceLink("reply","mid.quoted",null,null));
        if(!kind.equals("v1-with-link"))event=fixture.f.change(event,"messageType",MessageSourceLink.BATCH_TYPE);
        if(kind.equals("foreign-platform"))event=fixture.f.change(event,"platform","vk");
        if(kind.equals("legacy-kind"))event=fixture.f.change(event,"eventKind","client_message.active_ticket");
        var rejected=event;assertThatThrownBy(()->fixture.ingestion.ingest(rejected,"integration.ticket.max")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(fixture.f.storage,fixture.history,fixture.metadata,fixture.active);
    }
    @Test void contextJsonIsBoundedAndRoundTripsRawIdentity() {
        var link=new MessageSourceLink("reply","mid.<opaque>:a+b","9223372036854775807",null);
        assertThat(MessageSourceLinkJson.read(MessageSourceLinkJson.write(link))).isEqualTo(link);
        assertThat(MessageSourceLinkJson.read("a".repeat(4097))).isNull();assertThat(MessageSourceLinkJson.read("{bad}")).isNull();
    }
}
