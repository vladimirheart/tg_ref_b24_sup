package com.example.panel.passports;

import com.example.panel.passports.ObjectPassportDocumentStorage.FileInfo;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ObjectPassportDocument(UUID id, long passportId, int revision, String title, String category,
        LocalDate eventDate, String description, Map<String, String> fields, boolean archived, FileInfo file,
        String action, String actor, OffsetDateTime changedAt) {}
