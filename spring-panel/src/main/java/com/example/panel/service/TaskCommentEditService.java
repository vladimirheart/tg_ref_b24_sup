package com.example.panel.service;

import com.example.panel.entity.TaskComment;
import com.example.panel.repository.TaskRepository;
import com.example.panel.repository.TaskCommentRepository;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskCommentEditService {
    private final TaskRepository tasks;
    private final TaskCommentRepository comments;
    public TaskCommentEditService(TaskRepository tasks, TaskCommentRepository comments) { this.tasks = tasks; this.comments = comments; }

    @Transactional
    public TaskComment edit(long taskId, long commentId, String actor, String html, String expectedHtml) {
        tasks.findByIdForContentUpdate(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var comment = comments.findById(commentId).filter(item -> item.getTask().getId().equals(taskId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!Objects.equals(actor, comment.getAuthor())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Можно редактировать только свои комментарии");
        if (!Objects.equals(expectedHtml, comment.getHtml())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Комментарий изменился. Откройте задачу повторно");
        var original = Jsoup.parseBodyFragment(comment.getHtml() == null ? "" : comment.getHtml());
        original.outputSettings().prettyPrint(false);
        String selector = "figure[data-task-attachment], a[data-task-attachment], a[download], img, video, audio";
        var files = original.select(selector).stream().filter(node -> node.parents().stream().noneMatch(parent -> parent.is(selector))).toList();
        String attachments = files.stream().map(org.jsoup.nodes.Element::outerHtml).collect(java.util.stream.Collectors.joining());
        var text = Jsoup.parseBodyFragment(html == null ? "" : html); text.outputSettings().prettyPrint(false);
        text.select(selector).remove();
        String clean = Jsoup.clean(text.body().html(), "", Safelist.relaxed().removeTags("img").addTags("h1", "h2", "h3", "s", "pre", "code"), text.outputSettings());
        if (Jsoup.parseBodyFragment(clean).text().isBlank() && attachments.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Комментарий не может быть пустым");
        comment.setHtml(clean + attachments);
        return comments.saveAndFlush(comment);
    }
}
