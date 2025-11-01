package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.entity.ChatHistory;
import com.example.panel.entity.WebFormSession;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormQuestion;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ChatHistoryRepository;
import com.example.panel.repository.WebFormSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Transactional
public class PublicFormService {

    private static final Logger log = LoggerFactory.getLogger(PublicFormService.class);

    private final ChannelRepository channelRepository;
    private final WebFormSessionRepository sessionRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ObjectMapper objectMapper;

    public PublicFormService(ChannelRepository channelRepository,
                             WebFormSessionRepository sessionRepository,
                             ChatHistoryRepository chatHistoryRepository,
                             ObjectMapper objectMapper) {
        this.channelRepository = channelRepository;
        this.sessionRepository = sessionRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Optional<PublicFormConfig> loadConfig(String channelRef) {
        return resolveChannel(channelRef).map(this::toConfig);
    }

    public PublicFormSessionDto createSession(String channelRef, PublicFormSubmission submission) {
        Channel channel = resolveChannel(channelRef)
                .orElseThrow(() -> new IllegalArgumentException("Канал не найден"));
        if (!StringUtils.hasText(submission.message())) {
            throw new IllegalArgumentException("Опишите проблему");
        }

        Map<String, String> answers = sanitizeAnswers(submission.answers());
        PublicFormConfig config = toConfig(channel);
        String combinedMessage = buildCombinedMessage(config, answers, submission.message());

        WebFormSession session = new WebFormSession();
        session.setChannel(channel);
        session.setToken(generateToken());
        session.setTicketId(generateTicketId());
        session.setAnswersJson(writeJson(answers));
        session.setClientName(resolveClientName(submission, answers));
        session.setClientContact(trim(submission.clientContact()));
        session.setUsername(StringUtils.hasText(submission.username()) ? submission.username().trim() : "web_form");
        OffsetDateTime now = OffsetDateTime.now();
        session.setCreatedAt(now);
        session.setLastActiveAt(now);
        WebFormSession saved = sessionRepository.save(session);

        ChatHistory history = new ChatHistory();
        history.setChannel(channel);
        history.setTicketId(saved.getTicketId());
        history.setSender("user");
        history.setMessage(combinedMessage);
        history.setTimestamp(now);
        history.setMessageType("text");
        chatHistoryRepository.save(history);

        return new PublicFormSessionDto(
                saved.getToken(),
                saved.getTicketId(),
                channel.getId(),
                channel.getPublicId(),
                saved.getClientName(),
                saved.getClientContact(),
                saved.getUsername(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Optional<PublicFormSessionDto> findSession(String channelRef, String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return resolveChannel(channelRef)
                .flatMap(channel -> sessionRepository.findByToken(token)
                        .filter(session -> session.getChannel() != null && session.getChannel().getId().equals(channel.getId()))
                        .map(session -> new PublicFormSessionDto(
                                session.getToken(),
                                session.getTicketId(),
                                channel.getId(),
                                channel.getPublicId(),
                                session.getClientName(),
                                session.getClientContact(),
                                session.getUsername(),
                                session.getCreatedAt()
                        )));
    }

    private Optional<Channel> resolveChannel(String channelRef) {
        if (!StringUtils.hasText(channelRef)) {
            return Optional.empty();
        }
        String trimmed = channelRef.trim();
        Optional<Channel> direct = channelRepository.findByPublicIdIgnoreCase(trimmed);
        if (direct.isPresent()) {
            return direct;
        }
        if (trimmed.chars().allMatch(Character::isDigit)) {
            try {
                long id = Long.parseLong(trimmed);
                return channelRepository.findById(id);
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        return Optional.empty();
    }

    private PublicFormConfig toConfig(Channel channel) {
        List<PublicFormQuestion> questions = parseQuestions(channel);
        String publicId = StringUtils.hasText(channel.getPublicId())
                ? channel.getPublicId()
                : (channel.getId() != null ? channel.getId().toString() : null);
        return new PublicFormConfig(channel.getId(), publicId, channel.getChannelName(), questions);
    }

    private List<PublicFormQuestion> parseQuestions(Channel channel) {
        String payload = channel.getQuestionsCfg();
        if (!StringUtils.hasText(payload)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (!root.isArray()) {
                return List.of();
            }
            AtomicInteger index = new AtomicInteger(0);
            return objectMapper.convertValue(root, new TypeReference<List<Map<String, Object>>>() {})
                    .stream()
                    .map(entry -> normalizeQuestion(entry, index.incrementAndGet()))
                    .sorted((a, b) -> Integer.compare(Optional.ofNullable(a.order()).orElse(0), Optional.ofNullable(b.order()).orElse(0)))
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to parse questions configuration for channel {}: {}", channel.getId(), ex.getMessage());
            return List.of();
        }
    }

    private PublicFormQuestion normalizeQuestion(Map<String, Object> raw, int index) {
        String id = value(raw.getOrDefault("id", "q" + index));
        String text = value(raw.get("text"));
        String type = value(raw.getOrDefault("type", "custom"));
        Integer order = raw.get("order") instanceof Number number ? number.intValue() : index;
        Map<String, Object> metadata = raw.entrySet().stream()
                .filter(entry -> !List.of("id", "text", "type", "order").contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
        return new PublicFormQuestion(id, text, type, order, metadata);
    }

    private Map<String, String> sanitizeAnswers(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                result.put(key.trim(), value.trim());
            }
        });
        return result;
    }

    private String buildCombinedMessage(PublicFormConfig config, Map<String, String> answers, String message) {
        List<String> summary = config.questions().stream()
                .filter(q -> answers.containsKey(q.id()))
                .map(q -> formatSummaryLine(q, answers.get(q.id())))
                .filter(StringUtils::hasText)
                .toList();
        StringBuilder builder = new StringBuilder();
        if (!summary.isEmpty()) {
            builder.append("Ответы формы:\n");
            summary.forEach(line -> builder.append(line).append('\n'));
            if (StringUtils.hasText(message)) {
                builder.append('\n');
            }
        }
        if (StringUtils.hasText(message)) {
            builder.append(message.trim());
        }
        return builder.length() > 0 ? builder.toString() : message;
    }

    private String formatSummaryLine(PublicFormQuestion question, String answer) {
        if (!StringUtils.hasText(answer)) {
            return null;
        }
        String label = StringUtils.hasText(question.text()) ? question.text().trim() : "Вопрос";
        return label + ": " + answer.trim();
    }

    private String writeJson(Map<String, String> answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (Exception ex) {
            log.warn("Failed to serialize answers: {}", ex.getMessage());
            return "{}";
        }
    }

    private String resolveClientName(PublicFormSubmission submission, Map<String, String> answers) {
        if (StringUtils.hasText(submission.clientName())) {
            return submission.clientName().trim();
        }
        for (String key : List.of("client_name", "name", "full_name")) {
            if (answers.containsKey(key) && StringUtils.hasText(answers.get(key))) {
                return answers.get(key);
            }
        }
        return "Клиент веб-формы";
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }

    private String generateTicketId() {
        return "web-" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String value(Object value) {
        return value != null ? value.toString() : null;
    }
}
