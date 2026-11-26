package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class DialogInternalAttachmentPostgresTest extends DialogOperatorCollaborationPostgresTest {
    AttachmentObjectStorageService storage;
    DialogInternalAttachmentService attachments;
    Map<String,byte[]> objects;
    AtomicInteger reads;
    boolean failPut,revokeOnPut;
    @BeforeEach void binaries() throws Exception {
        objects=new ConcurrentHashMap<>();reads=new AtomicInteger();
        storage=mock(AttachmentObjectStorageService.class);
        when(storage.storeInternalDialogAttachment(anyString(),anyString(),any())).thenAnswer(invocation->{
            String key=invocation.getArgument(0),mime=invocation.getArgument(1);
            byte[] bytes=((InputStream)invocation.getArgument(2)).readAllBytes();objects.put(key,bytes);
            if(failPut)throw new IOException("Fixture upload failed after write");
            if(revokeOnPut)jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=1 AND business_id=11");
            return new AttachmentObjectStorageService.StoredBinary(key,mime,bytes.length,new ByteArrayInputStream(bytes));
        });
        doAnswer(invocation->{objects.remove(invocation.<String>getArgument(0));return null;})
            .when(storage).deleteInternalDialogAttachment(anyString());
        when(storage.openInternalDialogAttachment(anyString())).thenAnswer(invocation->{
            reads.incrementAndGet();String key=invocation.getArgument(0);byte[] bytes=objects.get(key);
            if(bytes==null)throw new FileNotFoundException();
            return new AttachmentObjectStorageService.StoredBinary(key,"application/octet-stream",bytes.length,new ByteArrayInputStream(bytes));
        });
        attachments=new DialogInternalAttachmentService(storage,service);
    }
    MockMultipartFile file(String name,String text){
        return new MockMultipartFile("files",name,"text/html",text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void fileOnlyBatchIsPrivateDurableAndHasNoCustomerOrTransportSideEffects() throws Exception {
        var id=UUID.randomUUID();
        assertThat(attachments.send(context(),operator(),id,"",List.of(file("Отчёт.txt","Internal secret"),file("data..json","{}")))).isTrue();
        var message=service.messages(pub,auth(2,"viewer"),0).get(0);
        assertThat(message.body()).isEmpty();assertThat(message.attachments()).hasSize(2);
        assertThat(message.attachments().get(0).name()).isEqualTo("Отчёт.txt");
        assertThat(message.attachments().get(0).mimeType()).isEqualTo("application/octet-stream");
        assertThat(objects).hasSize(2);
        var entry=attachments.open(pub,UUID.fromString(message.attachments().get(0).id()),auth(2,"viewer"));
        try(var binary=entry.binary()){assertThat(new String(binary.inputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("Internal secret");}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_history",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ui_event_outbox",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_responsibles",Long.class)).isZero();
    }
    @Test void retryMatchesFileBytesNamesOrderAndBodyAndDiscardsNewCopies() throws Exception {
        var id=UUID.randomUUID();var state=context();
        var files=List.<org.springframework.web.multipart.MultipartFile>of(file("file.txt","Same bytes"));
        assertThat(attachments.send(state,operator(),id,"Caption",files)).isTrue();
        assertThat(attachments.send(state,operator(),id,"Caption",files)).isTrue();
        assertThat(objects).hasSize(1);
        assertThat(attachments.send(state,operator(),id,"Caption",List.of(file("file.txt","Changed bytes")))).isFalse();
        assertThat(attachments.send(state,operator(),id,"Changed caption",files)).isFalse();
        assertThat(attachments.send(state,operator(),id,"Caption",List.of(file("different.txt","Same bytes")))).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_internal_messages",Long.class)).isEqualTo(1);
        assertThat(objects).hasSize(1);
    }
    @Test void failedMetadataRollsBackMessageAndDeletesUploadedBinaries() throws Exception {
        jdbc.execute("ALTER TABLE dialog_internal_attachments ADD CONSTRAINT fail_test CHECK(FALSE) NOT VALID");
        assertThatThrownBy(()->attachments.send(context(),operator(),UUID.randomUUID(),"Caption",List.of(file("a.txt","Bytes"))))
            .isInstanceOf(RuntimeException.class);
        assertThat(objects).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_internal_messages",Long.class)).isZero();
    }
    @Test void storageFailureAndRevokedWriteDoNotLeavePublishedMessages() {
        failPut=true;
        assertThatThrownBy(()->attachments.send(context(),operator(),UUID.randomUUID(),"",List.of(file("a.txt","Bytes")))).isInstanceOf(IOException.class);
        assertThat(objects).isEmpty();
        failPut=false;revokeOnPut=true;
        assertThatCode(()->assertThat(attachments.send(context(),operator(),UUID.randomUUID(),"",List.of(file("a.txt","Bytes")))).isFalse()).doesNotThrowAnyException();
        assertThat(objects).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_internal_messages",Long.class)).isZero();
    }
    @Test void downloadUsesCurrentOwnerAndCannotBorrowAnotherBusinessOrId() throws Exception {
        assertThat(attachments.send(context(),operator(),UUID.randomUUID(),"",List.of(file("a.txt","Bytes")))).isTrue();
        var id=UUID.fromString(service.messages(pub,operator(),0).get(0).attachments().get(0).id());
        assertThatThrownBy(()->attachments.open(pub,id,auth(3,"foreign"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->attachments.open("b".repeat(32),id,operator())).isInstanceOf(ResponseStatusException.class);
        assertThat(reads.get()).isZero();
        assertThat(service.transfer(context(),22,operator())).isTrue();
        assertThatThrownBy(()->attachments.open(pub,id,auth(2,"viewer"))).isInstanceOf(ResponseStatusException.class);
        try(var binary=attachments.open(pub,id,auth(3,"foreign")).binary()){assertThat(binary.size()).isEqualTo(5);}
    }
    @ParameterizedTest @ValueSource(strings={"../bad.txt","bad\\file.txt","bad\nname.txt",""})
    void unsafeNamesAreRejectedBeforeStorage(String name) {
        assertThatThrownBy(()->attachments.send(context(),operator(),UUID.randomUUID(),"",List.of(file(name,"Bytes"))))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(storage);
    }
    @Test void sizeAndCountLimitsAreCheckedBeforeStorage() {
        var large=mock(org.springframework.web.multipart.MultipartFile.class);
        when(large.getSize()).thenReturn(DialogInternalAttachmentService.MAX_BYTES+1);
        assertThatThrownBy(()->attachments.send(context(),operator(),UUID.randomUUID(),"",List.of(large))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->attachments.send(context(),operator(),UUID.randomUUID(),"",Collections.nCopies(11,file("a","Bytes"))))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(storage);
    }
    @ParameterizedTest @ValueSource(strings={"GENERAL","UNRESOLVED"})
    void filesRespectTheSameGeneralOrUnresolvedOwnerAsText(String kind) throws Exception {
        jdbc.update("UPDATE tickets SET business_id=NULL,is_general=?",kind.equals("GENERAL"));
        if(kind.equals("GENERAL")){
            assertThatThrownBy(()->attachments.send(new DialogOperatorCollaborationService.Context(
                new UiEventTicketScope(-42L,raw,pub,"GENERAL",null),0,"open",true),operator(),UUID.randomUUID(),"",List.of(file("a","Bytes"))))
                .isInstanceOf(ResponseStatusException.class);
            jdbc.update("INSERT INTO general_ticket_read_grants(user_id) VALUES(1)");
            jdbc.update("INSERT INTO general_ticket_operate_grants(user_id) VALUES(1)");
        }
        assertThat(attachments.send(context(),operator(),UUID.randomUUID(),"",List.of(file("a","Bytes")))).isTrue();
        assertThat(service.messages(pub,operator(),0).get(0).attachments()).hasSize(1);
    }
    @Test void lostCommitAcknowledgementNeverDeletesAnAlreadyCommittedObject() throws Exception {
        var source=jdbc.getDataSource();var failOnce=new java.util.concurrent.atomic.AtomicBoolean(true);
        var uncertain=new org.springframework.jdbc.datasource.AbstractDataSource(){
            @Override public java.sql.Connection getConnection() throws java.sql.SQLException{return wrap(source.getConnection());}
            @Override public java.sql.Connection getConnection(String name,String password) throws java.sql.SQLException{return wrap(source.getConnection(name,password));}
            java.sql.Connection wrap(java.sql.Connection delegate){
                return (java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Connection.class},
                    (proxy,method,args)->{
                        if(method.getName().equals("commit")){
                            delegate.commit();if(failOnce.compareAndSet(true,false))throw new java.sql.SQLException("Fixture lost commit acknowledgement");
                            return null;
                        }
                        try{return method.invoke(delegate,args);}catch(java.lang.reflect.InvocationTargetException ex){throw ex.getCause();}
                    });
            }
        };
        var uncertainJdbc=new org.springframework.jdbc.core.JdbcTemplate(uncertain);
        var uncertainMessages=new DialogOperatorCollaborationService(uncertainJdbc,flags,
            new UiEventOutboxAppendService(uncertainJdbc,new LegacyTicketIdJdbcGuard(uncertainJdbc)));
        var sender=new DialogInternalAttachmentService(storage,uncertainMessages);
        var request=UUID.randomUUID();var expected=context();
        var files=List.<org.springframework.web.multipart.MultipartFile>of(file("a.txt","Bytes"));
        assertThatThrownBy(()->sender.send(expected,operator(),request,"",files)).isInstanceOf(RuntimeException.class);
        assertThat(objects).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dialog_internal_messages",Long.class)).isEqualTo(1);
        assertThat(sender.send(expected,operator(),request,"",files)).isTrue();
        assertThat(objects).hasSize(1);
    }
}

