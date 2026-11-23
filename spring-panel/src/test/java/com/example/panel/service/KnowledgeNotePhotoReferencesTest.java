package com.example.panel.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeNotePhotoReferencesTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "![Фото](/api/attachments/knowledge-base/photo.jpg)",
        "До\n![Фото](/api/attachments/knowledge-base/%70hoto.jpg)\nПосле"
    })
    void acceptsExactRasterPhotoReferences(String body) {
        assertThat(KnowledgeNotePhotoService.references(body, "photo.jpg")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[Фото](/api/attachments/knowledge-base/photo.jpg)",
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
}
