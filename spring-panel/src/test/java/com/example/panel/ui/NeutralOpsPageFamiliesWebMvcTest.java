package com.example.panel.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Визуальные модели для экранов, не покрытых существующими controller MVC fixtures.
 * Это template-render QA; проверки прав и бизнес-сценариев остаются в controller tests. */
@WebMvcTest(NeutralOpsPageFamiliesWebMvcTest.FixtureController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(NeutralOpsPageFamiliesWebMvcTest.FixtureController.class)
class NeutralOpsPageFamiliesWebMvcTest {
    private static final List<String> VIEWS = List.of("analytics/iiko-api-monitoring", "businesses/ai-source",
        "businesses/ai-sources", "businesses/dialog-ai", "businesses/dialog-assignment", "clients/profile",
        "clients/unblock_requests", "general/new", "general/work", "incidents/index", "knowledge/editor",
        "knowledge/note-editor", "knowledge/notes", "ui-kit/index");
    @Autowired private MockMvc mvc;

    @Test void remainingPageFamiliesRenderWithoutWorkingDatabase() throws Exception {
        for (String view : VIEWS) {
            mvc.perform(get("/__visual-family").param("view", view).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/css/app.css?v=20261022-neutral-ops-r9")))
                .andExpect(content().string(containsString("data-ui-page=")));
        }
    }

    public enum Kind { KNOWLEDGE }
    public enum SourceStatus { DRAFT }
    @Controller static class FixtureController {
        @GetMapping("/__visual-family") String render(@RequestParam String view, Model model) {
            if (!VIEWS.contains(view)) throw new IllegalArgumentException("Unknown fixture");
            for (String flag : List.of("canManageTasks", "canViewIncidents", "canViewClients", "canViewPassports",
                    "canEditKnowledgeBase", "canViewScopedAiSources", "canViewAnalytics", "canViewSettings"))
                model.addAttribute(flag, true);
            model.addAttribute("sidebarUserDisplayName", "Оператор поддержки");
            model.addAttribute("sidebarUserUsername", "operator");
            model.addAttribute("activePage", "dialogs");
            switch (view) {
                case "clients/profile" -> {
                    Map<String,Object> profile = map("header", map("userId", 1001L, "sourceKey", "telegram", "sourceLabel", "Telegram", "platform", "telegram", "clientName", "Александр Константинопольский", "username", "alexander"),
                        "blacklist", map("blacklisted", false, "unblockRequested", false), "stats", map("total", 24, "resolved", 21, "pending", 3, "formattedTime", "2 ч 35 мин"),
                        "clientStatus", "Активный", "averageRating", 4.8, "ratingTotal", 24);
                    for (String key : List.of("phonesTelegram", "phonesManual", "blacklistHistory", "periodComparisons", "ratingStats", "categoryStats", "locationStats", "tickets", "usernameHistory")) profile.put(key, List.of());
                    model.addAttribute("profile", profile); model.addAttribute("clientStatuses", List.of("Активный", "Новый"));
                }
                case "clients/unblock_requests" -> { model.addAttribute("requests", List.of()); model.addAttribute("availableStatuses", List.of("pending", "approved")); }
                case "businesses/ai-source", "businesses/ai-sources" -> {
                    model.addAllAttributes(map("business", map("id", 1, "displayName", "Служба поддержки"), "businesses", List.of(map("id", 1, "displayName", "Служба поддержки")), "sources", List.of(),
                        "canConfigure", true, "isNew", true, "sourceKind", Kind.KNOWLEDGE, "sourceStatus", SourceStatus.DRAFT, "sourceTitle", "Инструкция оператора", "sourceBody", "Проверьте статус обращения и связь с клиентом.", "history", List.of(), "revision", 1));
                }
                case "businesses/dialog-ai" -> model.addAllAttributes(map("businessId", 1, "canOperate", false, "canCurate", false,
                    "control", map("aiDisabled", false, "autoReplyBlocked", true), "publicId", "TEST-1", "events", List.of(), "library", List.of(), "candidates", List.of(), "messages", List.of()));
                case "businesses/dialog-assignment" -> {
                    Map<String,Object> scope = map("businessId", 1, "ticketPublicId", "TEST-1", "ticketUserId", 1001L, "ticketId", "TEST-1");
                    model.addAttribute("work", map("status", "open", "revision", 1, "categories", List.of("Техническая поддержка"), "canOperate", false, "scope", scope));
                    model.addAttribute("state", map("revision", 1, "responsible", null, "canOperate", false, "scope", scope));
                    for (String key : List.of("workEvents", "events", "candidates", "members", "memberCandidates", "participantEvents")) model.addAttribute(key, List.of());
                }
                case "general/new" -> model.addAttribute("requestKey", "qa-request");
                case "general/work" -> {
                    model.addAllAttributes(map("status", "open", "canOperate", false, "publicId", "GENERAL-TEST-1", "revision", 1));
                    for (String key : List.of("participants", "participantCandidates", "participantEvents", "workIntervals", "replies", "replyChangeTargets", "replyChanges", "documentReplies")) model.addAttribute(key, List.of());
                }
                case "knowledge/editor" -> model.addAttribute("article", map("id", null, "title", "Инструкция оператора", "author", "Служба поддержки", "status", "draft", "department", "Поддержка", "externalSource", null, "externalId", null, "externalUrl", null, "externalUpdatedAt", null, "updatedAt", null, "articleType", "Инструкция", "direction", "", "directionSubtype", "", "summary", "Порядок обработки обращения", "contentHtml", "<p>Уточните проблему и проверьте доступность сервиса.</p>", "content", "Уточните проблему и проверьте доступность сервиса.", "attachments", List.of()));
                case "knowledge/note-editor" -> {
                    model.addAttribute("note", map("id", null, "title", "Рабочая заметка", "body", "Контекст обращения", "customFields", Map.of(), "knowledgeArticleIds", List.of(), "objectPassportIds", List.of(), "createdBy", null, "createdAt", null, "updatedAt", null));
                    model.addAttribute("canLinkPassports", false);
                    for (String key : List.of("articles", "passports", "locationOptions", "selectedLocationNames")) model.addAttribute(key, List.of());
                }
                case "knowledge/notes" -> model.addAttribute("notes", List.of());
                default -> { }
            }
            return view;
        }
    }

    private static Map<String,Object> map(Object... values) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int i=0; i<values.length; i+=2) result.put((String)values[i], values[i+1]);
        return result;
    }
}
