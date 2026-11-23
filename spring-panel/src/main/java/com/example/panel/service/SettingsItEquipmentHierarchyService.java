package com.example.panel.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SettingsItEquipmentHierarchyService {

    private final JdbcTemplate jdbcTemplate;

    public SettingsItEquipmentHierarchyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> listHierarchy() {
        return Map.of("success", true, "branches", loadBranches());
    }

    @Transactional
    public Map<String, Object> createBranch(Map<String, Object> payload) {
        String kind = normalizeKind(payload.get("node_kind"));
        String type = value(payload.get("equipment_type"));
        String vendor = "vendor".equals(kind) ? value(payload.get("equipment_vendor")) : "";
        String error = validatePath(kind, type, vendor);
        if (error != null) return Map.of("success", false, "error", error);
        if (branchExists(kind, type, vendor, null)) {
            return Map.of("success", false, "error", "Такая ветка уже есть в иерархии");
        }
        jdbcTemplate.update("""
                INSERT INTO it_equipment_hierarchy_branches(node_kind, equipment_type, equipment_vendor, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, kind, type, vendor);
        return Map.of("success", true, "branches", loadBranches());
    }

    @Transactional
    public Map<String, Object> renameBranch(long branchId, Map<String, Object> payload) {
        Branch branch = findBranch(branchId);
        if (branch == null) return Map.of("success", false, "error", "Ветка иерархии не найдена");
        String name = value(payload.get("name"));
        if (!StringUtils.hasText(name)) return Map.of("success", false, "error", "Укажите новое название ветки");

        String targetType = "type".equals(branch.kind()) ? name : branch.type();
        String targetVendor = "vendor".equals(branch.kind()) ? name : "";
        if (branchExists(branch.kind(), targetType, targetVendor, branchId)) {
            return Map.of("success", false, "error", "Такая ветка уже есть в иерархии");
        }
        if ("type".equals(branch.kind()) && !sameValue(branch.type(), targetType)) {
            if (hasVendorBranchCollisions(branch.type(), targetType)) {
                return Map.of("success", false, "error", "В новом типе уже есть производитель с таким названием");
            }
            if (hasConfigurationCollisions(branch.type(), targetType)) {
                return Map.of("success", false, "error", "Переименование конфликтует с настройками комплектации нового типа");
            }
        }

        List<Leaf> leaves = loadLeaves(branch);
        for (Leaf leaf : leaves) {
            Leaf target = "type".equals(branch.kind())
                    ? new Leaf(leaf.id(), targetType, leaf.vendor(), leaf.model())
                    : new Leaf(leaf.id(), leaf.type(), targetVendor, leaf.model());
            Integer collisions = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM it_equipment_catalog
                    WHERE LOWER(equipment_type) = LOWER(?)
                      AND LOWER(equipment_vendor) = LOWER(?)
                      AND LOWER(equipment_model) = LOWER(?)
                      AND id <> ?
                    """, Integer.class, target.type(), target.vendor(), target.model(), target.id());
            if (collisions != null && collisions > 0) {
                return Map.of("success", false, "error", "Переименование создаёт повторяющуюся модель в каталоге");
            }
        }

        for (Leaf leaf : leaves) {
            Leaf target = "type".equals(branch.kind())
                    ? new Leaf(leaf.id(), targetType, leaf.vendor(), leaf.model())
                    : new Leaf(leaf.id(), leaf.type(), targetVendor, leaf.model());
            jdbcTemplate.update("""
                    UPDATE it_equipment_catalog_aliases
                    SET target_type = ?, target_vendor = ?, target_model = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE LOWER(target_type) = LOWER(?)
                      AND LOWER(target_vendor) = LOWER(?)
                      AND LOWER(target_model) = LOWER(?)
                    """, target.type(), target.vendor(), target.model(), leaf.type(), leaf.vendor(), leaf.model());
            jdbcTemplate.update("""
                    INSERT INTO it_equipment_catalog_aliases(
                        source_type, source_vendor, source_model, target_type, target_vendor, target_model, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (source_type, source_vendor, source_model) DO UPDATE
                    SET target_type = EXCLUDED.target_type,
                        target_vendor = EXCLUDED.target_vendor,
                        target_model = EXCLUDED.target_model,
                        updated_at = CURRENT_TIMESTAMP
                    """, leaf.type(), leaf.vendor(), leaf.model(), target.type(), target.vendor(), target.model());
        }
        if ("type".equals(branch.kind())) {
            jdbcTemplate.update("UPDATE it_equipment_catalog SET equipment_type = ?, updated_at = CURRENT_TIMESTAMP WHERE LOWER(equipment_type) = LOWER(?)", targetType, branch.type());
            jdbcTemplate.update("UPDATE it_equipment_hierarchy_branches SET equipment_type = ?, updated_at = CURRENT_TIMESTAMP WHERE LOWER(equipment_type) = LOWER(?)", targetType, branch.type());
            jdbcTemplate.update("UPDATE it_equipment_attribute_definitions SET equipment_type = ?, updated_at = CURRENT_TIMESTAMP WHERE LOWER(equipment_type) = LOWER(?)", targetType, branch.type());
            jdbcTemplate.update("UPDATE it_equipment_configuration_profiles SET equipment_type = ?, updated_at = CURRENT_TIMESTAMP WHERE LOWER(equipment_type) = LOWER(?)", targetType, branch.type());
        } else {
            jdbcTemplate.update("""
                    UPDATE it_equipment_catalog SET equipment_vendor = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE LOWER(equipment_type) = LOWER(?) AND LOWER(equipment_vendor) = LOWER(?)
                    """, targetVendor, branch.type(), branch.vendor());
            jdbcTemplate.update("""
                    UPDATE it_equipment_hierarchy_branches SET equipment_vendor = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE node_kind = 'vendor' AND LOWER(equipment_type) = LOWER(?) AND LOWER(equipment_vendor) = LOWER(?)
                    """, targetVendor, branch.type(), branch.vendor());
        }
        return Map.of("success", true, "branches", loadBranches());
    }

    @Transactional
    public Map<String, Object> deleteBranch(long branchId) {
        Branch branch = findBranch(branchId);
        if (branch == null) return Map.of("success", false, "error", "Ветка иерархии не найдена");
        Integer modelCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM it_equipment_catalog
                WHERE LOWER(equipment_type) = LOWER(?)
                  AND (? = 'type' OR LOWER(equipment_vendor) = LOWER(?))
                """, Integer.class, branch.type(), branch.kind(), branch.vendor());
        Integer childCount = "type".equals(branch.kind()) ? jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM it_equipment_hierarchy_branches
                WHERE node_kind = 'vendor' AND LOWER(equipment_type) = LOWER(?)
                """, Integer.class, branch.type()) : 0;
        if ((modelCount != null && modelCount > 0) || (childCount != null && childCount > 0)) {
            return Map.of("success", false, "error", "Сначала удалите или перенесите модели и дочерние ветки");
        }
        jdbcTemplate.update("DELETE FROM it_equipment_hierarchy_branches WHERE id = ?", branchId);
        return Map.of("success", true, "branches", loadBranches());
    }

    private List<Leaf> loadLeaves(Branch branch) {
        return jdbcTemplate.query("""
                SELECT id, equipment_type, equipment_vendor, equipment_model FROM it_equipment_catalog
                WHERE LOWER(equipment_type) = LOWER(?)
                  AND (? = 'type' OR LOWER(equipment_vendor) = LOWER(?))
                """, (rs, rowNum) -> new Leaf(rs.getLong("id"), rs.getString("equipment_type"), rs.getString("equipment_vendor"), rs.getString("equipment_model")),
                branch.type(), branch.kind(), branch.vendor());
    }

    private List<Map<String, Object>> loadBranches() {
        return jdbcTemplate.query("""
                SELECT id, node_kind, equipment_type, equipment_vendor FROM it_equipment_hierarchy_branches
                ORDER BY LOWER(equipment_type), node_kind, LOWER(equipment_vendor), id
                """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("node_kind", rs.getString("node_kind"));
            row.put("equipment_type", rs.getString("equipment_type"));
            row.put("equipment_vendor", rs.getString("equipment_vendor"));
            return row;
        });
    }

    private Branch findBranch(long id) {
        List<Branch> rows = jdbcTemplate.query("SELECT id, node_kind, equipment_type, equipment_vendor FROM it_equipment_hierarchy_branches WHERE id = ?",
                (rs, rowNum) -> new Branch(rs.getLong("id"), rs.getString("node_kind"), rs.getString("equipment_type"), rs.getString("equipment_vendor")), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean branchExists(String kind, String type, String vendor, Long excludedId) {
        String sql = "SELECT COUNT(*) FROM it_equipment_hierarchy_branches WHERE node_kind = ? AND LOWER(equipment_type) = LOWER(?) AND LOWER(equipment_vendor) = LOWER(?)" + (excludedId == null ? "" : " AND id <> ?");
        Integer count = excludedId == null
                ? jdbcTemplate.queryForObject(sql, Integer.class, kind, type, vendor)
                : jdbcTemplate.queryForObject(sql, Integer.class, kind, type, vendor, excludedId);
        return count != null && count > 0;
    }

    private boolean hasVendorBranchCollisions(String sourceType, String targetType) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM it_equipment_hierarchy_branches source
                JOIN it_equipment_hierarchy_branches target
                  ON source.node_kind = 'vendor'
                 AND target.node_kind = 'vendor'
                 AND LOWER(source.equipment_vendor) = LOWER(target.equipment_vendor)
                WHERE LOWER(source.equipment_type) = LOWER(?)
                  AND LOWER(target.equipment_type) = LOWER(?)
                  AND source.id <> target.id
                """, Integer.class, sourceType, targetType);
        return count != null && count > 0;
    }

    private boolean hasConfigurationCollisions(String sourceType, String targetType) {
        Integer attributeCollisions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM it_equipment_attribute_definitions source
                JOIN it_equipment_attribute_definitions target
                  ON LOWER(source.attribute_key) = LOWER(target.attribute_key)
                WHERE LOWER(source.equipment_type) = LOWER(?)
                  AND LOWER(target.equipment_type) = LOWER(?)
                  AND source.id <> target.id
                """, Integer.class, sourceType, targetType);
        if (attributeCollisions != null && attributeCollisions > 0) {
            return true;
        }
        Integer profileCollisions = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM it_equipment_configuration_profiles source
                JOIN it_equipment_configuration_profiles target
                  ON LOWER(source.profile_name) = LOWER(target.profile_name)
                WHERE LOWER(source.equipment_type) = LOWER(?)
                  AND LOWER(target.equipment_type) = LOWER(?)
                  AND source.id <> target.id
                """, Integer.class, sourceType, targetType);
        return profileCollisions != null && profileCollisions > 0;
    }

    private String validatePath(String kind, String type, String vendor) {
        if (!"type".equals(kind) && !"vendor".equals(kind)) return "Неизвестный уровень иерархии";
        if (!StringUtils.hasText(type)) return "Укажите тип оборудования";
        if ("vendor".equals(kind) && !StringUtils.hasText(vendor)) return "Укажите производителя";
        return null;
    }

    private String normalizeKind(Object raw) {
        return value(raw).toLowerCase(Locale.ROOT);
    }

    private boolean sameValue(String first, String second) {
        return value(first).equalsIgnoreCase(value(second));
    }

    private String value(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    private record Branch(long id, String kind, String type, String vendor) { }
    private record Leaf(long id, String type, String vendor, String model) { }
}
