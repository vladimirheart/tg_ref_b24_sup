package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit canonical resources; legacy settings and external location aliases are never inputs. */
@Service
public class BusinessQuestionnaireResourcesService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public BusinessQuestionnaireResourcesService(JdbcTemplate jdbc,ObjectMapper mapper) {this.jdbc=jdbc;this.mapper=mapper;}

    public Optional<Resources> snapshot(long businessId) {
        var config=presets(businessId,false);
        if(config.isEmpty()) return Optional.empty();
        var locations=jdbc.query("""
            SELECT id,location_name,location_type,city FROM business_locations
            WHERE business_id=? AND status='active' ORDER BY id LIMIT 1001
            """,(rs,row)->Map.<String,Object>of("id",rs.getLong("id"),"name",rs.getString("location_name"),
                "locationType",rs.getString("location_type"),"city",rs.getString("city")),businessId);
        if(locations.size()>1000) throw new IllegalStateException("Too many questionnaire locations");
        var tree=Map.<String,Object>of("businessId",businessId,"locations",locations);
        var definitions=Map.<String,Object>of("businessId",businessId,"groups",config.get().groups());
        return Optional.of(new Resources(tree,definitions,fingerprint(List.of(tree,definitions))));
    }

    public Optional<Presets> presets(long businessId) {return presets(businessId,false);}
    private Optional<Presets> presets(long businessId,boolean lock) {
        if(businessId<=0) throw new IllegalArgumentException("Invalid business ID");
        var rows=jdbc.queryForList("SELECT settings::text FROM businesses WHERE id=? AND status='active'"+(lock?" FOR UPDATE":""),String.class,businessId);
        if(rows.isEmpty()) return Optional.empty();
        try {
            Map<String,Object> settings=mapper.readValue(rows.get(0),new TypeReference<>() {});
            Object raw=settings.getOrDefault("questionnaire_presets",Map.of());
            var groups=validateGroups(raw);
            return Optional.of(new Presets(businessId,fingerprint(groups),groups));
        } catch(java.io.IOException ex) {throw new IllegalStateException("Invalid business settings",ex);}
    }

    @Transactional
    public Optional<Presets> replacePresets(long businessId,String expectedSha256,Map<String,Object> groups) {
        var normalized=validateGroups(groups);
        var current=presets(businessId,true);
        if(current.isEmpty()) return Optional.empty();
        if(expectedSha256==null || !expectedSha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Preset version is required");
        if(!current.get().sha256().equals(expectedSha256)) throw new IllegalStateException("Preset version changed; reload before saving");
        try {
            jdbc.update("UPDATE businesses SET settings=jsonb_set(settings,'{questionnaire_presets}',?::jsonb),updated_at=CURRENT_TIMESTAMP WHERE id=?",
                mapper.writeValueAsString(normalized),businessId);
        } catch(java.io.IOException ex) {throw new IllegalArgumentException("Invalid preset payload",ex);}
        return Optional.of(new Presets(businessId,fingerprint(normalized),normalized));
    }

    public Map<String,Object> validateGroups(Object raw) {
        if(!(raw instanceof Map<?,?> groups) || groups.size()>20) throw new IllegalArgumentException("Invalid preset groups");
        Map<String,Object> result=new TreeMap<>(); int total=0;
        for(var group:groups.entrySet()) {
            if(!(group.getKey() instanceof String name) || !name.matches("[a-z][a-z0-9_]{0,39}") || "locations".equals(name)
                    || !(group.getValue() instanceof Map<?,?> fields) || fields.isEmpty() || fields.size()>20)
                throw new IllegalArgumentException("Invalid preset group");
            Map<String,Object> normalized=new TreeMap<>();
            for(var field:fields.entrySet()) {
                if(!(field.getKey() instanceof String key) || !key.matches("[a-z][a-z0-9_]{0,39}")
                        || !(field.getValue() instanceof List<?> values) || values.isEmpty() || values.size()>100)
                    throw new IllegalArgumentException("Invalid preset field");
                Set<String> unique=new LinkedHashSet<>();
                for(Object value:values) {
                    if(++total>1000) throw new IllegalArgumentException("Too many preset options");
                    if(!(value instanceof String text) || text.isBlank() || !text.equals(text.strip()) || text.length()>200
                            || text.chars().anyMatch(Character::isISOControl) || !unique.add(text))
                        throw new IllegalArgumentException("Invalid preset option");
                }
                normalized.put(key,List.copyOf(unique));
            }
            result.put(name,Collections.unmodifiableMap(normalized));
        }
        return Collections.unmodifiableMap(result);
    }

    public boolean supports(Map<String,Object> template,Resources resources) {
        if(!(template.get("question_flow") instanceof List<?> questions)) return false;
        Set<String> dimensions=new HashSet<>();
        for(Object item:questions) {
            if(!(item instanceof Map<?,?> q)) return false;
            if(q.get("preset")==null && !"preset".equalsIgnoreCase(Objects.toString(q.get("type"),""))) continue;
            if(!"preset".equalsIgnoreCase(Objects.toString(q.get("type"),"")) || !(q.get("preset") instanceof Map<?,?> ref)
                    || !ref.keySet().equals(Set.of("group","field")) || !(ref.get("group") instanceof String group)
                    || !(ref.get("field") instanceof String field)) return false;
            if("locations".equals(group)) {
                if(!Set.of("location_type","city","location_name").contains(field) || !dimensions.add(field)) return false;
                var rows=(List<?>)resources.locationTree().get("locations");
                String key=switch(field){case "location_type"->"locationType";case "location_name"->"name";default->"city";};
                if(rows.stream().noneMatch(row->row instanceof Map<?,?> m && m.get(key) instanceof String value && !value.isBlank())) return false;
            } else {
                var groups=(Map<?,?>)resources.presetDefinitions().get("groups");
                if(!(groups.get(group) instanceof Map<?,?> fields) || !(fields.get(field) instanceof List<?> values) || values.isEmpty()) return false;
            }
        }
        return true;
    }

    private static String fingerprint(Object value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new ObjectMapper().writeValueAsBytes(canonical(value))));}
        catch(Exception ex) {throw new IllegalStateException("Cannot fingerprint questionnaire resources",ex);}
    }
    private static Object canonical(Object value) {
        if(value instanceof Map<?,?> map){var sorted=new TreeMap<String,Object>();map.forEach((k,v)->sorted.put(k.toString(),canonical(v)));return sorted;}
        if(value instanceof List<?> list)return list.stream().map(BusinessQuestionnaireResourcesService::canonical).toList();
        return value;
    }
    public static String resourceSha256(Map<String,Object> tree,Map<String,Object> presets){return fingerprint(List.of(tree,presets));}
    public record Resources(Map<String,Object> locationTree,Map<String,Object> presetDefinitions,String sha256) {}
    public record Presets(long businessId,String sha256,Map<String,Object> groups) {}
}
