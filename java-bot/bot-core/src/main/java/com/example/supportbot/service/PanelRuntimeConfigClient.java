package com.example.supportbot.service;

import com.example.supportbot.config.IntegrationPanelApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PanelRuntimeConfigClient {


    private final IntegrationPanelApiProperties properties;
    private final ObjectMapper objectMapper;
    private final PanelApiRequestHeadersFactory requestHeadersFactory;
    private final HttpClient httpClient;

    public PanelRuntimeConfigClient(IntegrationPanelApiProperties properties,
                                    ObjectMapper objectMapper,
                                    PanelApiRequestHeadersFactory requestHeadersFactory) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.requestHeadersFactory = requestHeadersFactory;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public boolean isEnabled() {
        return properties.isConfigured();
    }

    public Optional<RuntimeConfigSnapshot> findByChannelId(Long channelId) {
        if (!isEnabled() || channelId == null || channelId <= 0) {
            return Optional.empty();
        }
        // Each new lookup re-proves channel mode; a cached legacy config must not survive a business rebind.
        return fetch(channelId);
    }

    private Optional<RuntimeConfigSnapshot> fetch(Long channelId) {
        String path = "/internal/api/bot/channels/" + channelId + "/runtime-config";
        RuntimeConfigResponse payload=fetchTyped(path,RuntimeConfigResponse.class);
        if (payload==null || !channelId.equals(payload.channelId()) || payload.botSettings()==null
                || payload.locationTree()==null || payload.presetDefinitions()==null) throw unavailable();
        var snapshot=payload.toSnapshot();
        boolean legacy=snapshot.scopeKind()==null && snapshot.scopeSelectionSource()==null
            && snapshot.questionTemplateId()==null && snapshot.templateSha256()==null;
        if (!legacy && !snapshot.isGeneralChannel()) throw unavailable();
        return Optional.of(snapshot);
    }

    public ChoiceCatalog choices(long channelId) {
        return choices(channelId, false);
    }

    public ChoiceCatalog choicesForStart(long channelId) {
        return choices(channelId, true);
    }

    private ChoiceCatalog choices(long channelId, boolean forStart) {
        requireChannel(channelId);
        ChoiceCatalog catalog=fetchTyped("/internal/api/bot/channels/"+channelId+"/questionnaire/choices"
            +(forStart ? "?forStart=true" : ""),ChoiceCatalog.class,forStart);
        if (catalog == null || catalog.version() != 1) throw unavailable();
        validateCatalog(channelId,catalog);
        return catalog;
    }

    public ChoiceCatalog choicesV2ForStart(long channelId) {
        requireChannel(channelId);
        var catalog=fetchTyped("/internal/api/bot/channels/"+channelId+"/questionnaire/v2/choices?forStart=true",ChoiceCatalog.class,2);
        if(catalog==null || catalog.version()!=2) throw unavailable();
        validateCatalog(channelId,catalog);return catalog;
    }
    public ChoiceCatalog choicesV3ForStart(long channelId) {
        requireChannel(channelId);
        var catalog=fetchTyped("/internal/api/bot/channels/"+channelId+"/questionnaire/v3/choices?forStart=true",ChoiceCatalog.class,3);
        if(catalog==null || catalog.version()!=3) throw unavailable();
        validateCatalog(channelId,catalog);return catalog;
    }
    /** V4 has a bounded complete catalog, paginated locally from its durable session snapshot. */
    public ChoiceCatalog choicesV4ForStart(long channelId) {
        requireChannel(channelId);
        var catalog=fetchTyped("/internal/api/bot/channels/"+channelId+"/questionnaire/v4/choices?forStart=true",ChoiceCatalog.class,4);
        if(catalog==null || catalog.version()!=4) throw unavailable();
        validateCatalog(channelId,catalog);return catalog;
    }
    public SelectedQuestionnaire selectGeneralForStart(ChoiceCatalog catalog) {
        if(catalog==null || (catalog.version()!=2 && catalog.version()!=3 && catalog.version()!=4)) throw unavailable();
        return select(catalog,"GENERAL",null,true);
    }

    public SelectedQuestionnaire select(ChoiceCatalog catalog,long businessId) {
        return select(catalog, businessId, false);
    }

    public SelectedQuestionnaire selectForStart(ChoiceCatalog catalog,long businessId) {
        return select(catalog, businessId, true);
    }

    private SelectedQuestionnaire select(ChoiceCatalog catalog,long businessId,boolean forStart) {
        return select(catalog,"BUSINESS",businessId,forStart);
    }
    private SelectedQuestionnaire select(ChoiceCatalog catalog,String kind,Long businessId,boolean forStart) {
        if (catalog==null) throw unavailable();
        requireChannel(catalog.channelId());validateCatalog(catalog.channelId(),catalog);
        Choice choice=catalog.choices().stream().filter(item->kind.equals(item.scopeKind()) && java.util.Objects.equals(item.businessId(),businessId)).findFirst().orElseThrow(PanelRuntimeConfigClient::unavailable);
        SelectedQuestionnaire selected=fetchTyped("/internal/api/bot/channels/"+catalog.channelId()
            +"/questionnaire/"+(catalog.version()>=2?"v"+catalog.version()+"/":"")+"selection?"
            +(catalog.version()>=2?"scopeKind="+kind+"&":"")+(businessId==null?"":"businessId="+businessId+"&")
            +"catalogSha256="+catalog.catalogSha256()+(forStart ? "&forStart=true" : ""),SelectedQuestionnaire.class,forStart?catalog.version():0);
        if (selected==null || selected.version()!=catalog.version() || selected.channelId()!=catalog.channelId() || !java.util.Objects.equals(selected.businessId(),businessId)
                || !kind.equals(selected.scopeKind()) || !"USER_SELECTION".equals(selected.scopeSelectionSource())
                || !catalog.catalogSha256().equals(selected.catalogSha256()) || !choice.questionTemplateId().equals(selected.questionTemplateId())
                || !choice.templateSha256().equals(selected.templateSha256()) || selected.locationTree()==null || (selected.version()<3 && !selected.locationTree().isEmpty())
                || selected.presetDefinitions()==null || (selected.version()<3 && !selected.presetDefinitions().isEmpty()) || selected.botSettings()==null
                || !selected.botSettings().keySet().equals(java.util.Set.of("active_template_id","question_templates"))
                || !choice.questionTemplateId().equals(selected.botSettings().get("active_template_id"))
                || !(selected.botSettings().get("question_templates") instanceof List<?> templates) || templates.size()!=1
                || !(templates.get(0) instanceof Map<?,?> template) || !choice.questionTemplateId().equals(template.get("id"))) throw unavailable();
        try {
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(objectMapper.writeValueAsBytes(template)));
            if (!actual.equals(choice.templateSha256())) throw unavailable();
        } catch (java.security.NoSuchAlgorithmException | IOException ex) { throw unavailable(); }
        try {SelectedQuestionnaireOptions.validate(selected,catalog);} catch(IllegalStateException | IllegalArgumentException ex) {throw unavailable();}
        return selected;
    }

    private void requireChannel(long channelId) {
        if (!isEnabled() || channelId<=0) throw unavailable();
    }
    public static void validateCatalog(long channelId,ChoiceCatalog catalog) {
        if (channelId<=0 || catalog==null || (catalog.version()!=1 && catalog.version()!=2 && catalog.version()!=3 && catalog.version()!=4) || catalog.channelId()!=channelId || !hash(catalog.catalogSha256())
                || catalog.choices()==null || catalog.choices().isEmpty() || catalog.choices().size()>(catalog.version()==4?1000:100)) throw unavailable();
        var businesses=new HashSet<Long>();var templates=new HashSet<String>();boolean general=false;
        for (Choice choice:catalog.choices()) {
            boolean isGeneral=choice!=null && catalog.version()>=2 && "GENERAL".equals(choice.scopeKind()) && choice.businessId()==null;
            boolean isBusiness=choice!=null && "BUSINESS".equals(choice.scopeKind()) && choice.businessId()!=null && choice.businessId()>0;
            if (choice==null || (!isGeneral && !isBusiness) || (isGeneral && general)
                    || (isBusiness && !businesses.add(choice.businessId()))
                    || choice.code()==null || choice.code().isBlank() || choice.displayName()==null || choice.displayName().isBlank()
                    || choice.questionTemplateId()==null || choice.questionTemplateId().isBlank() || !templates.add(choice.questionTemplateId())
                    || !hash(choice.templateSha256()) || (catalog.version()>=3 && !hash(choice.resourcesSha256()))) throw unavailable();
            general|=isGeneral;
        }
        if(businesses.isEmpty()) throw unavailable();
    }
    private static boolean hash(String value) { return value!=null && value.matches("[a-f0-9]{64}"); }
    private static ScopedRuntimeConfigUnavailableException unavailable() {
        return new ScopedRuntimeConfigUnavailableException("Verified questionnaire runtime is unavailable");
    }
    private <T> T fetchTyped(String path,Class<T> type) {
        return fetchTyped(path,type,false);
    }
    private <T> T fetchTyped(String path,Class<T> type,boolean requireWriter) {
        return fetchTyped(path,type,requireWriter?1:0);
    }
    private <T> T fetchTyped(String path,Class<T> type,int writerVersion) {
        URI uri = resolve(path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(properties.getRequestTimeout())
            .GET();
        requestHeadersFactory.apply(builder::header, "GET", uri, null, null);
        HttpRequest request = builder.build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable();
            }
            if(writerVersion>0 && !Integer.toString(writerVersion).equals(response.headers().firstValue("X-Iguana-Questionnaire-Writer-Version").orElse(null))) {
                throw unavailable();
            }
            return objectMapper.readValue(response.body(),type);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw unavailable();
        }
    }

    private URI resolve(String path) {
        String baseUrl = properties.getBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBase + normalizedPath);
    }

    public record RuntimeConfigSnapshot(Long channelId,
                                        Map<String, Object> botSettings,
                                        Map<String, Object> locationTree,
                                        Map<String, Object> presetDefinitions,
                                        String scopeKind,
                                        String scopeSelectionSource,
                                        String questionTemplateId,
                                        String templateSha256) {
        public boolean isGeneralChannel() {
            return "GENERAL".equals(scopeKind) && "GENERAL_CHANNEL".equals(scopeSelectionSource)
                && questionTemplateId != null && !questionTemplateId.isBlank()
                && templateSha256 != null && !templateSha256.isBlank();
        }
    }

    public static class ScopedRuntimeConfigUnavailableException extends RuntimeException {
        public ScopedRuntimeConfigUnavailableException(String message) {
            super(message);
        }
    }

    public record Choice(String scopeKind,Long businessId,String code,String displayName,String questionTemplateId,String templateSha256,String resourcesSha256) {
        public Choice(String kind,Long businessId,String code,String name,String template,String sha){this(kind,businessId,code,name,template,sha,null);}
        public Choice(String scopeKind,long businessId,String code,String displayName,String questionTemplateId,String templateSha256) {
            this(scopeKind,Long.valueOf(businessId),code,displayName,questionTemplateId,templateSha256,null);
        }
        public boolean general() {return "GENERAL".equals(scopeKind) && businessId==null;}
    }
    public record ChoiceCatalog(int version,long channelId,String catalogSha256,List<Choice> choices) { }
    public record SelectedQuestionnaire(int version,long channelId,String scopeKind,String scopeSelectionSource,Long businessId,
        String questionTemplateId,String templateSha256,String catalogSha256,Map<String,Object> botSettings,
        Map<String,Object> locationTree,Map<String,Object> presetDefinitions) {
        public SelectedQuestionnaire(int version,long channelId,String scopeKind,String source,long businessId,
                String template,String sha,String catalog,Map<String,Object> settings,Map<String,Object> locations,Map<String,Object> presets) {
            this(version,channelId,scopeKind,source,Long.valueOf(businessId),template,sha,catalog,settings,locations,presets);
        }
    }

    private record RuntimeConfigResponse(Long channelId,
                                         Map<String, Object> botSettings,
                                         Map<String, Object> locationTree,
                                         Map<String, Object> presetDefinitions,
                                         String scopeKind,
                                         String scopeSelectionSource,
                                         String questionTemplateId,
                                         String templateSha256) {
        private RuntimeConfigSnapshot toSnapshot() {
            return new RuntimeConfigSnapshot(
                channelId,
                copyMap(botSettings),
                copyMap(locationTree),
                copyMap(presetDefinitions),
                scopeKind,
                scopeSelectionSource,
                questionTemplateId,
                templateSha256
            );
        }

        private static Map<String, Object> copyMap(Map<String, Object> source) {
            return source != null ? new LinkedHashMap<>(source) : new LinkedHashMap<>();
        }
    }
}
