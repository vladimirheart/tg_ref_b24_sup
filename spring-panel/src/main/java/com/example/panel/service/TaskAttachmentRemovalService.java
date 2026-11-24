package com.example.panel.service;

import com.example.panel.repository.TaskRepository;
import com.example.panel.repository.TaskCommentRepository;
import java.util.ArrayList;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Removes a reference from its owning text; shared binary objects survive while still referenced. */
@Service
public class TaskAttachmentRemovalService {
    private final TaskRepository tasks;
    private final TaskCommentRepository comments;
    private final TaskAttachmentService attachments;
    public TaskAttachmentRemovalService(TaskRepository tasks, TaskCommentRepository comments, TaskAttachmentService attachments) {
        this.tasks = tasks; this.comments = comments; this.attachments = attachments;
    }

    @Transactional
    public String remove(long taskId, String scope, Long commentId, String reference) {
        var task = tasks.findByIdForContentUpdate(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String updated;
        if ("body".equals(scope) && commentId == null) {
            updated = removeReference(task.getBodyHtml(), reference);
            task.setBodyHtml(updated); tasks.saveAndFlush(task);
        } else if ("comment".equals(scope) && commentId != null) {
            var comment = comments.findById(commentId).filter(item -> item.getTask().getId().equals(taskId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            updated = removeReference(comment.getHtml(), reference);
            comment.setHtml(updated); comments.saveAndFlush(comment);
        } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите источник вложения");
        var remaining = new ArrayList<String>(); remaining.add(task.getBodyHtml());
        comments.findByTaskIdOrderByCreatedAtAsc(taskId).forEach(item -> remaining.add(item.getHtml()));
        if (remaining.stream().noneMatch(html -> hasReference(html, reference))) attachments.deleteAfterCommit(taskId, reference);
        return updated;
    }

    static String canonical(String reference) {
        if (reference == null) return "";
        String value = reference;
        try {
            var uri = java.net.URI.create(reference);
            if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getRawPath() != null && uri.getRawPath().startsWith("/api/tasks/"))
                value = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        } catch (IllegalArgumentException invalid) { /* Keep legacy references literal. */ }
        return value.replaceFirst("\\?preview=true$", "");
    }
    private static boolean matches(Element element, String reference) {
        return canonical(element.attr(element.hasAttr("href") ? "href" : "src")).equals(canonical(reference));
    }
    static boolean hasReference(String html, String reference) {
        if (reference == null || reference.isBlank()) return false;
        return Jsoup.parseBodyFragment(html == null ? "" : html).select("a[href], img[src], video[src], audio[src], source[src]")
            .stream().anyMatch(element -> matches(element, reference));
    }
    static String removeReference(String html, String reference) {
        if (reference == null || reference.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var document = Jsoup.parseBodyFragment(html == null ? "" : html); document.outputSettings().prettyPrint(false);
        var matches = document.select("a[href], img[src], video[src], audio[src], source[src]").stream()
            .filter(element -> matches(element, reference)).toList();
        if (matches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Вложение уже удалено. Откройте задачу повторно");
        for (var element : matches) {
            if (!element.parents().contains(document.body())) continue;
            var figure = element.closest("figure[data-task-attachment]");
            if (figure != null) figure.remove();
            else if (element.parent() != null && element.parent().is("a") && element.parent().text().isBlank()) element.parent().remove();
            else element.remove();
        }
        document.select("p").stream().filter(element -> element.text().isBlank() && element.children().isEmpty()).forEach(Element::remove);
        return document.body().html();
    }
}
