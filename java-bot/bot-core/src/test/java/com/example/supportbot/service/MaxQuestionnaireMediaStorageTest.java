package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.ObjectStorageProperties;
import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class MaxQuestionnaireMediaStorageTest {
    @TempDir Path root;
    @ParameterizedTest @ValueSource(booleans={false,true})
    void ownedBytesAreIdempotentAndSeparatedFromTelegramVkOtherUsersAndSlots(boolean general) throws Exception {
        var service=new AttachmentService(root,new ObjectStorageProperties());Long business=general?null:7L;
        byte[] bytes={1,2,3};String catalog="c".repeat(64);
        var first=service.storeMaxQuestionnaire(55,business,1001,catalog,Long.MAX_VALUE,1,"bin",new ByteArrayInputStream(bytes));
        assertThat(service.storeMaxQuestionnaire(55,business,1001,catalog,Long.MAX_VALUE,1,"bin",new ByteArrayInputStream(bytes))).isEqualTo(first);
        assertThat(Files.readAllBytes(first.localPath())).isEqualTo(bytes);
        assertThat(first.storageKey()).isEqualTo("questionnaires/max/55/"+(general?"general":"business-7")+"/1001/"+catalog+"/"+Long.MAX_VALUE+"/1/"+QuestionnaireMedia.sha256(bytes)+".bin");
        assertThat(QuestionnaireMedia.matchesMax(first.storageKey(),55,business,1001,catalog,Long.MAX_VALUE,1)).isTrue();
        assertThat(QuestionnaireMedia.matchesMax(first.storageKey(),55,business,1001,catalog,Long.MAX_VALUE,2)).isFalse();
        assertThat(QuestionnaireMedia.matchesMax(first.storageKey(),55,general?7L:null,1001,catalog,Long.MAX_VALUE,1)).isFalse();
        assertThat(QuestionnaireMedia.matchesMax(first.storageKey(),56,business,1001,catalog,Long.MAX_VALUE,1)).isFalse();
        assertThat(QuestionnaireMedia.matchesMax(first.storageKey(),55,business,1002,catalog,Long.MAX_VALUE,1)).isFalse();
        assertThat(QuestionnaireMedia.matchesMax(first.storageKey(),55,business,1001,"d".repeat(64),Long.MAX_VALUE,1)).isFalse();
        assertThat(QuestionnaireMedia.matchesMax(first.storageKey(),55,business,1001,catalog,33,1)).isFalse();
        assertThat(QuestionnaireMedia.matches(first.storageKey(),55,business,1001,catalog,Long.MAX_VALUE)).isFalse();
        assertThat(QuestionnaireMedia.matchesVk(first.storageKey(),55,business,1001,catalog,Long.MAX_VALUE)).isFalse();
        var second=service.storeMaxQuestionnaire(55,business,1001,catalog,Long.MAX_VALUE,2,"bin",new ByteArrayInputStream(bytes));
        assertThat(second.storageKey()).isNotEqualTo(first.storageKey());
        try(var files=Files.walk(root)){assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(2);}
    }
    @ParameterizedTest @ValueSource(ints={0,-1,11})
    void invalidSlotDoesNotReadOrCloseUntransferredInput(int slot) {
        var input=mock(InputStream.class);
        assertThatThrownBy(()->new AttachmentService(root,new ObjectStorageProperties()).storeMaxQuestionnaire(55,null,1001,"c".repeat(64),33,slot,"bin",input)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(input);
    }
    @ParameterizedTest @ValueSource(strings={"channel","business","user","catalog","event"})
    void invalidOwnerDoesNotReadInput(String field) {
        var input=mock(InputStream.class);
        assertThatThrownBy(()->new AttachmentService(root,new ObjectStorageProperties()).storeMaxQuestionnaire(field.equals("channel")?0:55,
            field.equals("business")?0L:7L,field.equals("user")?0:1001,field.equals("catalog")?"C".repeat(64):"c".repeat(64),field.equals("event")?0:33,1,"bin",input))
            .isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(input);
    }
    @ParameterizedTest @ValueSource(strings={"../txt","txt/other","TXT","","%2f","txt\\other"})
    void invalidExtensionDoesNotReadInput(String extension) {
        var input=mock(InputStream.class);
        assertThatThrownBy(()->new AttachmentService(root,new ObjectStorageProperties()).storeMaxQuestionnaire(55,7L,1001,"c".repeat(64),33,1,extension,input))
            .isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(input);
    }
    @ParameterizedTest @ValueSource(ints={0,10485761})
    void invalidSizeClosesInputAndWritesNothing(int size) throws Exception {
        var input=spy(new ByteArrayInputStream(new byte[size]));
        assertThatThrownBy(()->new AttachmentService(root,new ObjectStorageProperties()).storeMaxQuestionnaire(55,null,1001,"c".repeat(64),33,1,"bin",input))
            .isInstanceOf(AttachmentService.MediaSizeException.class);verify(input).close();
        try(var files=Files.walk(root)){assertThat(files.filter(Files::isRegularFile).count()).isZero();}
    }
    @Test void exactLimitIsHashBoundAndFailedInputLeavesNoObject() throws Exception {
        byte[] bytes=new byte[QuestionnaireMedia.MAX_BYTES];bytes[bytes.length-1]=19;
        var service=new AttachmentService(root,new ObjectStorageProperties());
        var stored=service.storeMaxQuestionnaire(55,null,1001,"c".repeat(64),33,10,"bin",new ByteArrayInputStream(bytes));
        assertThat(Files.size(stored.localPath())).isEqualTo(bytes.length);assertThat(stored.storageKey()).endsWith(QuestionnaireMedia.sha256(bytes)+".bin");
        var failed=spy(new InputStream(){@Override public int read() throws IOException{throw new IOException("fixture");}});
        assertThatThrownBy(()->service.storeMaxQuestionnaire(55,null,1001,"c".repeat(64),34,1,"bin",failed)).isInstanceOf(IOException.class);verify(failed).close();
        try(var files=Files.walk(root)){assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(1);}
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void s3UploadUsesTheSameOwnedKeyAndExactBoundedBytes(boolean general) throws Exception {
        var properties=new ObjectStorageProperties();properties.setMode("s3");properties.setBucket("fixture-bucket");
        var s3=mock(software.amazon.awssdk.services.s3.S3Client.class);
        var service=new AttachmentService(root,properties);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"s3Client",s3);
        byte[] bytes={3,2,1};Long business=general?null:7L;
        var stored=service.storeMaxQuestionnaire(55,business,1001,"c".repeat(64),33,1,"bin",new ByteArrayInputStream(bytes));
        assertThat(stored.storageProvider()).isEqualTo("s3");assertThat(stored.localPath()).isNull();
        var request=org.mockito.ArgumentCaptor.forClass(software.amazon.awssdk.services.s3.model.PutObjectRequest.class);
        var body=org.mockito.ArgumentCaptor.forClass(software.amazon.awssdk.core.sync.RequestBody.class);
        verify(s3).putObject(request.capture(),body.capture());verifyNoMoreInteractions(s3);
        assertThat(request.getValue().bucket()).isEqualTo("fixture-bucket");assertThat(request.getValue().key()).isEqualTo("iguana/attachments/"+stored.storageKey());
        try(var input=body.getValue().contentStreamProvider().newStream()){assertThat(input.readAllBytes()).isEqualTo(bytes);}
        try(var files=Files.walk(root)){assertThat(files.filter(Files::isRegularFile).count()).isZero();}
    }
}
