package com.example.panel.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeNotePhotoReferencesTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "![Фото](/api/attachments/knowledge-base/photo.jpg)",
        "[Фото](/api/attachments/knowledge-base/photo.jpg)",
        "До\n![Фото](/api/attachments/knowledge-base/%70hoto.jpg)\nПосле"
    })
    void acceptsExactRasterPhotoReferences(String body) {
        assertThat(KnowledgeNotePhotoService.references(body, "photo.jpg")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "![Фото](https://example.org/api/attachments/knowledge-base/photo.jpg)",
        "![Фото](/api/attachments/knowledge-base/other-photo.jpg)",
        "![Фото](/api/attachments/knowledge-base/photo.jpg.bak)",
        "![Фото](/api/attachments/knowledge-base/photo.jpg?query=1)",
        "![Фото](/api/attachments/knowledge-base/photo.jpg#fragment)",
        "![Фото](/api/attachments/knowledge-base/%2e%2e%2fphoto.jpg)",
        "![Фото](/api/attachments/knowledge-base/%ZZphoto.jpg)",
        "![Фото](/api/attachments/knowledge-base/photo.jpg extra)"
    })
    void cannotGrantAccessThroughSubstringOrMalformedReference(String body) {
        assertThat(KnowledgeNotePhotoService.references(body, "photo.jpg")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../photo.jpg", "a/../photo.jpg", "a\\photo.jpg", "photo.svg", "photo.html", "photo.jpg?x", "photo.jpg#x"})
    void rejectsPathsAndActiveContent(String name) {
        assertThat(KnowledgeNotePhotoService.isPhotoName(name)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"report.pdf", "archive.zip", "data.7z", "table.xlsx", "report.docx"})
    void filesAreAccessibleOnlyByExactDownloadLinks(String name) {
        String path="/api/attachments/knowledge-base/"+name;
        assertThat(KnowledgeNotePhotoService.references("[Документ]("+path+")", name)).isTrue();
        assertThat(KnowledgeNotePhotoService.references("![Документ]("+path+")", name)).isFalse();
        assertThat(KnowledgeNotePhotoService.references("[Другой]("+path+".bak)", name)).isFalse();
        assertThat(KnowledgeNotePhotoService.references("[Внешний](https://example.org"+path+")", name)).isFalse();
    }
}
