package com.example.panel.model.dialog;

public record DialogPreviousHistoryPage(DialogPreviousHistoryBatch batch,
                                        Integer nextOffset,
                                        boolean hasMore) {
}
