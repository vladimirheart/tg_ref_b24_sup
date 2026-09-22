package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UiNavigationDialogsSourceContractTest {
  private String read(String path) throws IOException { return Files.readString(Path.of(path), StandardCharsets.UTF_8).replace("\r\n", "\n"); }

  @Test void sidebarDropsReorderAndUsesUserCardForActions() throws IOException {
    String js = read("src/main/resources/static/js/sidebar.js");
    String html = read("src/main/resources/templates/fragments/navbar.html");
    assertThat(js).doesNotContain("sidebarNavOrder").doesNotContain("editSidebarOrderBtn").doesNotContain("applyDraggableState");
    assertThat(html).contains("data-sidebar-action-trigger").contains("editSelf").doesNotContain("sidebar-action-menu-orb").doesNotContain("editSidebarOrderBtn");
  }

  @Test void dialogsKeepListOnlyPreferenceAndCompactTheList() throws IOException {
    String js = read("src/main/resources/static/js/dialogs.js");
    String html = read("src/main/resources/templates/dialogs/index.html");
    assertThat(js).doesNotContain("exitListOnlyMode").doesNotContain("STORAGE_COMPACT_MODE").contains("item.key !== 'select'").contains("sanitizeDialogProblemLabel").contains("dialog-open-icon");
    assertThat(html).doesNotContain("id=\"dialogCompactToggle\"").contains("data-column-key=\"select\"").contains("dialog-open-icon").contains("page-header-title-row");
  }

  @Test void selfProfileDeepLinkUsesExistingAuthorizedEditor() throws IOException {
    String auth = read("src/main/resources/static/js/auth-management.js");
    assertThat(auth).contains("openRequestedSelfEditor").contains("params.get('editSelf')").contains("this.openUserModal(userId)");
  }
}
