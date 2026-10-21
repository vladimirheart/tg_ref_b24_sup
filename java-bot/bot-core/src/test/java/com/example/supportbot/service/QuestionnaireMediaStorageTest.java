package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import com.example.supportbot.config.ObjectStorageProperties;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuestionnaireMediaStorageTest {
    @TempDir Path root;
    @ParameterizedTest @ValueSource(booleans={false,true})
    void sameOwnedBytesRetryToOneExactObjectWithDifferentScopeNamespaces(boolean general) throws Exception {
        var service=new AttachmentService(root,new ObjectStorageProperties());byte[] bytes="owned bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Long owner=general?null:7L;
        var first=service.storeQuestionnaire(55,owner,1001,"c".repeat(64),33,"txt",new ByteArrayInputStream(bytes));
        var second=service.storeQuestionnaire(55,owner,1001,"c".repeat(64),33,"txt",new ByteArrayInputStream(bytes));
        assertThat(first).isEqualTo(second);assertThat(Files.readAllBytes(first.localPath())).isEqualTo(bytes);
        assertThat(first.storageKey()).isEqualTo("questionnaires/telegram/55/"+(general?"general":"business-7")+"/1001/"+"c".repeat(64)+"/33/"+QuestionnaireMedia.sha256(bytes)+".txt");
        try(var files=Files.walk(root)){assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(1);}
        assertThat(QuestionnaireMedia.matches(first.storageKey(),55,owner,1001,"c".repeat(64),33)).isTrue();
        assertThat(QuestionnaireMedia.matches(first.storageKey(),55,general?7L:null,1001,"c".repeat(64),33)).isFalse();
        assertThat(QuestionnaireMedia.matches(first.storageKey(),56,owner,1001,"c".repeat(64),33)).isFalse();
    }
    @ParameterizedTest @ValueSource(ints={0,10485761})
    void rejectsEmptyOrOversizedBytesAndClosesTheStreamBeforeCreatingObjects(int size) throws Exception {
        var service=new AttachmentService(root,new ObjectStorageProperties());
        var input=new ByteArrayInputStream(new byte[size]) {boolean closed;@Override public void close(){closed=true;} };
        assertThatThrownBy(()->service.storeQuestionnaire(55,7L,1001,"c".repeat(64),33,"bin",input)).isInstanceOf(AttachmentService.MediaSizeException.class);
        assertThat(input.closed).isTrue();try(var files=Files.walk(root)){assertThat(files.filter(Files::isRegularFile).count()).isZero();}
    }
    @Test void exactTenMiBLimitIsAcceptedAndHashPinsEveryByte() throws Exception {
        byte[] bytes=new byte[QuestionnaireMedia.MAX_BYTES];bytes[bytes.length-1]=19;
        var stored=new AttachmentService(root,new ObjectStorageProperties()).storeQuestionnaire(55,7L,1001,"c".repeat(64),33,"bin",new ByteArrayInputStream(bytes));
        assertThat(Files.size(stored.localPath())).isEqualTo(QuestionnaireMedia.MAX_BYTES);
        assertThat(stored.storageKey()).endsWith(QuestionnaireMedia.sha256(bytes)+".bin");
    }
    @ParameterizedTest @ValueSource(strings={"../txt","txt/other","TXT","","%2f","txt\\other"})
    void rejectsUntrustedExtensionsBeforeReadingOrWriting(String extension) throws Exception {
        var service=new AttachmentService(root,new ObjectStorageProperties());var input=org.mockito.Mockito.mock(InputStream.class);
        assertThatThrownBy(()->service.storeQuestionnaire(55,7L,1001,"c".repeat(64),33,extension,input)).isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verifyNoInteractions(input);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void vkMultipleSlotsAreStableAndSeparateFromTelegramAndOtherOwners(boolean general) throws Exception {
        var service=new AttachmentService(root,new ObjectStorageProperties());Long owner=general?null:7L;byte[] bytes={1,2,3};
        var first=service.storeVkQuestionnaire(55,owner,1001,"c".repeat(64),33,1,"bin",new ByteArrayInputStream(bytes));
        assertThat(service.storeVkQuestionnaire(55,owner,1001,"c".repeat(64),33,1,"bin",new ByteArrayInputStream(bytes))).isEqualTo(first);
        var second=service.storeVkQuestionnaire(55,owner,1001,"c".repeat(64),33,2,"bin",new ByteArrayInputStream(bytes));
        assertThat(first.storageKey()).isNotEqualTo(second.storageKey());assertThat(QuestionnaireMedia.matchesVk(first.storageKey(),55,owner,1001,"c".repeat(64),33)).isTrue();
        assertThat(QuestionnaireMedia.matches(first.storageKey(),55,owner,1001,"c".repeat(64),33)).isFalse();
        assertThat(QuestionnaireMedia.matchesVk(first.storageKey(),55,general?7L:null,1001,"c".repeat(64),33)).isFalse();
        try(var files=Files.walk(root)){assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(2);}
    }
    @ParameterizedTest @ValueSource(ints={0,11,-1})
    void invalidVkSlotsFailBeforeInputIsRead(int slot) {
        var input=org.mockito.Mockito.mock(InputStream.class);
        assertThatThrownBy(()->new AttachmentService(root,new ObjectStorageProperties()).storeVkQuestionnaire(55,7L,1001,"c".repeat(64),33,slot,"bin",input)).isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verifyNoInteractions(input);
    }
}
