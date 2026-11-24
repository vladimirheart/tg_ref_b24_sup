package com.example.panel.service;

import com.example.panel.entity.Task;
import com.example.panel.entity.TaskComment;
import com.example.panel.repository.TaskRepository;
import com.example.panel.repository.TaskCommentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskContentEditingTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final TaskCommentRepository comments = mock(TaskCommentRepository.class);
    private final TaskAttachmentService attachments = mock(TaskAttachmentService.class);
    private final TaskAttachmentRemovalService removal = new TaskAttachmentRemovalService(tasks, comments, attachments);
    private final TaskCommentEditService edits = new TaskCommentEditService(tasks, comments);
    private final String url = "/api/tasks/41/attachments/12345678-1234-1234-1234-123456789abc_photo.png";
    private final String figure = "<figure data-task-attachment='true'><a href='" + url + "' download='photo.png'><img src='" + url + "?preview=true'></a><figcaption>Фото</figcaption></figure>";
    private Task task; private TaskComment comment;
    @BeforeEach void prepare() {
        task = new Task(); task.setId(41L); task.setBodyHtml("<p><strong>Описание</strong></p>" + figure);
        comment = new TaskComment(); comment.setId(7L); comment.setTask(task); comment.setAuthor("anna"); comment.setCreatedAt(OffsetDateTime.now()); comment.setHtml("<p>Комментарий</p>" + figure);
        when(tasks.findByIdForContentUpdate(41L)).thenReturn(Optional.of(task));
        when(comments.findById(7L)).thenReturn(Optional.of(comment));
        when(comments.findByTaskIdOrderByCreatedAtAsc(41L)).thenReturn(List.of(comment));
        when(comments.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }
    @Test void removesOnlySelectedBodyReferenceAndKeepsSharedBinary() {
        comment.setHtml(comment.getHtml().replace("href='" + url, "href='https://panel.example" + url).replace("src='" + url,"src='https://panel.example" + url));
        String html = removal.remove(41, "body", null, url);
        assertThat(html).contains("<strong>Описание</strong>").doesNotContain("figure", "photo.png");
        assertThat(comment.getHtml()).contains(url);
        verify(attachments, never()).deleteAfterCommit(anyLong(), any());
    }
    @Test void lastReferenceIsDeletedOnlyAfterCommitAndOtherTextSurvives() {
        task.setBodyHtml("<p>Описание</p>");
        String html = removal.remove(41, "comment", 7L, url);
        assertThat(html).isEqualTo("<p>Комментарий</p>");
        verify(attachments).deleteAfterCommit(41, url);
        verify(comments).saveAndFlush(comment);
    }
    @Test void legacyClipboardImageCanBeRemovedWithoutRemovingText() {
        assertThat(TaskAttachmentRemovalService.canonical("https:example")).isEqualTo("https:example");
        String image = "data:image/png;base64,iVBORw0KGgo=";
        task.setBodyHtml("<p>До<img src='" + image + "'>После</p>");
        assertThat(removal.remove(41,"body",null,image)).isEqualTo("<p>ДоПосле</p>");
    }
    @Test void missingOrForeignCommentDoesNotMutateTaskOrStorage() {
        var other = new Task(); other.setId(42L); comment.setTask(other);
        assertStatus(() -> removal.remove(41,"comment",7L,url),404);
        assertStatus(() -> removal.remove(41,"body",null,"/api/tasks/42/attachments/no-such-file"),404);
        verify(tasks, never()).saveAndFlush(any()); verify(comments, never()).saveAndFlush(any()); verifyNoInteractions(attachments);
    }
    @Test void editOwnTextPreservesOriginalMediaAndFormattingAndDropsActiveHtml() {
        String original = comment.getHtml();
        var saved = edits.edit(41,7,"anna","<h2>Исправлено</h2><ul><li><strong>Шаг</strong></li></ul><script>alert(1)</script><img src='https://foreign.invalid/new.png'>",original);
        assertThat(saved.getHtml()).contains("<h2>Исправлено</h2>", "<strong>Шаг</strong>", url, "figcaption").doesNotContain("<script", "foreign.invalid");
        assertThat(saved.getCreatedAt()).isEqualTo(comment.getCreatedAt());
        assertThat(org.jsoup.Jsoup.parseBodyFragment(saved.getHtml()).select("figure")).hasSize(1);
    }
    @Test void editRequiresOriginalAuthorAndCurrentHtmlAndTaskOwnership() {
        String original = comment.getHtml();
        assertStatus(() -> edits.edit(41,7,"victor","<p>Чужой</p>",original),403);
        assertStatus(() -> edits.edit(41,7,"anna","<p>Правка</p>","stale"),409);
        var other = new Task(); other.setId(42L); comment.setTask(other);
        assertStatus(() -> edits.edit(41,7,"anna","<p>Правка</p>",original),404);
        verify(comments,never()).saveAndFlush(any());
    }
    @Test void emptyEditRequiresAtLeastOneExistingAttachment() {
        comment.setHtml("<p>Текст</p>");
        assertStatus(() -> edits.edit(41,7,"anna","<p><br></p>",comment.getHtml()),400);
        comment.setHtml(figure);
        assertThat(edits.edit(41,7,"anna","",figure).getHtml()).contains(url);
    }
    private void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, int status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,error -> assertThat(error.getStatusCode().value()).isEqualTo(status));
    }
}
