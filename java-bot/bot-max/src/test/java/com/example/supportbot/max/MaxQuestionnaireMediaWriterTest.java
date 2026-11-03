package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.service.*;
import com.example.supportbot.config.ObjectStorageProperties;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class MaxQuestionnaireMediaWriterTest {
    @TempDir Path root;
    final MaxApiClient api=mock(MaxApiClient.class);
    private MaxQuestionnaireMediaWriter.Owner owner(boolean general) {return new MaxQuestionnaireMediaWriter.Owner(55,general?null:7L,1001,"c".repeat(64),9007199254740993L);}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void writesOnlyActualScopedBytesAndNeverPersistsProviderUrl(boolean general) throws Exception {
        byte[] bytes={1,2,3};String url="https://cdn.max.ru/private?secret=do-not-persist";
        when(api.downloadQuestionnaireAttachment(url)).thenReturn(new MaxApiClient.DownloadedQuestionnaireAttachment(bytes,"application/pdf"));
        var storage=spy(new AttachmentService(root,new ObjectStorageProperties()));var writer=new MaxQuestionnaireMediaWriter(api,storage);
        var first=writer.store(owner(general),1,"document",url,"../../private\u0000.pdf");
        assertThat(writer.store(owner(general),1,"document",url,"private.pdf")).isEqualTo(first);
        assertThat(first.name()).isEqualTo("private.pdf");assertThat(first.sha256()).isEqualTo(QuestionnaireMedia.sha256(bytes));
        assertThat(first.size()).isEqualTo(3);assertThat(first.providerMessageId()).isEqualTo(9007199254740993L);
        assertThat(Files.readAllBytes(root.resolve(first.storageKey()))).isEqualTo(bytes);assertThat(first.toString()).doesNotContain("secret","https:","../");
        verify(storage,never()).store(anyString(),anyString(),any());verify(api,never()).downloadAttachment(anyString());
        assertThat(writer.store(owner(general),2,"document",url,"private.pdf").storageKey()).isNotEqualTo(first.storageKey());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"unknown","file","PHOTO","../photo"})
    void invalidTypeDoesNotDownload(String type) {
        var storage=mock(AttachmentService.class);
        assertThatThrownBy(()->new MaxQuestionnaireMediaWriter(api,storage).store(owner(false),1,type,"https://cdn.max.ru/file",null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(api,storage);
    }
    @ParameterizedTest @ValueSource(ints={0,-1,11})
    void invalidSlotDoesNotDownload(int slot) {
        var storage=mock(AttachmentService.class);
        assertThatThrownBy(()->new MaxQuestionnaireMediaWriter(api,storage).store(owner(false),slot,"document","https://cdn.max.ru/file",null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(api,storage);
    }
    @ParameterizedTest @ValueSource(strings={"size","empty","download","missing","store","wrong-key"})
    void failuresDoNotBecomeLegacyStorageOrSuccessfulMetadata(String failure) throws Exception {
        String url="https://cdn.max.ru/file";var storage=mock(AttachmentService.class);
        if(failure.equals("download"))when(api.downloadQuestionnaireAttachment(url)).thenThrow(new IOException("download failure"));
        else if(!failure.equals("missing"))when(api.downloadQuestionnaireAttachment(url)).thenReturn(new MaxApiClient.DownloadedQuestionnaireAttachment(new byte[failure.equals("size")?QuestionnaireMedia.MAX_BYTES+1:failure.equals("empty")?0:3],null));
        if(failure.equals("store"))when(storage.storeMaxQuestionnaire(anyLong(),any(),anyLong(),anyString(),anyLong(),anyInt(),anyString(),any())).thenThrow(new IOException("store failure"));
        else when(storage.storeMaxQuestionnaire(anyLong(),any(),anyLong(),anyString(),anyLong(),anyInt(),anyString(),any())).thenReturn(new AttachmentService.StoredAttachment("legacy/wrong.bin","fixture",null));
        assertThatThrownBy(()->new MaxQuestionnaireMediaWriter(api,storage).store(owner(false),1,"document",url,null)).isInstanceOf(IOException.class);
        verify(storage,never()).store(anyString(),anyString(),any());verify(api,never()).downloadAttachment(anyString());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"https://cdn.max.ru/path?secret=private","..\\..\\\u0000","\u0000\n"})
    void missingOrUnsafeNameIsNotDerivedFromSignedUrl(String name) throws Exception {
        when(api.downloadQuestionnaireAttachment(anyString())).thenReturn(new MaxApiClient.DownloadedQuestionnaireAttachment(new byte[]{1},"application/pdf"));
        var result=new MaxQuestionnaireMediaWriter(api,new AttachmentService(root,new ObjectStorageProperties())).store(owner(true),1,"document","https://cdn.max.ru/path?secret=private",name);
        assertThat(result.name()).isEqualTo("вложение.pdf");assertThat(result.toString()).doesNotContain("secret","https:");
    }
    @Test void downloadedBytesAreDefensiveCopies() {
        byte[] bytes={1};var data=new MaxApiClient.DownloadedQuestionnaireAttachment(bytes,null);bytes[0]=2;data.bytes()[0]=3;
        assertThat(data.bytes()).containsExactly((byte)1);
    }
}
