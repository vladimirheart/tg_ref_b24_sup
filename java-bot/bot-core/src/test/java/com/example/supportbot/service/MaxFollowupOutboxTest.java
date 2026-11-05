package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

class MaxFollowupOutboxTest {
    final ObjectMapper mapper=new ObjectMapper();
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    IntegrationTransportOutboxService service;
    ObjectNode payload;List<Map<String,Object>> rows;
    @BeforeEach void setup() throws Exception {
        service=new IntegrationTransportOutboxService(jdbc,mapper,mock(RabbitTemplate.class));
        payload=(ObjectNode)mapper.readTree("{\"platform\":\"max\",\"channelId\":55,\"userId\":1001,\"ticketId\":\" T-MAX \",\"providerMessageId\":\"33\",\"occurredAt\":\"2026-11-05T08:00:00Z\",\"messageType\":\"selected_media_batch.v1\",\"mediaBatch\":[{\"messageType\":\"document\",\"attachmentPath\":\"questionnaires/max/55/business-7/1001/"+"c".repeat(64)+"/33/1/"+"a".repeat(64)+".pdf\",\"attachmentName\":\"proof.pdf\"}]}");
        payload.put("eventId",InboundClientMessageEvent.mediaBatchId(55,1001," T-MAX ","33")).put("eventKind",InboundClientMessageEvent.MEDIA_BATCH_KIND);
        payload.put("attachmentPath",payload.path("mediaBatch").get(0).path("attachmentPath").asText()).put("attachmentName","proof.pdf");
        rows=new ArrayList<>();when(jdbc.queryForList(contains("payload_json"),any(Object[].class))).thenAnswer(c->rows);clearInvocations(jdbc);
    }
    Map<String,Object> row(String state) throws Exception {return Map.of("event_id",payload.path("eventId").asText(),"event_kind",payload.path("eventKind").asText(),"ticket_id",payload.path("ticketId").asText(),"status",state,"payload_json",mapper.writeValueAsString(payload));}
    @SuppressWarnings("unchecked") Optional<String> recorded(){try{return (Optional<String>)MaxFollowupAdmissionTest.invoke(service,"findRecordedMaxMediaTicket",new Class<?>[]{long.class,long.class,long.class},55L,1001L,33L);}catch(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException baseline){return Optional.empty();}}
    @ParameterizedTest @ValueSource(strings={"queued","processing","failed","published"})
    void committedBatchAcknowledgesOnlyOriginalTicketInEveryDispatchState(String state) throws Exception {
        rows.add(row(state));assertThat(recorded()).contains(" T-MAX ");
        verify(jdbc).queryForList(contains("channel_id = ?"),eq(55L),eq(1001L),eq(InboundClientMessageEvent.MEDIA_BATCH_KIND),eq("33"),anyString());verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void initialQuestionnaireMediaReplayIsNotAFollowupAfterSessionLoss() throws Exception {
        payload.put("eventId","creation").put("eventKind","ticket.created.initial_contact").put("scopeKind","BUSINESS").put("scopeSelectionSource","USER_SELECTION").put("businessId",7).put("questionTemplateId","q").put("templateSha256","a".repeat(64)).put("catalogSha256","c".repeat(64));
        var entry=payload.putArray("historyEntries").addObject();entry.put("userId",1001).put("providerMessageId","33").put("attachmentPath",payload.path("attachmentPath").asText());rows.add(row("published"));assertThat(recorded()).contains(" T-MAX ");
    }
    @ParameterizedTest @ValueSource(strings={"platform","channel","user","provider","event-id","row-ticket","kind","items","path","initial-provider","ambiguous","corrupt-json","state"})
    void corruptOrAmbiguousJournalFailsClosedWithoutPublishing(String kind) throws Exception {
        switch(kind){case "platform"->payload.put("platform","vk");case "channel"->payload.put("channelId",56);case "user"->payload.put("userId",1002);case "provider"->payload.put("providerMessageId","34");case "event-id"->payload.put("eventId","other");case "kind"->payload.put("eventKind","client_message.active_ticket");case "items"->payload.putArray("mediaBatch");case "path"->((ObjectNode)payload.path("mediaBatch").get(0)).put("attachmentPath","foreign/key");case "initial-provider"->{payload.put("eventKind","ticket.created.initial_contact");payload.putArray("historyEntries").addObject().put("providerMessageId","34");}}
        var row=new HashMap<>(row(kind.equals("state")?"unknown":"published"));if(kind.equals("row-ticket"))row.put("ticket_id","other");if(kind.equals("corrupt-json"))row.put("payload_json","{");rows.add(row);if(kind.equals("ambiguous"))rows.add(row);
        assertThatThrownBy(this::recorded).isInstanceOf(IllegalStateException.class);verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void noDurableEventIsNotReportedAsPublished(){assertThat(recorded()).isEmpty();}
}
