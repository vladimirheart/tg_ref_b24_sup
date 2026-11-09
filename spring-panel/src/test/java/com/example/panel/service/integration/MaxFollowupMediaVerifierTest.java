package com.example.panel.service.integration;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.storage.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.lang.reflect.*;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaxFollowupMediaVerifierTest {
    Fixture f;
    @BeforeEach void setup()throws Exception{f=new Fixture();}
    static class Fixture {
        final JdbcTemplate jdbc=mock(JdbcTemplate.class);final ResultSet row=mock(ResultSet.class);
        final AttachmentObjectStorageService storage=mock(AttachmentObjectStorageService.class);
        final MultibusinessFeatureFlags flags=new MultibusinessFeatureFlags();
        final SelectedTicketFollowupMediaService service;
        final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
        final String ticketId=" raw-max ",catalog="c".repeat(64),sha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(new byte[]{1,2,3}));
        boolean general,duplicate;int rows=1;String badKey,badBytes;final List<Object[]> reads=new ArrayList<>();
        Fixture()throws Exception{
            flags.getFoundation().setEnabled(true);flags.setTicketWriteRequiresBusiness(true);flags.setSharedChannelResolution(true);flags.setGeneralChannelResolution(true);
            root(false,0);
            doAnswer(call->{reads.add(call.getArguments());var result=new ArrayList<SelectedTicketFollowupMediaService.Admission>();@SuppressWarnings("unchecked") RowMapper<SelectedTicketFollowupMediaService.Admission> mapping=call.getArgument(1);for(int i=0;i<rows;i++)result.add(mapping.mapRow(row,i));return result;})
                .when(jdbc).query(anyString(),any(RowMapper.class),anyLong(),anyLong(),anyString(),anyString());
            doAnswer(call->duplicate?List.of(1L):List.of()).when(jdbc).queryForList(anyString(),eq(Long.class),anyLong(),anyLong(),anyLong());
            when(storage.openDialogAttachmentByStorageKey(anyString())).thenAnswer(call->{String key=call.getArgument(0);if(key.equals(badKey)&&"missing".equals(badBytes))throw new IOException("missing fixture object");
                byte[] bytes=key.equals(badKey)&&"hash".equals(badBytes)?new byte[]{3,2,1}:new byte[]{1,2,3};
                return new AttachmentObjectStorageService.StoredBinary(key,"application/octet-stream",key.equals(badKey)&&"size".equals(badBytes)?4:3,new ByteArrayInputStream(bytes));});
            service=new SelectedTicketFollowupMediaService(jdbc,flags,storage);
        }
        void root(boolean noBusiness,long count)throws Exception{
            general=noBusiness;when(row.getObject("business_id")).thenReturn(general?null:7L);when(row.getBoolean("is_general")).thenReturn(general);
            when(row.getString("general_selection_source")).thenReturn("USER_SELECTION");when(row.getString("business_selection_source")).thenReturn("USER_SELECTION");
            when(row.getString("question_template_id")).thenReturn("frozen-questionnaire");when(row.getString("question_template_sha256")).thenReturn("a".repeat(64));when(row.getString("questionnaire_catalog_sha256")).thenReturn(catalog);when(row.getLong("media_count")).thenReturn(count);
        }
        String key(String platform,String provider,int slot){return "questionnaires/"+platform+"/55/"+(general?"general":"business-7")+"/1001/"+catalog+"/"+provider+"/"+slot+"/"+sha+".bin";}
        InboundClientMessageEvent event(String platform,String provider,int count){
            var items=new ArrayList<InboundClientMessageEvent.MediaItem>();for(int i=1;i<=count;i++)items.add(new InboundClientMessageEvent.MediaItem(i%2==0?"photo":"document",key(platform,provider,i),"file-"+i+".bin"));
            var first=items.isEmpty()?null:items.get(0);
            return new InboundClientMessageEvent(InboundClientMessageEvent.mediaBatchId(55,1001,ticketId,provider),InboundClientMessageEvent.MEDIA_BATCH_KIND,platform,55L,ticketId,1001L,"client","client","Client","Private caption","selected_media_batch.v1",first==null?null:first.attachmentPath(),first==null?null:first.attachmentName(),provider,null,null,OffsetDateTime.parse("2026-11-05T07:00:00Z"),items);
        }
        InboundClientMessageEvent change(InboundClientMessageEvent event,String field,Object value)throws Exception{ObjectNode json=mapper.valueToTree(event);json.set(field,mapper.valueToTree(value));return mapper.treeToValue(json,InboundClientMessageEvent.class);}
        @SuppressWarnings("unchecked") Map<String,SelectedQuestionnaireMediaValidator.Verified> verifyMax(InboundClientMessageEvent event)throws Exception{
            try{return (Map<String,SelectedQuestionnaireMediaValidator.Verified>)service.getClass().getMethod("verifyMaxBatch",InboundClientMessageEvent.class).invoke(service,event);}
            catch(NoSuchMethodException baseline){return service.verifyVkBatch(event);}catch(InvocationTargetException target){if(target.getCause() instanceof RuntimeException error)throw error;throw new IllegalStateException(target.getCause());}
        }
        SelectedTicketFollowupMediaService.Admission admit()throws Exception{
            try{return (SelectedTicketFollowupMediaService.Admission)service.getClass().getMethod("maxBatchAdmission",long.class,long.class,String.class).invoke(service,55L,1001L,ticketId);}
            catch(NoSuchMethodException baseline){return service.vkBatchAdmission(55L,1001L,ticketId);}catch(InvocationTargetException target){if(target.getCause() instanceof RuntimeException error)throw error;throw new IllegalStateException(target.getCause());}
        }
    }
    static Stream<Arguments> accepted(){return Stream.of(false,true).flatMap(general->Stream.of("33",Long.toString(Long.MAX_VALUE)).flatMap(provider->Stream.of(1,2,10).map(count->Arguments.of(general,provider,count))));}
    @ParameterizedTest @MethodSource("accepted") void wholeMaxBatchUsesFrozenRootAndEveryActualByte(boolean general,String provider,int count)throws Exception{
        f.root(general,0);var event=f.event("max",provider,count);assertThatCode(()->f.verifyMax(event)).doesNotThrowAnyException();var verified=f.verifyMax(event);assertThat(verified).hasSize(count);
        for(var item:event.mediaBatch()){assertThat(verified.get(item.attachmentPath()).size()).isEqualTo(3);assertThat(verified.get(item.attachmentPath()).sha256()).isEqualTo(f.sha);}
        var read=f.reads.get(0);assertThat(read[0].toString()).contains("FOR UPDATE OF ticket,active FOR SHARE OF channel");assertThat(Arrays.copyOfRange(read,2,6)).containsExactly(55L,1001L,f.ticketId,"max");
    }
    @ParameterizedTest @CsvSource({"false,0","true,0","false,9","true,9"}) void maxAdmissionHasItsOwnVersionAndFrozenQuota(boolean general,int used)throws Exception{
        f.root(general,used);var proof=f.admit();assertThat(proof.version()).isEqualTo(3);assertThat(proof.remainingFiles()).isEqualTo(10-used);assertThat(proof.ticketId()).isEqualTo(f.ticketId);assertThat(proof.scopeKind()).isEqualTo(general?"GENERAL":"BUSINESS");assertThat(proof.businessId()).isEqualTo(general?null:7L);assertThat(proof.catalogSha256()).isEqualTo(f.catalog);
        assertThat(f.reads.get(0)[0].toString()).doesNotContain("FOR UPDATE");assertThat(f.reads.get(0)[5]).isEqualTo("max");verifyNoInteractions(f.storage);
    }
    @ParameterizedTest @ValueSource(strings={"kind","platform","event-id","zero","overflow","leading-zero","reply","forward","empty","too-many","top-key","top-name","occurred","wrong-channel","wrong-user","wrong-ticket"})
    void malformedEnvelopeCannotAuthorizeAnyByte(String mode)throws Exception{
        var event=f.event("max",switch(mode){case "zero"->"0";case "overflow"->"9223372036854775808";case "leading-zero"->"033";default->"33";},mode.equals("empty")?0:mode.equals("too-many")?11:2);
        event=switch(mode){case "kind"->f.change(event,"eventKind","client_message.active_ticket");case "platform"->f.change(event,"platform","vk");case "event-id"->f.change(event,"eventId","foreign-event");case "reply"->f.change(event,"replyToProviderMessageId","1");case "forward"->f.change(event,"forwardedFrom","Somebody");case "top-key"->f.change(event,"attachmentPath","foreign");case "top-name"->f.change(event,"attachmentName","foreign");case "occurred"->f.change(event,"occurredAt",null);case "wrong-channel"->f.change(event,"channelId",56);case "wrong-user"->f.change(event,"userId",1002);case "wrong-ticket"->f.change(event,"ticketId","raw-max");default->event;};
        var rejected=event;assertThatThrownBy(()->f.verifyMax(rejected)).isInstanceOf(IllegalStateException.class);verifyNoInteractions(f.storage);
    }
    @ParameterizedTest @ValueSource(strings={"foundation","write","shared","general","missing","ambiguous","quota","source","template","hash","catalog","wrong-business"})
    void rootFlagsIdentityAndQuotaRemainMandatory(String mode)throws Exception{
        f.root(mode.equals("general")||mode.equals("wrong-business"),0);
        switch(mode){case "foundation"->f.flags.getFoundation().setEnabled(false);case "write"->f.flags.setTicketWriteRequiresBusiness(false);case "shared"->f.flags.setSharedChannelResolution(false);case "general"->f.flags.setGeneralChannelResolution(false);case "missing"->f.rows=0;case "ambiguous"->f.rows=2;case "quota"->f.root(false,9);case "source"->when(f.row.getString("business_selection_source")).thenReturn("OTHER");case "template"->when(f.row.getString("question_template_id")).thenReturn(" ");case "hash"->when(f.row.getString("question_template_sha256")).thenReturn("old");case "catalog"->when(f.row.getString("questionnaire_catalog_sha256")).thenReturn(null);case "wrong-business"->when(f.row.getObject("business_id")).thenReturn(7L);}
        assertThatThrownBy(()->f.verifyMax(f.event("max","33",2))).isInstanceOf(IllegalStateException.class);verifyNoInteractions(f.storage);
    }
    @ParameterizedTest @ValueSource(strings={"type","name","namespace","channel","business","user","catalog","provider","slot","hash-key","duplicate","already-recorded"})
    void foreignOrUnsafeItemsNeverReachStorage(String mode)throws Exception{
        var event=f.event("max","33",1);var old=event.mediaBatch().get(0);String key=old.attachmentPath(),name=old.attachmentName(),type=old.messageType();
        switch(mode){case "type"->type="voice";case "name"->name="../private.bin";case "namespace"->key=key.replace("/max/","/vk/");case "channel"->key=key.replace("/55/","/56/");case "business"->key=key.replace("business-7","general");case "user"->key=key.replace("/1001/","/1002/");case "catalog"->key=key.replace(f.catalog,"d".repeat(64));case "provider"->key=key.replace("/33/","/34/");case "slot"->key=key.replace("/1/","/2/");case "hash-key"->key=key.replace(f.sha,"invalid");case "already-recorded"->f.duplicate=true;}
        var item=new InboundClientMessageEvent.MediaItem(type,key,name);var items=mode.equals("duplicate")?List.of(item,item):List.of(item);
        event=f.change(event,"mediaBatch",items);event=f.change(event,"attachmentPath",key);event=f.change(event,"attachmentName",name);var denied=event;
        assertThatThrownBy(()->f.verifyMax(denied)).isInstanceOf(IllegalStateException.class);
        // A duplicate slot can be discovered after a preceding valid slot was read, never after a domain write.
        if(!mode.equals("duplicate"))verifyNoInteractions(f.storage);
    }
    @ParameterizedTest @ValueSource(strings={"missing","hash","size"}) void laterBadBytePreventsAWholeBatchProof(String mode)throws Exception{
        var event=f.event("max","33",2);f.badKey=event.mediaBatch().get(1).attachmentPath();f.badBytes=mode;
        assertThatThrownBy(()->f.verifyMax(event)).isInstanceOf(IllegalStateException.class);verify(f.storage).openDialogAttachmentByStorageKey(event.mediaBatch().get(0).attachmentPath());
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) void existingVkProofKeepsItsOwnNamespaceAndVersion(boolean general)throws Exception{
        f.root(general,0);assertThat(f.service.verifyVkBatch(f.event("vk","33",2))).hasSize(2);assertThat(f.service.vkBatchAdmission(55,1001,f.ticketId).version()).isEqualTo(2);
    }
}
