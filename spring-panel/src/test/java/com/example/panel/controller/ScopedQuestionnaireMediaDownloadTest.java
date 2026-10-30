package com.example.panel.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.*;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class ScopedQuestionnaireMediaDownloadTest {
    @ParameterizedTest @CsvSource({"false,success","true,success","false,bytes","true,bytes","false,revoke","true,revoke","false,denied","true,denied"})
    void downloadChecksCurrentGrantAndExactBytesAndAlwaysDownloadsActiveContent(boolean general,String boundary) throws Exception {
        byte[] expected="<html>untrusted document</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash=GeneralTicketDocumentReplyService.sha256(expected),key="questionnaires/telegram/55/"+(general?"general":"business-7")+"/1001/"+"c".repeat(64)+"/33/"+hash+".html";
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);
        var storage=mock(AttachmentObjectStorageService.class);var business=mock(BusinessScopedTicketReadService.class);
        var read=mock(GeneralTicketReadService.class);var access=mock(GeneralTicketAccessService.class);
        var actor=new UsernamePasswordAuthenticationToken("reader","unused",List.of());
        when(business.requireAuthenticatedUserId(actor)).thenReturn(9L);when(access.requireAuthenticatedUserId(actor)).thenReturn(9L);
        var b=new BusinessScopedTicketReadService.AttachmentReference(key,"active.html",hash,(long)expected.length);
        var g=new GeneralTicketReadService.AttachmentReference(key,"active.html",hash,(long)expected.length);
        when(business.findAttachmentByPublicId(actor,"public",33)).thenReturn(Optional.of(b));when(read.attachment(actor,"public",33)).thenReturn(Optional.of(g));
        if(boundary.equals("denied")) {when(business.findAttachmentByPublicId(actor,"public",33)).thenReturn(Optional.empty());when(read.attachment(actor,"public",33)).thenReturn(Optional.empty());}
        if(boundary.equals("revoke")) {when(business.findAttachmentByPublicId(actor,"public",33)).thenReturn(Optional.of(b),Optional.empty());when(read.attachment(actor,"public",33)).thenReturn(Optional.of(g),Optional.empty());}
        byte[] bytes=expected.clone();if(boundary.equals("bytes"))bytes[0]=0;
        var input=spy(new ByteArrayInputStream(bytes));
        when(storage.openDialogAttachmentByStorageKey(key)).thenReturn(new AttachmentObjectStorageService.StoredBinary(key,"text/html",bytes.length,input));
        var businessController=new BusinessScopeReadController(business,storage,flags);var generalController=new GeneralTicketReadController(read,access,flags,storage);
        if(!boundary.equals("success")) {
            assertThatThrownBy(()->{if(general)generalController.attachment("public","33",actor);else businessController.attachment("public","33",actor);})
                .isInstanceOfSatisfying(ResponseStatusException.class,ex->assertThat(ex.getStatusCode().value()).isEqualTo(404));
            if(boundary.equals("denied"))verifyNoInteractions(storage);else verify(input,atLeastOnce()).close();
            return;
        }
        var response=general?generalController.attachment("public","33",actor):businessController.attachment("public","33",actor);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/octet-stream");
        assertThat(response.getHeaders().getFirst("Content-Disposition")).startsWith("attachment;");
        assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getBody().getInputStream().readAllBytes()).isEqualTo(expected);verify(input,atLeastOnce()).close();
    }
}
