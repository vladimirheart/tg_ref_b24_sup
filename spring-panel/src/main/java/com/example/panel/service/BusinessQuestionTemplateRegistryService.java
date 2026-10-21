package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Links one business to one distinct, unchanged template in canonical bot_settings. */
@Service
public class BusinessQuestionTemplateRegistryService {
    public static final int EXPANDED_CATALOG_LIMIT = 1000;

    private final JdbcTemplate jdbc;
    private final SharedConfigService sharedConfigService;
    private final BotSettingsPayloadNormalizer normalizer;
    private final ObjectMapper objectMapper;
    private final BusinessQuestionnaireResourcesService resources;

    public BusinessQuestionTemplateRegistryService(JdbcTemplate jdbc, SharedConfigService sharedConfigService,
            BotSettingsPayloadNormalizer normalizer, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.sharedConfigService = sharedConfigService;
        this.normalizer = normalizer;
        this.objectMapper = objectMapper;
        this.resources = new BusinessQuestionnaireResourcesService(jdbc,objectMapper);
    }

    public List<TemplateSummary> availableTemplates() {
        return loadTemplates(true).stream()
                .filter(template -> !isLegacyAssigned(template.id()))
                .map(template -> new TemplateSummary(template.id(), template.name()))
                .toList();
    }

    public Optional<Binding> findBinding(long businessId) {
        requirePositive(businessId);
        return jdbc.query("""
                SELECT business_id, template_id, template_sha256, is_active
                  FROM business_question_template_bindings WHERE business_id = ?
                """, (rs, row) -> new Binding(rs.getLong("business_id"), rs.getString("template_id"),
                rs.getString("template_sha256"), rs.getBoolean("is_active")), businessId).stream().findFirst();
    }

