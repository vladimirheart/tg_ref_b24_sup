package com.example.acceptance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.example.acceptance.MaxMediaChainFixture.*;
import com.example.supportbot.service.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Always runnable without Docker; these are wire/isolation/provenance contracts, not infrastructure acceptance. */
class MaxMediaChainContractTest {
    static Stream<Arguments> shapes(){return Stream.of(false,true).flatMap(g->Stream.of("document","photo","audio","video").map(t->Arguments.of(g,t)));}
    @ParameterizedTest @MethodSource("shapes")
    void producerWireDeserializesAsActualPanelBatchWithExactOpaqueIdentity(boolean general,String type)throws Exception{
        var outbox=mock(IntegrationTransportOutboxService.class);var config=new com.example.supportbot.config.IntegrationRabbitProperties();config.setRoutingMax("r287.max");var publisher=new InboundClientMessagePublisher(outbox,config);
        var channel=new com.example.supportbot.entity.Channel();channel.setId(CHANNEL);channel.setPlatform("max");String key="questionnaires/max/"+CHANNEL+"/"+(general?"general":"business-"+BUSINESS)+"/"+USER+"/"+"c".repeat(64)+"/"+Long.MAX_VALUE+"/1/"+"a".repeat(64)+".bin";
        var command=new ActiveInboundClientMessageCommand(USER,"i","u","C",channel,RAW,"Caption","selected_media_batch.v1",null,null,Long.MAX_VALUE,null,null,OffsetDateTime.parse("2026-11-05T08:00:00Z"));
        publisher.publishSelectedMediaBatch(command,List.of(new InboundClientMessageEvent.MediaItem(type,key,"proof.bin")));var captured=org.mockito.ArgumentCaptor.forClass(InboundClientMessageEvent.class);verify(outbox).enqueueInboundClientMessage(captured.capture(),eq("r287.max"),same(config));
        var mapper=org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();var panel=mapper.readValue(mapper.writeValueAsBytes(captured.getValue()),com.example.panel.service.integration.InboundClientMessageEvent.class);
        assertThat(panel.platform()).isEqualTo("max");assertThat(panel.userId()).isEqualTo(USER);assertThat(panel.channelId()).isEqualTo(CHANNEL);assertThat(panel.ticketId()).isEqualTo(RAW);assertThat(panel.providerMessageId()).isEqualTo(Long.toString(Long.MAX_VALUE));assertThat(panel.mediaBatch().get(0).attachmentPath()).isEqualTo(key);assertThat(panel.mediaBatch().get(0).messageType()).isEqualTo(type);
        assertThat(panel.eventId()).isEqualTo(com.example.panel.service.integration.InboundClientMessageEvent.mediaBatchId(CHANNEL,USER,RAW,Long.toString(Long.MAX_VALUE)));assertThat(panel.occurredAt()).isEqualTo(command.occurredAt());
    }
    @ParameterizedTest @ValueSource(strings={"remote-db","remote-amqp","default-db","default-amqp","low-db","low-amqp","foreign-db","public-schema","schema-injection","credentials","query","port-overflow"})
    void workingOrAmbiguousEndpointsAreRejectedBeforeConnecting(String kind){
        String db="jdbc:postgresql://127.0.0.1:15432/r287_max_media",host="127.0.0.1",schema="r287_"+"a".repeat(32);int port=15672;
        switch(kind){case "remote-db"->db=db.replace("127.0.0.1","production.invalid");case "remote-amqp"->host="production.invalid";case "default-db"->db=db.replace("15432","5432");case "default-amqp"->port=5672;case "low-db"->db=db.replace("15432","80");case "low-amqp"->port=80;case "foreign-db"->db=db.replace("r287_max_media","iguana");case "public-schema"->schema="public";case "schema-injection"->schema+=";drop schema public";case "credentials"->db=db.replace("127.0.0.1","user:secret@127.0.0.1");case "query"->db+="?currentSchema=public";case "port-overflow"->port=65536;}
        String jdbc=db,broker=host,namespace=schema;int amqp=port;assertThatThrownBy(()->endpoint(jdbc,broker,amqp,namespace)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"","abc","AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","../../outside"})
    void invalidOwnershipNonceCannotSelectResources(String value){assertThatThrownBy(()->nonce(value)).isInstanceOf(IllegalArgumentException.class);}
    @Test void loadedPanelBytecodeMatchesThisCheckout()throws Exception{verifyPanelClasses();}
    @Test void integrationMatrixIsExplicitAndInfrastructureIsNotStartedByDiscovery(){
        assertThat(MaxFollowupMediaChainIT.positiveCases().count()).isEqualTo(12);assertThat(MaxFollowupMediaChainIT.rollbackCases().count()).isEqualTo(6);assertThat(MaxFollowupMediaChainIT.byteCases().count()).isEqualTo(4);assertThat(MaxFollowupMediaChainIT.driftCases().count()).isEqualTo(6);assertThat(MaxFollowupMediaChainIT.initialCases().count()).isEqualTo(4);assertThat(MaxFollowupMediaChainIT.f).isNull();
    }
}
