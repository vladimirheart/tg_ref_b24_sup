package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.BotIntegrationTransportMode;
import com.example.supportbot.config.IntegrationRabbitProperties;
import com.example.supportbot.entity.Channel;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class MaxLinkedLegacyBoundaryTest {
    final Channel channel=new Channel();
    final MessageSourceLink source=new MessageSourceLink("reply","mid.original",null,null);
    final Object[] dependencies=Arrays.stream(TicketService.class.getConstructors()[0].getParameterTypes()).map(type->mock(type)).toArray();
    MaxLinkedLegacyBoundaryTest(){channel.setId(55L);channel.setPlatform("max");}
    TicketService service()throws Exception{return (TicketService)TicketService.class.getConstructors()[0].newInstance(dependencies);}
    ConversationTicketCreationCommand legacyCommand(){return new ConversationTicketCreationCommand(1001L,"actual","actual","Actual",Map.of(),List.of(),
        List.of(new ConversationHistoryEntry(1001L,"Caption","document","legacy/key","file.pdf","33",OffsetDateTime.now(),source)),channel,OffsetDateTime.now(),"submission",null,null,null,null,null,null);}
    @Test void localJdbcCreationCannotSilentlyDiscardSourceContext()throws Exception {
        assertThatThrownBy(()->service().createConversationTicket(legacyCommand())).isInstanceOf(IllegalStateException.class).hasMessageContaining("canonical Rabbit questionnaire writer");
        for(Object dependency:dependencies)if(!(dependency instanceof BotIntegrationTransportMode))verifyNoInteractions(dependency);
    }
    @Test void genericActiveMessageCannotSilentlyDiscardSourceContext()throws Exception {
        var command=new ActiveInboundClientMessageCommand(1001L,"actual","actual","Actual",channel,"raw","Caption","document",null,null,33L,null,null,OffsetDateTime.now(),source);
        assertThatThrownBy(()->service().recordActiveClientMessage(command)).isInstanceOf(IllegalStateException.class).hasMessageContaining("selected media batch writer");
        for(Object dependency:dependencies)verifyNoInteractions(dependency);
    }
    @Test void legacyInitialWireCannotBypassSelectedQuestionnaireAdmission() {
        var outbox=mock(IntegrationTransportOutboxService.class);var publisher=new ConversationTicketCreatedPublisher(outbox,new IntegrationRabbitProperties());
        assertThatThrownBy(()->publisher.publish(legacyCommand(),"raw",null,null,null,null,"Caption")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("explicitly selected MAX questionnaire");
        verifyNoInteractions(outbox);
    }
}