    /** A configured business/shared GENERAL channel must not enter the unscoped legacy runtime. */
    public boolean hasActiveChannelBindings(long channelId) {
        requirePositive(channelId);
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM business_channel_bindings WHERE channel_id=? AND is_active=TRUE)
                OR EXISTS(SELECT 1 FROM shared_channel_general_question_template_bindings WHERE channel_id=? AND is_active=TRUE)
            """,Boolean.class,channelId,channelId));
    }

    public Optional<ChoiceCatalog> choices(long channelId) {
        return choiceSnapshot(channelId).map(ChoiceSnapshot::catalog);
    }

    /** A choice from an older menu cannot silently select a rebound/edited template. */
    public Optional<SelectedRuntime> select(long channelId,long businessId,String expectedCatalogSha256) {
        requirePositive(channelId);requirePositive(businessId);
        if (expectedCatalogSha256==null || !expectedCatalogSha256.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid catalog fingerprint");
        }
        return choiceSnapshot(channelId).filter(snapshot->snapshot.catalog().catalogSha256().equals(expectedCatalogSha256))
            .flatMap(snapshot->Optional.ofNullable(snapshot.runtimes().get(businessId)));
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Optional<ChoiceCatalog> choicesWithResources(long channelId) { return choiceSnapshot(channelId,true).map(ChoiceSnapshot::catalog); }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Optional<SelectedRuntime> selectWithResources(long channelId,long businessId,String expected) {
        requirePositive(channelId); requirePositive(businessId);
        if(expected==null || !expected.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid catalog fingerprint");
        return choiceSnapshot(channelId,true).filter(s->s.catalog().catalogSha256().equals(expected)).map(s->s.runtimes().get(businessId));
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Optional<ExpandedSnapshot> expandedSnapshot(long channelId,Long selectedBusinessId) {
        if(selectedBusinessId!=null) requirePositive(selectedBusinessId);
        return choiceSnapshot(channelId,4,selectedBusinessId).map(s->new ExpandedSnapshot(s.catalog(),s.runtimes().get(selectedBusinessId)));
    }
    private Optional<ChoiceSnapshot> choiceSnapshot(long channelId) { return choiceSnapshot(channelId,false); }
    private Optional<ChoiceSnapshot> choiceSnapshot(long channelId,boolean withResources) {
        return choiceSnapshot(channelId,withResources?3:1,null);
    }
    private Optional<ChoiceSnapshot> choiceSnapshot(long channelId,int version,Long selectedBusinessId) {
        requirePositive(channelId);
        boolean withResources=version>=3;
        int limit=version==4?EXPANDED_CATALOG_LIMIT:100;
        var rows=jdbc.query("""
            SELECT binding.business_id,business.code,business.display_name,business.status,
                binding.resolution_mode,template.template_id,template.template_sha256,template.is_active AS template_active
            FROM channels channel JOIN business_channel_bindings binding ON binding.channel_id=channel.id
            JOIN businesses business ON business.id=binding.business_id
            LEFT JOIN business_question_template_bindings template ON template.business_id=business.id
            WHERE channel.id=? AND channel.is_active=TRUE AND binding.is_active=TRUE
              AND NOT EXISTS(SELECT 1 FROM general_channel_question_template_bindings general_binding
                  WHERE general_binding.channel_id=channel.id AND general_binding.is_active=TRUE)
            ORDER BY binding.business_id LIMIT ?
            """,(rs,row)->new ChoiceBinding(rs.getLong("business_id"),rs.getString("code"),rs.getString("display_name"),
                rs.getString("status"),rs.getString("resolution_mode"),rs.getString("template_id"),
                rs.getString("template_sha256"),rs.getBoolean("template_active")),channelId,limit+1);
        if (rows.isEmpty() || rows.size()>limit) return Optional.empty();
        Map<String,Template> templates=new LinkedHashMap<>();
        for (Template template:loadTemplates(withResources)) templates.put(template.id(),template);
        List<Choice> choices=new ArrayList<>();
        Map<Long,BusinessQuestionnaireResourcesService.Resources> snapshots=new LinkedHashMap<>();
        Map<Long,String> resourceHashes=new LinkedHashMap<>();
        Map<Long,Map<String,Object>> payloads=new LinkedHashMap<>();
        Set<String> selectedTemplateIds=new HashSet<>();
        Set<String> reserved=version==4?reservedTemplates():Set.of();
        for (ChoiceBinding binding:rows) {
            Template template=templates.get(binding.templateId());
            if (!"active".equals(binding.status()) || !"EXPLICIT_SELECTION".equals(binding.mode()) || !binding.active()
                    || template==null || (version==4?reserved.contains(template.id()):isLegacyAssigned(template.id())) || !selectedTemplateIds.add(template.id())
                    || !fingerprint(template.payload()).equals(binding.sha256()) || !uniqueQuestionIds(template.payload())) {
                return Optional.empty();
            }
            if(withResources) {
                if(!QuestionnaireFlowContract.supports(template.payload())) return Optional.empty();
                var resource=resources.snapshot(binding.businessId());
                if(resource.isEmpty() || !resources.supports(template.payload(),resource.get())) return Optional.empty();
                resourceHashes.put(binding.businessId(),resource.get().sha256());
                if(version!=4 || java.util.Objects.equals(binding.businessId(),selectedBusinessId)) snapshots.put(binding.businessId(),resource.get());
            }
            choices.add(new Choice("BUSINESS",binding.businessId(),binding.code(),binding.name(),template.id(),binding.sha256(),withResources?resourceHashes.get(binding.businessId()):null));
            // Round-trip creates an independent nested snapshot, not a view into mutable canonical settings.
            if(version!=4 || java.util.Objects.equals(binding.businessId(),selectedBusinessId))
                payloads.put(binding.businessId(),objectMapper.convertValue(objectMapper.valueToTree(template.payload()),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {}));
        }
        var identity=new LinkedHashMap<String,Object>();
        identity.put("version",version);identity.put("channelId",channelId);identity.put("choices",choices);
        if(withResources) identity.put("resources",resourceHashes.entrySet().stream().map(e->List.of(e.getKey(),e.getValue())).toList());
        String sha256=fingerprint(identity);
        Map<Long,SelectedRuntime> runtimes=new LinkedHashMap<>();
        for (Choice choice:choices) {
            if(version==4 && !java.util.Objects.equals(choice.businessId(),selectedBusinessId)) continue;
            runtimes.put(choice.businessId(),new SelectedRuntime(version,channelId,"BUSINESS","USER_SELECTION",choice.businessId(),
                choice.questionTemplateId(),choice.templateSha256(),sha256,
                Map.of("active_template_id",choice.questionTemplateId(),"question_templates",List.of(payloads.get(choice.businessId()))),
                withResources?snapshots.get(choice.businessId()).locationTree():Map.of(),
                withResources?snapshots.get(choice.businessId()).presetDefinitions():Map.of()));
        }
        return Optional.of(new ChoiceSnapshot(new ChoiceCatalog(version,channelId,sha256,List.copyOf(choices)),runtimes));
    }

    /** One reservation lookup for v4, independent of the number of business choices. */
    private Set<String> reservedTemplates() {
        Set<String> reserved=new HashSet<>(jdbc.queryForList("""
            SELECT template_id FROM shared_channel_general_question_template_bindings
            UNION SELECT template_id FROM general_channel_question_template_bindings WHERE is_active=TRUE
            UNION SELECT question_template_id FROM channels WHERE question_template_id IS NOT NULL
            """,String.class));
        reserved.add(text(normalizer.normalize(sharedConfigService.loadSettings().get("bot_settings")).get("active_template_id")));
        return reserved;
    }

    private boolean uniqueQuestionIds(Map<String,Object> template) {
        Set<String> ids=new HashSet<>();
        if (!(template.get("question_flow") instanceof List<?> questions)) return false;
        for (Object item:questions) {
            if (!(item instanceof Map<?,?> question) || !ids.add(text(question.get("id")))) return false;
        }
        return !ids.isEmpty();
    }

    /** Bind a distinct existing ID; cloning in the settings editor precedes this call. */
    @Transactional
    public Optional<Binding> bind(long businessId, String templateId) throws DuplicateKeyException {
        requirePositive(businessId);
        if (templateId == null || templateId.isBlank() || !templateId.equals(templateId.strip())) {
            throw new IllegalArgumentException("Question template ID is required");
        }
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext('question-template-scope'),hashtext(?))", templateId);
        Template template = loadTemplates(true).stream()
                .filter(item -> templateId.equals(item.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question template not found or unsuitable"));
        if (isLegacyAssigned(templateId)) {
            throw new IllegalArgumentException("Clone the legacy question template before binding it to a business");
        }
        if (jdbc.queryForList("SELECT id FROM businesses WHERE id = ? AND status = 'active'", Long.class,
                businessId).isEmpty()) {
            return Optional.empty();
        }
        var resource=resources.snapshot(businessId).orElseThrow();
        if(!resources.supports(template.payload(),resource)) throw new IllegalArgumentException("Question template references unavailable business presets");
        String fingerprint = fingerprint(template.payload());
        jdbc.update("""
                INSERT INTO business_question_template_bindings(business_id, template_id, template_sha256, is_active)
                VALUES (?, ?, ?, TRUE)
                ON CONFLICT (business_id) DO UPDATE SET
                    template_id = EXCLUDED.template_id,
                    template_sha256 = EXCLUDED.template_sha256,
                    is_active = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                """, businessId, templateId, fingerprint);
        return Optional.of(new Binding(businessId, templateId, fingerprint, true));
    }

    public boolean deactivate(long businessId) {
        requirePositive(businessId);
        return jdbc.update("""
                UPDATE business_question_template_bindings
                   SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
                 WHERE business_id = ? AND is_active = TRUE
                """, businessId) == 1;
    }

    /** Mixed/archived/unbound channels fail closed; a stale B template cannot change A's flow. */
    public Optional<ScopedRuntimeConfig> resolveExplicitChannel(long channelId, long businessId) {
        requirePositive(channelId);
        requirePositive(businessId);
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM general_channel_question_template_bindings
                                WHERE channel_id = ? AND is_active = TRUE)
                """, Boolean.class, channelId))) {
            return Optional.empty();
        }
        List<ChannelBinding> bindings = jdbc.query("""
                SELECT binding.business_id, binding.resolution_mode, business.status,
                       template.template_id, template.template_sha256, template.is_active AS template_active
                  FROM channels channel
                  JOIN business_channel_bindings binding ON binding.channel_id = channel.id
                  JOIN businesses business ON business.id = binding.business_id
                  LEFT JOIN business_question_template_bindings template
                    ON template.business_id = binding.business_id
                 WHERE channel.id = ? AND channel.is_active = TRUE AND binding.is_active = TRUE
                """, (rs, row) -> new ChannelBinding(rs.getLong("business_id"),
                rs.getString("resolution_mode"), rs.getString("status"),
                rs.getString("template_id"), rs.getString("template_sha256"),
                rs.getBoolean("template_active")), channelId);
        if (bindings.isEmpty() || bindings.stream().anyMatch(binding ->
                !"EXPLICIT_SELECTION".equals(binding.resolutionMode())
                    || !"active".equals(binding.businessStatus())
                    || binding.templateId() == null || binding.fingerprint() == null
                    || !binding.templateActive())) {
            return Optional.empty();
        }
        Map<String, Template> templates = new LinkedHashMap<>();
        for (Template template : loadTemplates()) {
            templates.putIfAbsent(template.id(), template);
        }
        Optional<ChannelBinding> selected = bindings.stream()
                .filter(binding -> binding.businessId() == businessId).findFirst();
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        ChannelBinding binding = selected.get();
        Template template = templates.get(binding.templateId());
        if (template == null || isLegacyAssigned(binding.templateId())
                || !binding.fingerprint().equals(fingerprint(template.payload()))) {
            return Optional.empty();
        }
        Map<String, Object> botSettings = new LinkedHashMap<>();
        botSettings.put("question_templates", List.of(template.payload()));
        botSettings.put("active_template_id", template.id());
        return Optional.of(new ScopedRuntimeConfig(channelId, businessId, template.id(),
                binding.fingerprint(), botSettings, Map.of(), Map.of()));
    }

    private List<Template> loadTemplates() {return loadTemplates(false);}
    private List<Template> loadTemplates(boolean withResources) {
        Object raw = normalizer.normalize(sharedConfigService.loadSettings().get("bot_settings"))
                .get("question_templates");
        if (!(raw instanceof List<?> source)) {
            return List.of();
        }
        List<Template> templates = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Set<String> duplicateIds = new HashSet<>();
        for (Object item : source) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    payload.put(key.toString(), value);
                }
            });
            String id = text(payload.get("id"));
            Object flow = payload.get("question_flow");
            if (id.isEmpty() || !(flow instanceof List<?> questions) || questions.isEmpty()
                    || questions.stream().anyMatch(question -> !(question instanceof Map<?, ?> questionMap)
                        || text(questionMap.get("id")).isEmpty())
                    || (!withResources && hasUnsupportedPresets(questions))) {
                continue;
            }
            if (!seenIds.add(id)) {
                duplicateIds.add(id);
            }
            templates.add(new Template(id, text(payload.get("name")), payload));
        }
        templates.removeIf(template -> duplicateIds.contains(template.id()));
        return templates;
    }

    private boolean isLegacyAssigned(String templateId) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM shared_channel_general_question_template_bindings WHERE template_id = ?)
                """, Boolean.class, templateId))) {
            return true;
        }
        Map<String, Object> botSettings = normalizer.normalize(
                sharedConfigService.loadSettings().get("bot_settings"));
        if (templateId.equals(text(botSettings.get("active_template_id")))) {
            return true;
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM general_channel_question_template_bindings
                                WHERE template_id = ? AND is_active = TRUE)
                """, Boolean.class, templateId))) {
            return true;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM channels WHERE question_template_id = ?)
                """, Boolean.class, templateId));
    }

    // Until canonical business_locations are wired to bot presets, no global location tree is exposed.
    private boolean hasUnsupportedPresets(List<?> questions) {
        return questions.stream().anyMatch(question -> question instanceof Map<?, ?> map
                && ("preset".equalsIgnoreCase(text(map.get("type"))) || map.get("preset") != null));
    }

    private String fingerprint(Map<String, Object> payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(payload)));
        } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
            throw new IllegalStateException("Cannot fingerprint question template", ex);
        }
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().strip();
    }

    private void requirePositive(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid identifier");
        }
    }

    private record Template(String id, String name, Map<String, Object> payload) {
    }

    private record ChannelBinding(long businessId, String resolutionMode, String businessStatus,
                                  String templateId, String fingerprint, boolean templateActive) {
    }

    public record TemplateSummary(String id, String name) {
    }

    private record ChoiceBinding(long businessId,String code,String name,String status,String mode,
                                 String templateId,String sha256,boolean active) { }
    private record ChoiceSnapshot(ChoiceCatalog catalog,Map<Long,SelectedRuntime> runtimes) { }
    public record ExpandedSnapshot(ChoiceCatalog catalog,SelectedRuntime selected) { }
    public record Choice(String scopeKind,long businessId,String code,String displayName,
                         String questionTemplateId,String templateSha256,
                         @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String resourcesSha256) {
        public Choice(String kind,long businessId,String code,String name,String template,String sha){this(kind,businessId,code,name,template,sha,null);}
    }
    public record ChoiceCatalog(int version,long channelId,String catalogSha256,List<Choice> choices) { }
    public record SelectedRuntime(int version,long channelId,String scopeKind,String scopeSelectionSource,
                                  long businessId,String questionTemplateId,String templateSha256,String catalogSha256,
                                  Map<String,Object> botSettings,Map<String,Object> locationTree,Map<String,Object> presetDefinitions) { }

    public record Binding(long businessId, String templateId, String templateSha256, boolean active) {
    }

    public record ScopedRuntimeConfig(long channelId, long businessId, String questionTemplateId,
                                      String templateSha256, Map<String, Object> botSettings,
                                      Map<String, Object> locationTree,
                                      Map<String, Object> presetDefinitions) {
    }
}
