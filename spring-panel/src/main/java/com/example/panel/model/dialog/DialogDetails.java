package com.example.panel.model.dialog;

import java.util.List;

public record DialogDetails(DialogListItem summary,
                            List<ChatMessageDto> history) {
}