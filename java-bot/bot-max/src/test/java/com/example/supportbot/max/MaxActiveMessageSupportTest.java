package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaxActiveMessageSupportTest {

    @Test
    void textMessagePreservesClientLabelTicketAndBody() {
        assertThat(MaxActiveMessageSupport.buildOperatorMessage(
                "20261125-042", "Клиент (@client)", "Текст сообщения", "text", null, 0
        )).isEqualTo("Новый ответ клиента Клиент (@client)\n"
                + "Обращение №20261125-042\n"
                + "Текст сообщения");
    }

    @Test
    void blankTextFallsBackToMessageType() {
        assertThat(MaxActiveMessageSupport.buildOperatorMessage(
                "20261125-043", "1001", "   ", "image", null, 0
        )).isEqualTo("Новый ответ клиента 1001\n"
                + "Обращение №20261125-043\n"
                + "[image]");
    }

    @Test
    void storedAttachmentReferenceTakesPrecedenceOverAttachmentCount() {
        assertThat(MaxActiveMessageSupport.buildOperatorMessage(
                "20261125-044", "1001", "Фото", "image", "attachments/abc.jpg", 3
        )).isEqualTo("Новый ответ клиента 1001\n"
                + "Обращение №20261125-044\n"
                + "Фото\n"
                + "Вложение: attachments/abc.jpg");
    }

    @Test
    void attachmentCountIsShownWhenStoredReferenceIsMissing() {
        assertThat(MaxActiveMessageSupport.buildOperatorMessage(
                "20261125-045", "1001", null, "file", "   ", 2
        )).isEqualTo("Новый ответ клиента 1001\n"
                + "Обращение №20261125-045\n"
                + "[file]\n"
                + "Вложений: 2");
    }
}
