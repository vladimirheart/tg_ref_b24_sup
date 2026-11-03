package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.storage.*;
import java.io.*;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class MaxInitialQuestionnaireMediaValidatorTest {
    final byte[] bytes={1,2,3};
    String sha() throws Exception {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    String key(boolean general,int slot) throws Exception {return "questionnaires/max/55/"+(general?"general":"business-7")+"/1001/"+"c".repeat(64)+"/33/"+slot+"/"+sha()+".bin";}
    ConversationTicketCreatedEvent.ConversationHistoryEntryPayload entry(String key){return new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(1001L,"Caption","document",key,"file.bin","33",OffsetDateTime.parse("2026-11-03T10:00:00Z"));}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void maxBatchVerifiesEveryOwnedByteAndReadProof(boolean general) throws Exception {
        var storage=mock(AttachmentObjectStorageService.class);String one=key(general,1),two=key(general,2);
        for(var key:List.of(one,two))when(storage.openDialogAttachmentByStorageKey(key)).thenReturn(new AttachmentObjectStorageService.StoredBinary(key,"application/octet-stream",3,new ByteArrayInputStream(bytes)));
        var event=SelectedQuestionnaireMediaValidatorTest.event(general,"max",List.of(entry(one),entry(two)));
        assertThat(new SelectedQuestionnaireMediaValidator(storage).validate(event,4)).containsKeys(one,two).hasSize(2);
        var row=mock(ResultSet.class);when(row.getObject("proof_channel")).thenReturn(55L);when(row.getObject("proof_user")).thenReturn(1001L);when(row.getObject("proof_business")).thenReturn(general?null:7L);when(row.getObject("proof_provider")).thenReturn(33L);
        when(row.getObject("proof_history_channel")).thenReturn(55L);when(row.getObject("proof_metadata_channel")).thenReturn(55L);
        when(row.getString("proof_source")).thenReturn("USER_SELECTION");when(row.getString("proof_platform")).thenReturn("max");when(row.getString("proof_catalog")).thenReturn("c".repeat(64));when(row.getString("content_hash")).thenReturn(sha());when(row.getObject("size")).thenReturn(3L);
        assertThat(QuestionnaireMediaStorageProof.matches(row,one)).isTrue();
        when(row.getObject("proof_history_channel")).thenReturn(56L);assertThat(QuestionnaireMediaStorageProof.matches(row,one)).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"channel","scope","user","catalog","event","slot","platform","duplicate","hash","size"})
    void foreignOrChangedMediaNeverProducesMetadata(String kind) throws Exception {
        String key=key(false,1);
        key=switch(kind){case "channel"->key.replace("/55/","/56/");case "scope"->key.replace("business-7","general");case "user"->key.replace("/1001/","/1002/");case "catalog"->key.replace("c".repeat(64),"d".repeat(64));case "event"->key.replace("/33/","/34/");case "slot"->key.replace("/1/","/11/");case "platform"->key.replace("/max/","/vk/");default->key;};
        var storage=mock(AttachmentObjectStorageService.class);
        when(storage.openDialogAttachmentByStorageKey(key)).thenReturn(new AttachmentObjectStorageService.StoredBinary(key,"application/octet-stream",kind.equals("size")?4:3,new ByteArrayInputStream(kind.equals("hash")?new byte[]{3,2,1}:bytes)));
        var entries=kind.equals("duplicate")?List.of(entry(key),entry(key)):List.of(entry(key));
        assertThatThrownBy(()->new SelectedQuestionnaireMediaValidator(storage).validate(SelectedQuestionnaireMediaValidatorTest.event(false,"max",entries),4)).isInstanceOf(IllegalStateException.class);
    }
}
