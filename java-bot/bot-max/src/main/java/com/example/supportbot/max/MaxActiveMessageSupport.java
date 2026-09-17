package com.example.supportbot.max;

final class MaxActiveMessageSupport {

    private MaxActiveMessageSupport() {
    }

    static String buildOperatorMessage(
            String ticketId,
            String clientDisplayLabel,
            String text,
            String messageType,
            String attachmentRef,
            int attachmentCount
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Новый ответ клиента ").append(clientDisplayLabel).append("\n");
        builder.append("ID заявки: #").append(ticketId).append("\n");
        if (text != null && !text.isBlank()) {
            builder.append(text);
        } else {
            builder.append("[").append(messageType).append("]");
        }
        if (attachmentRef != null && !attachmentRef.isBlank()) {
            builder.append("\nВложение: ").append(attachmentRef);
        } else if (attachmentCount > 0) {
            builder.append("\nВложений: ").append(attachmentCount);
        }
        return builder.toString();
    }
}
