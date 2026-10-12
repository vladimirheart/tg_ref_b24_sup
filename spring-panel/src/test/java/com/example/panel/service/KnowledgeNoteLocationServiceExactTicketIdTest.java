package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class KnowledgeNoteLocationServiceExactTicketIdTest {

    @ParameterizedTest
    @ValueSource(strings = {" T-100 ", " null ", " T/100?&%# Ю "})
    void missingExactTicketNeverFallsBackToTrimmedSibling(String ticketId) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DialogLookupReadService lookup = mock(DialogLookupReadService.class);
        when(lookup.findDialog(ticketId, "operator")).thenReturn(Optional.empty());
        KnowledgeNoteLocationService service = new KnowledgeNoteLocationService(
                jdbc, mock(IikoDepartmentLocationCatalogService.class), mock(KnowledgeNoteService.class), lookup);

        assertThat(service.listDialogNotes(ticketId, "operator")).isEmpty();

        verify(lookup).findDialog(ticketId, "operator");
        verify(lookup, never()).findDialog(ticketId.trim(), "operator");
        verifyNoInteractions(jdbc);
    }

    @Test
    void missingAndBlankTicketIdsDoNotQueryNotesOrDialogLookup() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DialogLookupReadService lookup = mock(DialogLookupReadService.class);
        KnowledgeNoteLocationService service = new KnowledgeNoteLocationService(
                jdbc, mock(IikoDepartmentLocationCatalogService.class), mock(KnowledgeNoteService.class), lookup);

        for (String ticketId : new String[] {null, "", " ", "\t\n"}) {
            assertThat(service.listDialogNotes(ticketId, "operator")).isEmpty();
        }
        verifyNoInteractions(jdbc, lookup);
    }
}
