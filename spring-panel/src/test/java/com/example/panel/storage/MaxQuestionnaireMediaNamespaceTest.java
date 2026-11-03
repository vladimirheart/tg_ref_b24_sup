package com.example.panel.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.sql.ResultSet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Reserve the namespace now; neither legacy reads nor canonical MAX admission is opened by the byte writer. */
class MaxQuestionnaireMediaNamespaceTest {
    @TempDir Path root;
    @ParameterizedTest @ValueSource(strings={"questionnaires/max/55/general/1/file.bin","/questionnaires/max/55/business-7/1/file.html","Questionnaires/MAX/55/business-7/1/file.html","questionnaires\\max\\55\\general\\1\\file.svg","C:\\legacy\\attachments\\questionnaires\\max\\55\\general\\1\\file.bin"})
    void maxNamespaceCannotUseAnyLegacyAttachmentRead(String key) throws Exception {
        var regression=new QuestionnaireMediaLegacyLookupTest();regression.root=root;
        regression.pageDialogsAndUniqueLegacyOwnerCannotOpenTheNewNamespace(key);
        if(!key.startsWith("C:"))assertThat(QuestionnaireMediaStorageProof.isScoped(key)).isTrue();
    }
    @Test void canonicalMaxReadProofIsStillClosedUntilTheConsumerProtocolIsImplemented() throws Exception {
        var row=mock(ResultSet.class);when(row.getString("proof_source")).thenReturn("USER_SELECTION");when(row.getString("proof_platform")).thenReturn("max");
        assertThat(QuestionnaireMediaStorageProof.matches(row,"questionnaires/max/55/general/1/file.bin")).isFalse();
    }
}
