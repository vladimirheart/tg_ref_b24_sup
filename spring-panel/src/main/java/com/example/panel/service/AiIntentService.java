package com.example.panel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiIntentService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(2);
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("(?iu)(?:заказ|order|№|#)\\s*[:#-]?\\s*([\\p{L}0-9\\-]{3,24})");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?iu)(\\d{2,6})\\s*(?:₽|р|руб)");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?iu)(\\+?[0-9][0-9\\-()\\s]{8,})");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("(?iu)(?:локац(?:ия|ии)|ресторан(?:е)?|точк(?:а|е)|адрес)\\s*[:\\-]?\\s*([\\p{L}0-9\\-\\s]{2,48})");
    private static final Set<String> STOP = Set.of("и", "в", "на", "не", "что", "как", "для", "или", "по", "из", "к", "у", "о", "об", "the", "a", "an", "to", "of", "in", "on", "for", "and", "or", "is", "are", "be");

    private static final Map<String, String> BUSINESS_ALIASES = Map.ofEntries(
            Map.entry("бб", "блинбери"),
            Map.entry("блинбери", "блинбери"),
            Map.entry("сушивесла", "сушивесла"),
            Map.entry("суши весла", "сушивесла"),
            Map.entry("пиццафабрика", "пиццафабрика"),
            Map.entry("pizzafabrika", "пиццафабрика")
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private volatile List<IntentCatalogEntry> cachedCatalog = List.of();
    private volatile Instant cachedCatalogAt = Instant.EPOCH;
    private volatile Map<String, IntentPolicy> cachedPolicies = Map.of();
    private volatile Instant cachedPolicyAt = Instant.EPOCH;

    public AiIntentService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public IntentMatch extract(String message) {
        String normalized = normalize(message);
        if (!StringUtils.hasText(normalized)) {
            return IntentMatch.empty("general_support");
        }
        Map<String, String> rawSlots = extractSlots(normalized);
        List<IntentCatalogEntry> catalog = loadCatalog();
        IntentCatalogEntry selected = selectIntent(catalog, normalized, rawSlots);
        SlotSchema schema = selected.slotSchema();
        Map<String, String> validatedSlots = validateSlots(rawSlots, schema);
        List<String> requiredMissing = resolveMissingRequiredSlots(validatedSlots, schema);
        boolean schemaValid = requiredMissing.isEmpty();
        double confidence = estimateConfidence(selected, normalized, validatedSlots, requiredMissing);
        String slotsJson = toJson(validatedSlots);
        String slotSignature = buildSlotSignature(validatedSlots);
        return new IntentMatch(
                selected.intentKey(),
                validatedSlots,
                slotsJson,
                slotSignature,
                schemaValid,
                requiredMissing,
                confidence
        );
    }

    public IntentPolicy resolvePolicy(String intentKey) {
        String normalizedKey = normalizeKey(intentKey);
        IntentPolicy policy = loadPolicies().get(normalizedKey);
        if (policy != null) {
            return policy;
        }
        if ("operator_request".equals(normalizedKey)) {
            return new IntentPolicy(normalizedKey, false, false, true, "normal", "fallback_operator_request");
        }
        return new IntentPolicy(normalizedKey, false, true, false, "normal", "fallback_assist_only");
    }

    public boolean isAutoReplyAllowed(String intentKey) {
        return resolvePolicy(intentKey).autoReplyAllowed();
    }

    public boolean requiresOperator(String intentKey) {
        return resolvePolicy(intentKey).requiresOperator();
    }

    public boolean isAssistOnly(String intentKey) {
        return resolvePolicy(intentKey).assistOnly();
    }

    private IntentCatalogEntry selectIntent(List<IntentCatalogEntry> catalog,
                                            String normalizedMessage,
                                            Map<String, String> slots) {
        if (containsHumanRequest(normalizedMessage)) {
            IntentCatalogEntry operator = catalog.stream()
                    .filter(entry -> "operator_request".equals(entry.intentKey()))
                    .findFirst()
                    .orElse(null);
            if (operator != null) {
                return operator;
            }
        }
        IntentCatalogEntry best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (IntentCatalogEntry entry : catalog) {
            if (!entry.enabled()) {
                continue;
            }
            double score = scoreIntent(entry, normalizedMessage, slots);
            if (score > bestScore) {
                best = entry;
                bestScore = score;
            } else if (best != null && score == bestScore && entry.priority() < best.priority()) {
                best = entry;
            }
        }
        IntentCatalogEntry general = catalog.stream()
                .filter(entry -> "general_support".equals(entry.intentKey()))
                .findFirst()
                .orElse(defaultCatalog().stream().filter(entry -> "general_support".equals(entry.intentKey())).findFirst().orElse(null));
        if (best == null) {
            return general != null ? general : new IntentCatalogEntry("general_support", "Общий вопрос", List.of(), SlotSchema.EMPTY, true, 1000);
        }
        if (bestScore < 0.65d && general != null) {
            return general;
        }
        return best;
    }

    private double scoreIntent(IntentCatalogEntry entry,
                               String normalizedMessage,
                               Map<String, String> slots) {
        Set<String> tokens = tokenize(normalizedMessage);
        double score = 0d;
        for (String hint : entry.patternHints()) {
            if (!StringUtils.hasText(hint)) {
                continue;
            }
            String normalizedHint = normalize(hint);
            if (normalizedMessage.contains(normalizedHint)) {
                score += 1.1d + Math.min(0.45d, normalizedHint.length() * 0.01d);
            } else {
                Set<String> hintTokens = tokenize(normalizedHint);
                int overlap = 0;
                for (String token : hintTokens) {
                    if (tokens.contains(token)) {
                        overlap++;
                    }
                }
                if (!hintTokens.isEmpty() && overlap > 0) {
                    score += 0.35d + 0.4d * (overlap / (double) hintTokens.size());
                }
            }
        }
        SlotSchema schema = entry.slotSchema();
        for (String required : schema.required()) {
            if (slots.containsKey(required)) {
                score += 0.35d;
            } else {
                score -= 0.18d;
            }
        }
        if (!slots.isEmpty()) {
            score += Math.min(0.4d, slots.size() * 0.06d);
        }
        score += Math.max(0d, (1000 - Math.min(1000, entry.priority())) / 5000d);
        return score;
    }

    private double estimateConfidence(IntentCatalogEntry selected,
                                      String normalizedMessage,
                                      Map<String, String> slots,
                                      List<String> requiredMissing) {
        double base = Math.min(1d, scoreIntent(selected, normalizedMessage, slots) / 3.5d);
        if (!requiredMissing.isEmpty()) {
            base = Math.max(0d, base - Math.min(0.35d, requiredMissing.size() * 0.15d));
        }
        return Math.max(0d, Math.min(1d, base));
    }

    private Map<String, String> extractSlots(String normalizedMessage) {
        Map<String, String> slots = new LinkedHashMap<>();
        String business = detectBusiness(normalizedMessage);
        if (business != null) {
            slots.put("business", business);
        }
        String orderId = firstGroup(ORDER_ID_PATTERN, normalizedMessage);
        if (orderId != null) {
            slots.put("order_id", normalizeSlotValue(orderId));
        }
        String amount = firstGroup(AMOUNT_PATTERN, normalizedMessage);
        if (amount != null) {
            slots.put("amount", normalizeSlotValue(amount));
        }
        String phone = firstGroup(PHONE_PATTERN, normalizedMessage);
        if (phone != null) {
            slots.put("contact_phone", normalizePhone(phone));
        }
        String location = firstGroup(LOCATION_PATTERN, normalizedMessage);
        if (location != null) {
            slots.put("location", normalizeSlotValue(location));
        }
        String deliveryType = detectDeliveryType(normalizedMessage);
        if (deliveryType != null) {
            slots.put("delivery_type", deliveryType);
        }
        String allergen = detectAllergen(normalizedMessage);
        if (allergen != null) {
            slots.put("allergen", allergen);
        }
        String channel = detectChannel(normalizedMessage);
        if (channel != null) {
            slots.put("channel", channel);
        }
        return slots;
    }

    private String detectBusiness(String normalizedMessage) {
        for (Map.Entry<String, String> entry : BUSINESS_ALIASES.entrySet()) {
            if (containsNormalizedToken(normalizedMessage, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String detectDeliveryType(String normalizedMessage) {
        if (normalizedMessage.contains("самовывоз")) {
            return "pickup";
        }
        if (normalizedMessage.contains("достав")) {
            return "delivery";
        }
        return null;
    }

    private String detectAllergen(String normalizedMessage) {
        if (normalizedMessage.contains("глютен")) {
            return "глютен";
        }
        if (normalizedMessage.contains("лактоз")) {
            return "лактоза";
        }
        if (normalizedMessage.contains("орех")) {
            return "орехи";
        }
        if (normalizedMessage.contains("аллерг")) {
            return "аллерген";
        }
        return null;
    }

    private String detectChannel(String normalizedMessage) {
        if (normalizedMessage.contains("telegram") || normalizedMessage.contains("телеграм")) {
            return "telegram";
        }
        if (normalizedMessage.contains("vk") || normalizedMessage.contains("вк")) {
            return "vk";
        }
        if (normalizedMessage.contains("max") || normalizedMessage.contains("мах")) {
            return "max";
        }
        return null;
    }

    private Map<String, String> validateSlots(Map<String, String> extracted, SlotSchema schema) {
        if (extracted.isEmpty()) {
            return Map.of();
        }
        Set<String> allowed = schema.allowed();
        if (allowed.isEmpty()) {
            return new LinkedHashMap<>(extracted);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : extracted.entrySet()) {
            if (allowed.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private List<String> resolveMissingRequiredSlots(Map<String, String> slots, SlotSchema schema) {
        if (schema.required().isEmpty()) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (String required : schema.required()) {
            if (!slots.containsKey(required)) {
                missing.add(required);
            }
        }
        return missing;
    }

    private String buildSlotSignature(Map<String, String> slots) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>(slots.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        StringBuilder base = new StringBuilder();
        for (Map.Entry<String, String> entry : entries) {
            if (!base.isEmpty()) {
                base.append('|');
            }
            base.append(entry.getKey()).append('=').append(normalizeSlotValue(entry.getValue()));
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(base.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(base.toString().hashCode());
        }
    }

    private String toJson(Map<String, String> slots) {
        try {
            return objectMapper.writeValueAsString(slots == null ? Map.of() : slots);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private List<IntentCatalogEntry> loadCatalog() {
        Instant now = Instant.now();
        if (!cachedCatalog.isEmpty() && Duration.between(cachedCatalogAt, now).compareTo(CACHE_TTL) < 0) {
            return cachedCatalog;
        }
        synchronized (this) {
            now = Instant.now();
            if (!cachedCatalog.isEmpty() && Duration.between(cachedCatalogAt, now).compareTo(CACHE_TTL) < 0) {
                return cachedCatalog;
            }
            List<IntentCatalogEntry> loaded = queryCatalog();
            if (loaded.isEmpty()) {
                loaded = defaultCatalog();
            }
            cachedCatalog = loaded;
            cachedCatalogAt = now;
            return loaded;
        }
    }

    private Map<String, IntentPolicy> loadPolicies() {
        Instant now = Instant.now();
        if (!cachedPolicies.isEmpty() && Duration.between(cachedPolicyAt, now).compareTo(CACHE_TTL) < 0) {
            return cachedPolicies;
        }
        synchronized (this) {
            now = Instant.now();
            if (!cachedPolicies.isEmpty() && Duration.between(cachedPolicyAt, now).compareTo(CACHE_TTL) < 0) {
                return cachedPolicies;
            }
            Map<String, IntentPolicy> loaded = queryPolicies();
            if (loaded.isEmpty()) {
                loaded = defaultPolicies();
            }
            cachedPolicies = loaded;
            cachedPolicyAt = now;
            return loaded;
        }
    }

    private List<IntentCatalogEntry> queryCatalog() {
        try {
            List<IntentCatalogEntry> rows = jdbcTemplate.query(
                    """
                    SELECT intent_key, title, pattern_hints, slot_schema_json, enabled, priority
                      FROM ai_agent_intent_catalog
                     WHERE enabled = 1
                     ORDER BY priority ASC, intent_key ASC
                    """,
                    (rs, rowNum) -> new IntentCatalogEntry(
                            normalizeKey(rs.getString("intent_key")),
                            trim(rs.getString("title")),
                            splitHints(rs.getString("pattern_hints")),
                            parseSchema(rs.getString("slot_schema_json")),
                            rs.getInt("enabled") > 0,
                            rs.getInt("priority")
                    )
            );
            rows.sort(Comparator.comparingInt(IntentCatalogEntry::priority));
            return rows;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, IntentPolicy> queryPolicies() {
        try {
            List<IntentPolicy> rows = jdbcTemplate.query(
                    """
                    SELECT intent_key, auto_reply_allowed, assist_only, requires_operator, safety_level, notes
                      FROM ai_agent_intent_policy
                    """,
                    (rs, rowNum) -> new IntentPolicy(
                            normalizeKey(rs.getString("intent_key")),
                            rs.getInt("auto_reply_allowed") > 0,
                            rs.getInt("assist_only") > 0,
                            rs.getInt("requires_operator") > 0,
                            trim(rs.getString("safety_level")),
                            trim(rs.getString("notes"))
                    )
            );
            Map<String, IntentPolicy> out = new HashMap<>();
            for (IntentPolicy row : rows) {
                out.put(row.intentKey(), row);
            }
            return out;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<IntentCatalogEntry> defaultCatalog() {
        return List.of(
                new IntentCatalogEntry("operator_request", "Запрос оператора", List.of("оператор", "человек", "менеджер", "живой"), SlotSchema.EMPTY, true, 50),
                new IntentCatalogEntry("refund_request", "Возврат", List.of("возврат", "верните деньги", "компенсац", "refund"), schema("business", "location", "order_id", "amount"), true, 60),
                new IntentCatalogEntry("payment_issue", "Проблема оплаты", List.of("списали", "оплата", "карта", "платеж"), schema("business", "location", "order_id", "amount"), true, 80),
                new IntentCatalogEntry("order_status", "Статус заказа", List.of("статус заказа", "где заказ", "номер заказа"), requiredSchema(List.of("order_id"), List.of("business", "location", "order_id", "delivery_type")), true, 120),
                new IntentCatalogEntry("delivery_delay", "Задержка доставки", List.of("доставка опоздала", "задержка доставки", "долго везут"), schema("business", "location", "order_id", "delivery_type"), true, 140),
                new IntentCatalogEntry("food_quality", "Качество блюда", List.of("невкусно", "холодное", "сырое", "испорчено"), schema("business", "location", "order_id"), true, 150),
                new IntentCatalogEntry("cancel_order", "Отмена заказа", List.of("отменить заказ", "отмена заказа", "cancel"), schema("business", "location", "order_id"), true, 160),
                new IntentCatalogEntry("technical_issue", "Техническая проблема", List.of("не работает", "ошибка", "приложение", "сайт"), SlotSchema.EMPTY, true, 200),
                new IntentCatalogEntry("loyalty_program", "Лояльность", List.of("бонус", "промокод", "скидка", "баллы"), SlotSchema.EMPTY, true, 220),
                new IntentCatalogEntry("general_support", "Общий вопрос", List.of("подскажите", "помогите", "вопрос"), SlotSchema.EMPTY, true, 900)
        );
    }

    private Map<String, IntentPolicy> defaultPolicies() {
        Map<String, IntentPolicy> policies = new HashMap<>();
        policies.put("general_support", new IntentPolicy("general_support", false, true, false, "normal", "fallback"));
        policies.put("order_status", new IntentPolicy("order_status", true, false, false, "normal", "fallback"));
        policies.put("delivery_delay", new IntentPolicy("delivery_delay", false, true, false, "normal", "fallback"));
        policies.put("payment_issue", new IntentPolicy("payment_issue", false, false, true, "high_risk", "fallback"));
        policies.put("refund_request", new IntentPolicy("refund_request", false, false, true, "high_risk", "fallback"));
        policies.put("allergy_question", new IntentPolicy("allergy_question", false, false, true, "high_risk", "fallback"));
        policies.put("operator_request", new IntentPolicy("operator_request", false, false, true, "normal", "fallback"));
        return policies;
    }

    private SlotSchema parseSchema(String rawJson) {
        String normalized = trim(rawJson);
        if (!StringUtils.hasText(normalized)) {
            return SlotSchema.EMPTY;
        }
        try {
            JsonNode root = objectMapper.readTree(normalized);
            Set<String> required = readStringSet(root.get("required"));
            Set<String> allowed = readStringSet(root.get("allowed"));
            return new SlotSchema(allowed, required);
        } catch (Exception ex) {
            return SlotSchema.EMPTY;
        }
    }

    private Set<String> readStringSet(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value = normalizeKey(item.asText(null));
            if (StringUtils.hasText(value)) {
                out.add(value);
            }
        }
        return out;
    }

    private List<String> splitHints(String value) {
        String normalized = trim(value);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : normalized.split(",")) {
            String hint = trim(part);
            if (StringUtils.hasText(hint)) {
                out.add(hint);
            }
        }
        return out;
    }

    private static SlotSchema schema(String... allowed) {
        Set<String> allowedSet = new LinkedHashSet<>();
        if (allowed != null) {
            for (String item : allowed) {
                if (StringUtils.hasText(item)) {
                    allowedSet.add(item);
                }
            }
        }
        return new SlotSchema(allowedSet, Set.of());
    }

    private static SlotSchema requiredSchema(List<String> required, List<String> allowed) {
        Set<String> requiredSet = new LinkedHashSet<>();
        Set<String> allowedSet = new LinkedHashSet<>();
        if (required != null) {
            requiredSet.addAll(required);
        }
        if (allowed != null) {
            allowedSet.addAll(allowed);
        }
        return new SlotSchema(allowedSet, requiredSet);
    }

    private boolean containsHumanRequest(String normalizedMessage) {
        return normalizedMessage.contains("оператор")
                || normalizedMessage.contains("человек")
                || normalizedMessage.contains("менеджер")
                || normalizedMessage.contains("живой");
    }

    private boolean containsNormalizedToken(String normalizedMessage, String alias) {
        String normalizedAlias = normalize(alias);
        if (!StringUtils.hasText(normalizedAlias)) {
            return false;
        }
        if (normalizedMessage.equals(normalizedAlias)) {
            return true;
        }
        if (normalizedMessage.contains(" " + normalizedAlias + " ")) {
            return true;
        }
        if (normalizedMessage.startsWith(normalizedAlias + " ") || normalizedMessage.endsWith(" " + normalizedAlias)) {
            return true;
        }
        return normalizedMessage.contains(normalizedAlias);
    }

    private String firstGroup(Pattern pattern, String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            return null;
        }
        return trim(matcher.group(1));
    }

    private String normalizePhone(String rawPhone) {
        String value = trim(rawPhone);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.replaceAll("[^0-9+]", "");
        if (digits.length() < 8) {
            return null;
        }
        return digits;
    }

    private String normalizeSlotValue(String value) {
        String normalized = trim(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435').replaceAll("\\s+", " ").trim();
    }

    private Set<String> tokenize(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String token : TOKEN_SPLIT.split(normalized)) {
            String item = trim(token);
            if (!StringUtils.hasText(item) || item.length() < 2 || STOP.contains(item)) {
                continue;
            }
            out.add(item);
        }
        return out;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435').replaceAll("\\s+", " ").trim();
    }

    private String normalizeKey(String value) {
        return normalize(value);
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record IntentMatch(String intentKey,
                              Map<String, String> slots,
                              String slotsJson,
                              String slotSignature,
                              boolean schemaValid,
                              List<String> requiredMissing,
                              double confidence) {
        public static IntentMatch empty(String intentKey) {
            return new IntentMatch(intentKey, Map.of(), "{}", null, true, List.of(), 0d);
        }
    }

    public record IntentPolicy(String intentKey,
                               boolean autoReplyAllowed,
                               boolean assistOnly,
                               boolean requiresOperator,
                               String safetyLevel,
                               String notes) {
    }

    private record IntentCatalogEntry(String intentKey,
                                      String title,
                                      List<String> patternHints,
                                      SlotSchema slotSchema,
                                      boolean enabled,
                                      int priority) {
    }

    private record SlotSchema(Set<String> allowed, Set<String> required) {
        static final SlotSchema EMPTY = new SlotSchema(Set.of(), Set.of());
    }
}

