package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AiRetrievalService {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Set<String> STOP = Set.of("и", "в", "на", "не", "что", "как", "для", "или", "по", "из", "к", "у", "о", "об", "the", "a", "an", "to", "of", "in", "on", "for", "and", "or", "is", "are", "be");

    private final JdbcTemplate jdbcTemplate;
    private final AiIntentService aiIntentService;

    public AiRetrievalService(JdbcTemplate jdbcTemplate, AiIntentService aiIntentService) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiIntentService = aiIntentService;
    }

    public RetrievalResult retrieve(String ticketId, String query, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        RetrievalContext context = buildContext(ticketId, query);
        if (!StringUtils.hasText(context.normalizedQuery()) || context.queryTokens().isEmpty()) {
            return new RetrievalResult(context, List.of(), new ConsistencyCheck(false, false, 0, "empty_query"));
        }

        List<DocumentVector> documents = new ArrayList<>();
        documents.addAll(loadMemoryDocuments(safeLimit * 16));
        documents.addAll(loadKnowledgeDocuments(safeLimit * 12));
        if (documents.isEmpty()) {
            return new RetrievalResult(context, List.of(), new ConsistencyCheck(false, false, 0, "no_evidence"));
        }

        Map<String, Integer> documentFrequency = buildDocumentFrequency(documents, context.queryTokens());
        double averageDocumentLength = documents.stream()
                .mapToInt(vector -> Math.max(1, vector.tokens().size()))
                .average()
                .orElse(1d);

        List<Candidate> scored = new ArrayList<>();
        for (DocumentVector vector : documents) {
            Candidate candidate = scoreDocument(context, vector, documentFrequency, averageDocumentLength, documents.size());
            if (candidate != null) {
                scored.add(candidate);
            }
        }

        scored.sort(Comparator
                .comparingDouble(Candidate::score).reversed()
                .thenComparing(candidate -> "knowledge".equals(candidate.source()) ? 0 : 1)
                .thenComparing(Candidate::title, Comparator.nullsLast(String::compareToIgnoreCase)));

        List<Candidate> limited = scored.size() > safeLimit
                ? new ArrayList<>(scored.subList(0, safeLimit))
                : scored;
        return new RetrievalResult(context, limited, evaluateConsistency(limited));
    }

    public List<Candidate> findSuggestions(String ticketId, String query, int limit) {
        return retrieve(ticketId, query, limit).candidates();
    }

    private RetrievalContext buildContext(String ticketId, String query) {
        String normalizedTicketId = trim(ticketId);
        String normalizedQuery = normalize(query);
        Set<String> queryTokens = tokenize(normalizedQuery);
        AiIntentService.IntentMatch intentMatch = aiIntentService.extract(query);
        AiIntentService.IntentPolicy intentPolicy = aiIntentService.resolvePolicy(intentMatch.intentKey());
        TicketScope ticketScope = loadTicketScope(normalizedTicketId);

        String requestedChannel = normalizeScope(firstNonBlank(intentMatch.slots().get("channel"), ticketScope.channel()));
        String requestedBusiness = normalizeScope(firstNonBlank(intentMatch.slots().get("business"), ticketScope.business()));
        String requestedLocation = normalizeScope(firstNonBlank(intentMatch.slots().get("location"), ticketScope.location()));

        return new RetrievalContext(
                normalizedTicketId,
                normalizedQuery,
                queryTokens,
                intentMatch,
                intentPolicy,
                requestedChannel,
                requestedBusiness,
                requestedLocation
        );
    }

    private TicketScope loadTicketScope(String ticketId) {
        String normalizedTicketId = trim(ticketId);
        if (normalizedTicketId == null) {
            return TicketScope.empty();
        }
        try {
            return jdbcTemplate.query(
                    """
                    SELECT
                        MAX(NULLIF(m.business, '')) AS business,
                        MAX(NULLIF(m.location_name, '')) AS location,
                        CASE
                            WHEN lower(COALESCE(MAX(c.platform), '')) = 'vk' THEN 'vk'
                            WHEN lower(COALESCE(MAX(c.platform), '')) = 'max' THEN 'max'
                            WHEN lower(COALESCE(MAX(c.platform), '')) = 'web_form' THEN 'web_form'
                            WHEN trim(COALESCE(MAX(c.platform), '')) = '' THEN NULL
                            ELSE lower(MAX(c.platform))
                        END AS channel
                      FROM tickets t
                      LEFT JOIN messages m ON m.ticket_id = t.ticket_id
                      LEFT JOIN channels c ON c.id = COALESCE(m.channel_id, t.channel_id)
                     WHERE t.ticket_id = ?
                    """,
                    rs -> rs.next()
                            ? new TicketScope(
                            normalizeScope(rs.getString("channel")),
                            normalizeScope(rs.getString("business")),
                            normalizeScope(rs.getString("location")))
                            : TicketScope.empty(),
                    normalizedTicketId
            );
        } catch (Exception ex) {
            return TicketScope.empty();
        }
    }

    private List<DocumentVector> loadMemoryDocuments(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 400));
        try {
            return jdbcTemplate.query(
                    """
                    SELECT m.query_key,
                           m.query_text,
                           m.solution_text,
                           m.status,
                           m.trust_level,
                           m.source_type,
                           m.safety_level,
                           m.intent_key,
                           m.slot_signature,
                           m.scope_channel,
                           m.scope_business,
                           m.scope_location,
                           m.times_confirmed,
                           m.times_corrected,
                           m.updated_at,
                           ku.unit_key AS knowledge_unit_key,
                           ku.title AS knowledge_title,
                           ku.body_text AS knowledge_body,
                           COALESCE((
                               SELECT COUNT(*)
                                 FROM ai_agent_memory_link ml2
                                WHERE ml2.knowledge_unit_id = ku.id
                           ), 0) AS linked_evidence_count
                      FROM ai_agent_solution_memory m
                      LEFT JOIN ai_agent_memory_link ml ON ml.query_key = m.query_key
                      LEFT JOIN ai_agent_knowledge_unit ku
                             ON ku.id = ml.knowledge_unit_id
                            AND lower(COALESCE(ku.status, 'active')) = 'active'
                     WHERE COALESCE(m.review_required, 0) = 0
                       AND trim(COALESCE(m.solution_text, '')) <> ''
                       AND lower(COALESCE(m.status, 'approved')) = 'approved'
                     ORDER BY datetime(substr(COALESCE(m.updated_at, ''), 1, 19)) DESC,
                              m.query_key DESC
                     LIMIT ?
                    """,
                    (rs, rowNum) -> mapMemoryVector(rs.getString("query_key"),
                            rs.getString("query_text"),
                            rs.getString("solution_text"),
                            rs.getString("status"),
                            rs.getString("trust_level"),
                            rs.getString("source_type"),
                            rs.getString("safety_level"),
                            rs.getString("intent_key"),
                            rs.getString("slot_signature"),
                            rs.getString("scope_channel"),
                            rs.getString("scope_business"),
                            rs.getString("scope_location"),
                            rs.getString("updated_at"),
                            rs.getString("knowledge_unit_key"),
                            rs.getString("knowledge_title"),
                            rs.getString("knowledge_body"),
                            rs.getInt("linked_evidence_count"),
                            rs.getInt("times_confirmed"),
                            rs.getInt("times_corrected")),
                    safeLimit
            ).stream().filter(item -> item != null).toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private DocumentVector mapMemoryVector(String queryKey,
                                           String queryText,
                                           String solutionText,
                                           String status,
                                           String trustLevel,
                                           String sourceType,
                                           String safetyLevel,
                                           String intentKey,
                                           String slotSignature,
                                           String scopeChannel,
                                           String scopeBusiness,
                                           String scopeLocation,
                                           String updatedAt,
                                           String knowledgeUnitKey,
                                           String knowledgeTitle,
                                           String knowledgeBody,
                                           int linkedEvidenceCount,
                                           int timesConfirmed,
                                           int timesCorrected) {
        String normalizedQueryKey = trim(queryKey);
        String snippet = firstNonBlank(knowledgeBody, solutionText);
        if (!StringUtils.hasText(snippet)) {
            return null;
        }
        String canonicalKey = firstNonBlank(knowledgeUnitKey, normalizedQueryKey != null ? "memory:" + normalizedQueryKey : null);
        return buildVector(
                "memory",
                firstNonBlank(knowledgeTitle, titleForIntent(intentKey, "Memory")),
                queryText,
                snippet,
                normalizedQueryKey,
                status,
                trustLevel,
                sourceType,
                safetyLevel,
                intentKey,
                slotSignature,
                scopeChannel,
                scopeBusiness,
                scopeLocation,
                canonicalKey,
                canonicalKey,
                Math.max(1, linkedEvidenceCount > 0 ? linkedEvidenceCount : 1),
                Math.max(0, timesConfirmed),
                Math.max(0, timesCorrected),
                parseInstant(updatedAt)
        );
    }

    private List<DocumentVector> loadKnowledgeDocuments(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 400));
        try {
            return jdbcTemplate.query(
                    """
                    SELECT ku.unit_key,
                           ku.title,
                           ku.body_text,
                           ku.intent_key,
                           ku.slot_signature,
                           ku.business,
                           ku.location,
                           ku.channel,
                           ku.status,
                           ku.updated_at,
                           COALESCE((
                               SELECT COUNT(*)
                                 FROM ai_agent_memory_link ml
                                WHERE ml.knowledge_unit_id = ku.id
                           ), 0) AS linked_evidence_count
                      FROM ai_agent_knowledge_unit ku
                     WHERE lower(COALESCE(ku.status, 'active')) = 'active'
                       AND trim(COALESCE(ku.body_text, '')) <> ''
                     ORDER BY datetime(substr(COALESCE(ku.updated_at, ''), 1, 19)) DESC,
                              ku.unit_key DESC
                     LIMIT ?
                    """,
                    (rs, rowNum) -> buildVector(
                            "knowledge",
                            firstNonBlank(trim(rs.getString("title")), titleForIntent(rs.getString("intent_key"), "Knowledge")),
                            null,
                            rs.getString("body_text"),
                            null,
                            rs.getString("status"),
                            "high",
                            "knowledge",
                            "normal",
                            rs.getString("intent_key"),
                            rs.getString("slot_signature"),
                            rs.getString("channel"),
                            rs.getString("business"),
                            rs.getString("location"),
                            rs.getString("unit_key"),
                            rs.getString("unit_key"),
                            Math.max(1, rs.getInt("linked_evidence_count") + 1),
                            Math.max(0, rs.getInt("linked_evidence_count")),
                            0,
                            parseInstant(rs.getString("updated_at"))
                    ),
                    safeLimit
            ).stream().filter(item -> item != null).toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private DocumentVector buildVector(String source,
                                       String title,
                                       String queryText,
                                       String body,
                                       String memoryKey,
                                       String status,
                                       String trustLevel,
                                       String sourceType,
                                       String safetyLevel,
                                       String intentKey,
                                       String slotSignature,
                                       String scopeChannel,
                                       String scopeBusiness,
                                       String scopeLocation,
                                       String canonicalKey,
                                       String sourceRef,
                                       int evidenceCount,
                                       int timesConfirmed,
                                       int timesCorrected,
                                       Instant updatedAt) {
        String normalizedBody = trim(body);
        if (!StringUtils.hasText(normalizedBody)) {
            return null;
        }
        String documentText = firstNonBlank(queryText, "") + " " + normalizedBody;
        Set<String> tokens = tokenize(documentText);
        if (tokens.isEmpty()) {
            return null;
        }
        return new DocumentVector(
                trim(source),
                firstNonBlank(trim(title), trim(source)),
                trim(queryText),
                normalizedBody,
                trim(memoryKey),
                trimOrDefault(status, "approved"),
                trimOrDefault(trustLevel, "medium"),
                trimOrDefault(sourceType, source),
                trimOrDefault(safetyLevel, "normal"),
                trim(intentKey),
                trim(slotSignature),
                normalizeScope(scopeChannel),
                normalizeScope(scopeBusiness),
                normalizeScope(scopeLocation),
                firstNonBlank(trim(canonicalKey), trim(sourceRef)),
                trim(sourceRef),
                Math.max(1, evidenceCount),
                Math.max(0, timesConfirmed),
                Math.max(0, timesCorrected),
                updatedAt != null ? updatedAt : Instant.EPOCH,
                tokens,
                countTerms(documentText),
                stableHash(normalizedBody)
        );
    }

    private Map<String, Integer> buildDocumentFrequency(List<DocumentVector> documents, Set<String> queryTokens) {
        Map<String, Integer> frequency = new HashMap<>();
        if (documents.isEmpty() || queryTokens.isEmpty()) {
            return frequency;
        }
        for (String token : queryTokens) {
            int count = 0;
            for (DocumentVector vector : documents) {
                if (vector.tokens().contains(token)) {
                    count++;
                }
            }
            frequency.put(token, count);
        }
        return frequency;
    }

    private Candidate scoreDocument(RetrievalContext context,
                                    DocumentVector vector,
                                    Map<String, Integer> documentFrequency,
                                    double averageDocumentLength,
                                    int corpusSize) {
        if (!isScopeCompatible(context.channel(), vector.scopeChannel())
                || !isScopeCompatible(context.business(), vector.scopeBusiness())
                || !isScopeCompatible(context.location(), vector.scopeLocation())) {
            return null;
        }

        double bm25 = bm25Score(context.queryTokens(), vector.termFrequency(), Math.max(1, vector.tokens().size()), averageDocumentLength, documentFrequency, corpusSize);
        double keywordScore = clamp01(1d - Math.exp(-Math.max(0d, bm25)));
        double lexicalScore = overlapScore(context.queryTokens(), vector.tokens());
        double semanticScore = semanticScore(context, vector, lexicalScore);
        int scopeMatches = countScopeMatches(context, vector);
        double scopeBoost = Math.min(0.18d, scopeMatches * 0.06d);
        double trustBoost = switch (trimOrDefault(vector.trustLevel(), "medium")) {
            case "high" -> 0.06d;
            case "medium" -> 0.03d;
            default -> 0d;
        };
        double freshnessBoost = freshnessBoost(vector.updatedAt());
        double supportBoost = Math.min(0.12d, Math.max(0, vector.evidenceCount() - 1) * 0.05d);
        double confirmationBoost = Math.min(0.10d, vector.timesConfirmed() * 0.02d);
        double correctionPenalty = Math.min(0.10d, vector.timesCorrected() * 0.025d);
        double finalScore = clamp01(
                0.42d * keywordScore
                        + 0.18d * lexicalScore
                        + 0.26d * semanticScore
                        + scopeBoost
                        + trustBoost
                        + freshnessBoost
                        + supportBoost
                        + confirmationBoost
                        - correctionPenalty
        );
        if (finalScore <= 0.01d) {
            return null;
        }

        String trace = "intent=" + trimOrDefault(vector.intentKey(), "none")
                + ",keyword=" + formatScore(keywordScore)
                + ",lexical=" + formatScore(lexicalScore)
                + ",semantic=" + formatScore(semanticScore)
                + ",scope=" + scopeMatches
                + ",evidence=" + vector.evidenceCount()
                + ",canonical=" + trimOrDefault(vector.canonicalKey(), "none");

        return new Candidate(
                vector.source(),
                vector.title(),
                cut(vector.body(), 420),
                finalScore,
                vector.memoryKey(),
                vector.status(),
                vector.trustLevel(),
                vector.sourceType(),
                vector.safetyLevel(),
                vector.sourceRef(),
                vector.intentKey(),
                vector.slotSignature(),
                vector.canonicalKey(),
                vector.evidenceCount(),
                trace
        );
    }

    private double semanticScore(RetrievalContext context, DocumentVector vector, double lexicalScore) {
        double score = 0d;
        String requestedIntent = trim(context.intentMatch().intentKey());
        String candidateIntent = trim(vector.intentKey());
        if (requestedIntent != null && requestedIntent.equals(candidateIntent)) {
            score += "general_support".equals(requestedIntent) ? 0.12d : 0.38d;
        }
        String requestedSlotSignature = trim(context.intentMatch().slotSignature());
        String candidateSlotSignature = trim(vector.slotSignature());
        if (requestedSlotSignature != null && requestedSlotSignature.equals(candidateSlotSignature)) {
            score += 0.28d;
        }
        score += lexicalScore * 0.20d;
        score += charTrigramSimilarity(context.normalizedQuery(), normalize(firstNonBlank(vector.queryText(), vector.body()))) * 0.16d;
        if (context.intentMatch().schemaValid()) {
            score += 0.05d;
        }
        return clamp01(score);
    }

    private double bm25Score(Set<String> queryTokens,
                             Map<String, Integer> termFrequency,
                             int documentLength,
                             double averageDocumentLength,
                             Map<String, Integer> documentFrequency,
                             int corpusSize) {
        if (queryTokens.isEmpty() || termFrequency.isEmpty()) {
            return 0d;
        }
        double score = 0d;
        double k1 = 1.2d;
        double b = 0.75d;
        for (String token : queryTokens) {
            int tf = termFrequency.getOrDefault(token, 0);
            if (tf <= 0) {
                continue;
            }
            int df = Math.max(0, documentFrequency.getOrDefault(token, 0));
            double idf = Math.log1p((corpusSize - df + 0.5d) / (df + 0.5d));
            double denominator = tf + k1 * (1d - b + b * (documentLength / Math.max(1d, averageDocumentLength)));
            score += idf * (tf * (k1 + 1d)) / Math.max(1d, denominator);
        }
        return Math.max(0d, score);
    }

    private ConsistencyCheck evaluateConsistency(List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new ConsistencyCheck(false, false, 0, "no_evidence");
        }
        Candidate top = candidates.get(0);
        int supportCount = Math.max(1, top.evidenceCount());
        Set<String> supportGroups = new LinkedHashSet<>();
        supportGroups.add(firstNonBlank(top.canonicalKey(), top.sourceRef(), top.memoryKey(), stableHash(top.snippet())));

        boolean conflict = false;
        for (int i = 1; i < Math.min(candidates.size(), 5); i++) {
            Candidate candidate = candidates.get(i);
            if (candidate.score() < Math.max(0.42d, top.score() - 0.18d)) {
                continue;
            }
            if (supportsTop(top, candidate)) {
                String groupKey = firstNonBlank(candidate.canonicalKey(), candidate.sourceRef(), candidate.memoryKey(), stableHash(candidate.snippet()));
                if (supportGroups.add(groupKey)) {
                    supportCount++;
                }
                supportCount = Math.max(supportCount, candidate.evidenceCount());
            } else if (candidate.score() >= Math.max(0.58d, top.score() - 0.07d)) {
                conflict = true;
            }
        }

        if (conflict) {
            return new ConsistencyCheck(false, true, supportCount, "evidence_conflict");
        }
        if (supportCount < 2) {
            return new ConsistencyCheck(false, false, supportCount, "insufficient_confirmations");
        }
        return new ConsistencyCheck(true, false, supportCount, "confirmed");
    }

    private boolean supportsTop(Candidate top, Candidate candidate) {
        if (top == null || candidate == null) {
            return false;
        }
        if (StringUtils.hasText(top.canonicalKey()) && top.canonicalKey().equals(candidate.canonicalKey())) {
            return true;
        }
        if (StringUtils.hasText(top.slotSignature()) && top.slotSignature().equals(candidate.slotSignature())
                && StringUtils.hasText(top.intentKey()) && top.intentKey().equals(candidate.intentKey())) {
            return answerSimilarity(top.snippet(), candidate.snippet()) >= 0.45d;
        }
        return answerSimilarity(top.snippet(), candidate.snippet()) >= 0.78d;
    }

    private double answerSimilarity(String left, String right) {
        return overlapScore(tokenize(left), tokenize(right));
    }

    private boolean isScopeCompatible(String requested, String candidate) {
        String requestedScope = normalizeScope(requested);
        String candidateScope = normalizeScope(candidate);
        return requestedScope == null || candidateScope == null || requestedScope.equals(candidateScope);
    }

    private int countScopeMatches(RetrievalContext context, DocumentVector vector) {
        int matches = 0;
        if (context.channel() != null && context.channel().equals(vector.scopeChannel())) {
            matches++;
        }
        if (context.business() != null && context.business().equals(vector.scopeBusiness())) {
            matches++;
        }
        if (context.location() != null && context.location().equals(vector.scopeLocation())) {
            matches++;
        }
        return matches;
    }

    private double freshnessBoost(Instant updatedAt) {
        if (updatedAt == null || updatedAt.equals(Instant.EPOCH)) {
            return 0d;
        }
        long ageDays = Math.max(0L, Duration.between(updatedAt, Instant.now()).toDays());
        if (ageDays <= 7) {
            return 0.05d;
        }
        if (ageDays <= 30) {
            return 0.03d;
        }
        if (ageDays <= 90) {
            return 0.01d;
        }
        return 0d;
    }

    private Map<String, Integer> countTerms(String value) {
        Map<String, Integer> out = new HashMap<>();
        for (String token : tokenize(value)) {
            out.put(token, out.getOrDefault(token, 0) + 1);
        }
        return out;
    }

    private double overlapScore(Set<String> first, Set<String> second) {
        if (first.isEmpty() || second.isEmpty()) {
            return 0d;
        }
        int overlap = 0;
        for (String token : first) {
            if (second.contains(token)) {
                overlap++;
            }
        }
        return clamp01(overlap / (double) Math.max(1, first.size()));
    }

    private double charTrigramSimilarity(String left, String right) {
        Set<String> first = trigrams(left);
        Set<String> second = trigrams(right);
        if (first.isEmpty() || second.isEmpty()) {
            return 0d;
        }
        int overlap = 0;
        for (String token : first) {
            if (second.contains(token)) {
                overlap++;
            }
        }
        int union = first.size() + second.size() - overlap;
        return union <= 0 ? 0d : overlap / (double) union;
    }

    private Set<String> trigrams(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized) || normalized.length() < 3) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i <= normalized.length() - 3; i++) {
            out.add(normalized.substring(i, i + 3));
        }
        return out;
    }

    private Set<String> tokenize(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String token : TOKEN_SPLIT.split(normalized)) {
            String item = trim(token);
            if (item == null || item.length() < 2 || STOP.contains(item)) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    private String titleForIntent(String intentKey, String fallbackPrefix) {
        String normalized = trim(intentKey);
        if (!StringUtils.hasText(normalized)) {
            return fallbackPrefix;
        }
        return fallbackPrefix + ": " + normalized.replace('_', ' ');
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
    }

    private String normalizeScope(String value) {
        String normalized = trim(value);
        return normalized != null ? normalize(normalized) : null;
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String trimOrDefault(String value, String fallback) {
        String normalized = trim(value);
        return normalized != null ? normalized : fallback;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trim(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String cut(String value, int max) {
        String normalized = trim(value);
        if (normalized == null) {
            return "";
        }
        return normalized.length() <= max ? normalized : normalized.substring(0, Math.max(0, max - 3)) + "...";
    }

    private String formatScore(double value) {
        return String.format(Locale.ROOT, "%.2f", clamp01(value));
    }

    private double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private String stableHash(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(normalized.hashCode());
        }
    }

    private Instant parseInstant(String value) {
        String normalized = trim(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            String compact = normalized.replace(' ', 'T');
            if (compact.length() == 19) {
                return LocalDateTime.parse(compact).toInstant(ZoneOffset.UTC);
            }
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    public record RetrievalResult(RetrievalContext context,
                                  List<Candidate> candidates,
                                  ConsistencyCheck consistency) {
    }

    public record RetrievalContext(String ticketId,
                                   String normalizedQuery,
                                   Set<String> queryTokens,
                                   AiIntentService.IntentMatch intentMatch,
                                   AiIntentService.IntentPolicy intentPolicy,
                                   String channel,
                                   String business,
                                   String location) {
    }

    public record ConsistencyCheck(boolean autoReplyAllowed,
                                   boolean hasConflict,
                                   int supportCount,
                                   String reason) {
    }

    public record Candidate(String source,
                            String title,
                            String snippet,
                            double score,
                            String memoryKey,
                            String status,
                            String trustLevel,
                            String sourceType,
                            String safetyLevel,
                            String sourceRef,
                            String intentKey,
                            String slotSignature,
                            String canonicalKey,
                            int evidenceCount,
                            String trace) {
    }

    private record TicketScope(String channel, String business, String location) {
        static TicketScope empty() {
            return new TicketScope(null, null, null);
        }
    }

    private record DocumentVector(String source,
                                  String title,
                                  String queryText,
                                  String body,
                                  String memoryKey,
                                  String status,
                                  String trustLevel,
                                  String sourceType,
                                  String safetyLevel,
                                  String intentKey,
                                  String slotSignature,
                                  String scopeChannel,
                                  String scopeBusiness,
                                  String scopeLocation,
                                  String canonicalKey,
                                  String sourceRef,
                                  int evidenceCount,
                                  int timesConfirmed,
                                  int timesCorrected,
                                  Instant updatedAt,
                                  Set<String> tokens,
                                  Map<String, Integer> termFrequency,
                                  String answerKey) {
    }
}
