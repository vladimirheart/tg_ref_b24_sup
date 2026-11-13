package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Save edited templates and refresh only their existing active bindings through the scope validators. */
@Service
public class QuestionTemplateSettingsService {
    private final SharedConfigService config;
    private final BotSettingsPayloadNormalizer normalizer;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final BusinessQuestionTemplateRegistryService business;
    private final GeneralChannelQuestionTemplateService general;
    private final SharedGeneralQuestionnaireService shared;

    public QuestionTemplateSettingsService(SharedConfigService config, BotSettingsPayloadNormalizer normalizer,
            ObjectMapper mapper, JdbcTemplate jdbc, PlatformTransactionManager manager,
            BusinessQuestionTemplateRegistryService business, GeneralChannelQuestionTemplateService general,
            SharedGeneralQuestionnaireService shared) {
        this.config=config;this.normalizer=normalizer;this.mapper=mapper;this.jdbc=jdbc;
        this.transaction=new TransactionTemplate(manager);this.business=business;this.general=general;this.shared=shared;
    }

    public void saveSettings(Map<String,Object> expected,Map<String,Object> updated) {
        boolean[] written={false};
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext('question-template-settings'),0)");
                if(!config.loadSettings().equals(expected))
                    throw new IllegalStateException("Настройки изменились. Обновите страницу перед сохранением.");
                var bindings=bindings();
                bindings.stream().map(Binding::templateId).distinct().sorted().forEach(id ->
                    jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext('question-template-scope'),hashtext(?))",id));
                if(!bindings.equals(bindings()))
                    throw new IllegalStateException("Привязки опросников изменились. Обновите страницу.");
                var templates=new LinkedHashMap<String,Map<String,Object>>();
                Object raw=normalizer.normalize(updated.get("bot_settings")).get("question_templates");
                if(raw instanceof List<?> list)for(Object item:list)if(item instanceof Map<?,?> map) {
                    String id=Objects.toString(map.get("id"),"");
                    var payload=new LinkedHashMap<String,Object>();
                    map.forEach((key,value)->{if(key!=null)payload.put(key.toString(),value);});
                    if(templates.putIfAbsent(id,payload)!=null)
                        throw new IllegalArgumentException("Идентификаторы опросников не должны повторяться.");
                }
                var changed=new ArrayList<Binding>();
                for(var binding:bindings) {
                    var template=templates.get(binding.templateId());
                    if(template==null)
                        throw new IllegalArgumentException("Нельзя удалить назначенный опросник. Сначала измените его привязки.");
                    if(!binding.sha256().equals(hash(template)))changed.add(binding);
                }
                // Readers can reject an in-flight change; no stale binding is silently accepted.
                written[0]=true;
                config.saveSettings(updated);
                for(var binding:changed) {
                    boolean present=switch(binding.kind()) {
                        case "BUSINESS" -> business.bind(binding.ownerId(),binding.templateId()).isPresent();
                        case "GENERAL" -> general.bind(binding.ownerId(),binding.templateId()).isPresent();
                        case "SHARED_GENERAL" -> shared.bind(binding.ownerId(),binding.templateId()).isPresent();
                        default -> throw new IllegalStateException("Unknown questionnaire scope");
                    };
                    if(!present)throw new IllegalStateException("Привязка опросника больше недоступна.");
                }
            });
        } catch(RuntimeException failure) {
            // TransactionTemplate also exposes commit failures here, after DB rollback.
            if(written[0])try {config.saveSettings(expected);}
            catch(RuntimeException restoreFailure){failure.addSuppressed(restoreFailure);}
            throw failure;
        }
    }

    private List<Binding> bindings() {
        return jdbc.queryForList("""
            SELECT 'BUSINESS' AS kind,business_id AS owner_id,template_id,template_sha256
              FROM business_question_template_bindings WHERE is_active=TRUE
            UNION ALL
            SELECT 'GENERAL',channel_id,template_id,template_sha256
              FROM general_channel_question_template_bindings WHERE is_active=TRUE
            UNION ALL
            SELECT 'SHARED_GENERAL',channel_id,template_id,template_sha256
              FROM shared_channel_general_question_template_bindings WHERE is_active=TRUE
            ORDER BY kind,owner_id
            """).stream().map(row->new Binding(row.get("kind").toString(),((Number)row.get("owner_id")).longValue(),
                row.get("template_id").toString(),row.get("template_sha256").toString())).toList();
    }
    private String hash(Map<String,Object> value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)));}
        catch(Exception ex){throw new IllegalStateException("Не удалось проверить версию опросника.",ex);}
    }
    private record Binding(String kind,long ownerId,String templateId,String sha256) {}
}
