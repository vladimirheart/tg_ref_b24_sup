package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaxActiveMessageSupportTest {

    @Test
    void textMessagePreservesClientLabelTicketAndBody() {
        assertThat(MaxActiveMessageSupport.buildOperatorMessage(
                "INC-42", "Клиент (@client)", "Текст сообщения", "text", null, 0
        )).isEqualTo("Новый ответ клиента Клиент (@client)\n"
                + "ID заявки: #INC-42\n"
                + "Текст сообщения");
    }

    @Test
    void blankTextFallsBackToMessageType() {
        assertThat(MaxActiveMessageSupport.buildOperatorMessage(
                "INC-43", "1001", "   ", "image", null, 0
        )).isEqualTo("Новый ответ клиента 1001\n"
                + "ID заявки: #INC-43\n"
                + "[image]");
    }

    @Test
    void storedAttachmentReferenceTakesPrecedenceOverAttachmentCount() {
        assertThat(MaxActiveMessageSupport.buildOperatorMessage(
                "INC-44", "1001", "Фото", "image", "attachments/abc.jpg", 3
        )).isEqualTo("Новый ответ клиента 1001\n"
                + "ID заявки: #INC-44\n"
                + "Фото\n"
                + "Вложение: attachments/abc.jpg");
    }

    @Test
    void attachmentCountIsShownWhenStoredReferenceIsMissing() {
        assertThat(MaxActiveMessageSupport.buildOperatorMessage(
                "INC-45", "1001", null, "file", "   ", 2
        )).isEqualTo("Новый ответ клиента 1001\n"
                + "ID заявки: #INC-45\n"
                + "[file]\n"
                + "Вложений: 2");
    }
}
