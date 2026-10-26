package com.example.panel.model.dialog;

import java.util.List;

public record DialogListPage(List<DialogListItem> dialogs, long totalItems, int page, int pageSize) {
    public long totalPages() {
        return totalItems == 0 ? 0 : (totalItems + pageSize - 1) / pageSize;
    }
}
