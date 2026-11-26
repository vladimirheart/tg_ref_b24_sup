package com.example.panel.controller;

import com.example.panel.service.*;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DialogInternalAttachmentController.class)
@Import(DialogInternalAttachmentControllerWebMvcTest.Security.class)
class DialogInternalAttachmentControllerWebMvcTest {
    @TestConfiguration @EnableMethodSecurity static class Security {}
    @Autowired MockMvc mvc;
    @MockBean DialogOperatorCollaborationService messages;
    @MockBean DialogInternalAttachmentService files;
    final String pub="a".repeat(32),base="/api/operator-dialogs/"+pub+"/internal-messages";
    final UUID fileId=UUID.randomUUID();
    final UiEventTicketScope scope=new UiEventTicketScope(-42L," raw ",pub,"BUSINESS",11L);
    DialogOperatorCollaborationService.Context state;
    MockMultipartFile command(String raw){
        String body="{\"userId\":\"-42\",\"ticketId\":\""+raw+"\",\"ticketPublicId\":\""+pub+"\",\"scopeKind\":\"BUSINESS\","
            +"\"businessId\":\"11\",\"body\":\"\",\"requestId\":\""+UUID.randomUUID()+"\"}";
        return new MockMultipartFile("command","","application/json",body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @BeforeEach void setup() throws Exception {
        state=new DialogOperatorCollaborationService.Context(scope,0,"open",true);
        when(messages.find(eq(pub),any())).thenReturn(Optional.of(state));
        when(files.send(eq(state),any(),any(),eq(""),anyList())).thenReturn(true);
        when(files.open(eq(pub),eq(fileId),any())).thenAnswer(i->new DialogInternalAttachmentService.Opened(
            new DialogOperatorCollaborationService.FileRecord(fileId,1,pub+"/"+fileId,"Файл.svg","application/octet-stream",5,"a".repeat(64)),
            new AttachmentObjectStorageService.StoredBinary("private","text/html",5,new ByteArrayInputStream("ABCDE".getBytes()))));
    }
    @Test void multipartRequiresDialogsCsrfOperateAndExactOwner() throws Exception {
        var binary=new MockMultipartFile("files","file.txt","text/plain",new byte[]{1});
        mvc.perform(multipart(base+"/files").file(command(" raw ")).file(binary).with(user("operator").authorities(()->"PAGE_DIALOGS")))
            .andExpect(status().isForbidden());
        mvc.perform(multipart(base+"/files").file(command(" raw ")).file(binary).with(user("operator")).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(multipart(base+"/files").file(command("wrong")).file(binary).with(user("operator").authorities(()->"PAGE_DIALOGS")).with(csrf()))
            .andExpect(status().isConflict());
        verify(files,never()).send(any(),any(),any(),any(),any());
    }
    @Test void fileOnlyBatchSupportsSeveralFilesWithoutClientReply() throws Exception {
        mvc.perform(multipart(base+"/files").file(command(" raw "))
            .file(new MockMultipartFile("files","a.txt","text/plain",new byte[]{1}))
            .file(new MockMultipartFile("files","b.txt","text/plain",new byte[]{2}))
            .with(user("operator").authorities(()->"PAGE_DIALOGS")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(files).send(eq(state),any(),any(),eq(""),argThat(list->list.size()==2));
    }
    @Test void unsafePayloadIsDownloadOnlyAndHasNoPublicCache() throws Exception {
        mvc.perform(get(base+"/attachments/"+fileId).param("preview","true").with(user("viewer").authorities(()->"PAGE_DIALOGS")))
            .andExpect(status().isOk()).andExpect(content().bytes("ABCDE".getBytes()))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(header().string("Content-Security-Policy","sandbox; default-src 'none'"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")));
    }
    @Test void mediaRangesAndInvalidRangeCloseTheStream() throws Exception {
        when(files.open(eq(pub),eq(fileId),any())).thenAnswer(i->new DialogInternalAttachmentService.Opened(
            new DialogOperatorCollaborationService.FileRecord(fileId,1,pub+"/"+fileId,"sound.mp3","audio/mpeg",5,"a".repeat(64)),
            new AttachmentObjectStorageService.StoredBinary("private","audio/mpeg",5,new ByteArrayInputStream("ABCDE".getBytes()))));
        mvc.perform(get(base+"/attachments/"+fileId).param("preview","true").header("Range","bytes=1-3")
            .with(user("viewer").authorities(()->"PAGE_DIALOGS")))
            .andExpect(status().isPartialContent()).andExpect(content().bytes("BCD".getBytes()))
            .andExpect(header().string("Content-Range","bytes 1-3/5")).andExpect(header().string("Content-Length","3"));
        mvc.perform(get(base+"/attachments/"+fileId).param("preview","true").header("Range","bytes=99-100")
            .with(user("viewer").authorities(()->"PAGE_DIALOGS"))).andExpect(status().isRequestedRangeNotSatisfiable());
    }
}

