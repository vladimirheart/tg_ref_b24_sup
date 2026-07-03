package com.example.panel.model.dialog;

import java.util.List;

public record DialogMyDialogs(List<DialogListItem> newUnassigned,
                              List<DialogListItem> unanswered,
                              List<DialogListItem> inWork) {

    public static DialogMyDialogs empty() {
        return new DialogMyDialogs(List.of(), List.of(), List.of());
    }
}
