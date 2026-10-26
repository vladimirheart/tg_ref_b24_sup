package com.example.panel.controller;

import com.example.panel.service.DialogListReadService;
import com.example.panel.model.dialog.DialogListQuery;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;

import java.util.Map;

@RestController
@RequestMapping("/api/dialogs")
public class DialogListController {

    private final DialogListReadService dialogListReadService;

    public DialogListController(DialogListReadService dialogListReadService) {
        this.dialogListReadService = dialogListReadService;
    }

    @GetMapping
    public Map<String, Object> list(Authentication authentication,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
                                    @RequestParam(defaultValue = "") String search,
                                    @RequestParam(defaultValue = "") String status,
                                    @RequestParam(defaultValue = "all") String view,
                                    @RequestParam(name = "sla_window_minutes", required = false) Integer slaWindow,
                                    @RequestParam(name = "sort_mode", defaultValue = "default") String sortMode) {
        String operator = authentication != null ? authentication.getName() : null;
        return dialogListReadService.loadListPayload(operator,
                new DialogListQuery(page, pageSize, search, status, view, slaWindow, sortMode, null), true);
    }

    /** Read-only POST avoids URL-length limits and preserves opaque snoozed IDs without comma splitting. */
    @PostMapping("/query")
    @PreAuthorize("hasAuthority('PAGE_DIALOGS')")
    public Map<String, Object> query(Authentication authentication, @RequestBody QueryRequest request) {
        return dialogListReadService.loadListPayload(authentication.getName(), request.query(), request.includeOverview());
    }

    public record QueryRequest(DialogListQuery query, boolean includeOverview) {
        public QueryRequest {
            if (query == null) query = DialogListQuery.defaults();
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badQuery() {
        return Map.of("success", false, "error", "Некорректные параметры списка диалогов");
    }
}
