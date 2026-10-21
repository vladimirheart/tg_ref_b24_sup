package com.example.panel.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.service.*;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.Authentication;

class QuestionnaireMediaLegacyLookupTest {
    @TempDir Path root;
    @ParameterizedTest @ValueSource(strings={"questionnaires/vk/55/general/1/33/1/file.bin","/questionnaires/vk/55/business-7/1/file.html","Questionnaires/VK/55/business-7/1/file.html","questionnaires\\vk\\55\\general\\1\\file.svg","C:\\legacy\\attachments\\questionnaires\\vk\\55\\general\\1\\file.bin","questionnaires/telegram/55/general/1/file.bin","/questionnaires/telegram/55/business-7/1/file.html","Questionnaires/Telegram/55/business-7/1/file.html","questionnaires\\telegram\\55\\general\\1\\file.svg","C:\\legacy\\attachments\\questionnaires\\telegram\\55\\general\\1\\file.bin"})
    void pageDialogsAndUniqueLegacyOwnerCannotOpenTheNewNamespace(String key) throws Exception {
        var permissions=mock(PermissionService.class);var storage=mock(AttachmentObjectStorageService.class);
        var tickets=mock(LegacyTicketIdJdbcGuard.class);var owner=mock(TicketAttachmentOwnerJdbcGuard.class);var actor=mock(Authentication.class);
        when(permissions.hasAuthority(actor,"PAGE_DIALOGS")).thenReturn(true);
        var service=new AttachmentService(permissions,storage,tickets,owner,root.resolve("files").toString(),root.resolve("kb").toString());
        assertThat(service.downloadTicketAttachmentByPath(actor,key).getStatusCode().value()).isEqualTo(404);
        if(!key.startsWith("C:")) {
            assertThat(service.downloadTicketAttachmentByStorageKey(actor,key).getStatusCode().value()).isEqualTo(404);
            String direct=key.replace('\\','/').replaceFirst("^/+","");int slash=direct.indexOf('/');
            assertThat(service.downloadTicketAttachment(actor,direct.substring(0,slash),direct.substring(slash+1),null).getStatusCode().value()).isEqualTo(404);
        }
        verifyNoInteractions(storage,tickets,owner);
    }
}
