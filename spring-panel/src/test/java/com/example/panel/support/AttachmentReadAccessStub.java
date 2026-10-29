package com.example.panel.support;

import com.example.panel.storage.LegacyAttachmentReadAccess;
import static org.mockito.Mockito.*;

/** Storage/URL fixtures isolate this new collaborator; security regressions use the real SQL guard. */
public final class AttachmentReadAccessStub {
    private AttachmentReadAccessStub() { }
    public static LegacyAttachmentReadAccess permitted() {
        var access=mock(LegacyAttachmentReadAccess.class);
        lenient().when(access.canRead(any(),any())).thenReturn(true);
        return access;
    }
}
