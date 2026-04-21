package com.example.panel.service;

import com.example.panel.model.dialog.DialogDetails;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DialogDetailsReadService {

    private final DialogLookupReadService dialogLookupReadService;
    private final DialogConversationReadService dialogConversationReadService;

    public DialogDetailsReadService(DialogLookupReadService dialogLookupReadService,
                                    DialogConversationReadService dialogConversationReadService) {
        this.dialogLookupReadService = dialogLookupReadService;
        this.dialogConversationReadService = dialogConversationReadService;
    }

    public Optional<DialogDetails> loadDialogDetails(String ticketId, Long channelId, String operator) {
        return dialogLookupReadService.findDialog(ticketId, operator).map(item -> new DialogDetails(
                item,
                dialogConversationReadService.loadHistory(ticketId, channelId),
                dialogConversationReadService.loadTicketCategories(ticketId)
        ));
    }
}
