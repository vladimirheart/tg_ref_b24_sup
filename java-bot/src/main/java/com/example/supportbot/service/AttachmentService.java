package com.example.supportbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final HexFormat HEX = HexFormat.of();

    private final Path attachmentsRoot;

    public AttachmentService(Path attachmentsRoot) {
        this.attachmentsRoot = attachmentsRoot;
    }

    public Path store(String channelPublicId, String extension, InputStream dataStream) throws IOException {
        OffsetDateTime now = OffsetDateTime.now();
        Path dateDir = attachmentsRoot.resolve(channelPublicId).resolve(DATE_PREFIX.format(now));
        Files.createDirectories(dateDir);

        String filename = buildFileName(extension);
        Path target = dateDir.resolve(filename);
        Files.copy(dataStream, target);
        log.info("Saved attachment {}", target);
        return target;
    }

    private String buildFileName(String extension) {
        byte[] randomBytes = new byte[12];
        ThreadLocalRandom.current().nextBytes(randomBytes);
        String base = HEX.formatHex(randomBytes);
        if (extension != null && !extension.isBlank()) {
            if (!extension.startsWith(".")) {
                return base + "." + extension;
            }
            return base + extension;
        }
        return base;
    }
}
