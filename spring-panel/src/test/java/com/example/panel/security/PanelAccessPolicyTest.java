package com.example.panel.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PanelAccessPolicyTest {
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    static Set<String> blocks() { return PanelAccessPolicy.BLOCKS.keySet(); }

    @ParameterizedTest @MethodSource("blocks")
    void aSettingsPageGrantDoesNotAllowEditingAnyBlock(String block) {
        var viewer = new UsernamePasswordAuthenticationToken("viewer", "", AuthorityUtils.createAuthorityList("PAGE_SETTINGS"));
        assertThat(PanelAccessPolicy.canEdit(viewer, block)).isFalse();
    }

    @ParameterizedTest @MethodSource("blocks")
    void eachRoleBlockMapsToOnlyItsOwnEditAuthority(String block) {
        String json = "{\"pages\":[\"settings\"],\"fields\":{\"edit\":[\"settings." + block + "\"]}}";
        assertThat(PanelAccessPolicy.roleAuthorities(json)).containsExactlyInAnyOrder(
                "PAGE_SETTINGS", PanelAccessPolicy.editAuthority("settings." + block));
    }

    @Test void individualDenialOverridesRoleAndIndividualAllowanceOverridesMissingRoleGrant() {
        var inherited = PanelAccessPolicy.roleAuthorities("{\"pages\":[\"settings\"],\"fields\":{\"edit\":[\"settings.dialogs\"]}}");
        var effective = PanelAccessPolicy.effective(inherited,
                Set.of("DENY_PAGE_SETTINGS", "DENY_EDIT_SETTINGS_DIALOGS", "PAGE_TASKS", "EDIT_SETTINGS_BACKUP"));
        assertThat(effective).contains("PAGE_TASKS", "EDIT_SETTINGS_BACKUP")
                .doesNotContain("PAGE_SETTINGS", "EDIT_SETTINGS_DIALOGS");
    }

    @Test void aPageWildcardStillDoesNotGrantEditingButTheEditWildcardDoes() {
        assertThat(PanelAccessPolicy.roleAuthorities("{\"pages\":[\"*\"]}"))
                .doesNotContain("EDIT_SETTINGS_DIALOGS").contains("PAGE_DASHBOARD", "PAGE_ANALYTICS");
        assertThat(PanelAccessPolicy.roleAuthorities("{\"fields\":{\"edit\":[\"*\"]}}"))
                .contains("EDIT_SETTINGS_DIALOGS", "EDIT_SETTINGS_BACKUP", "EDIT_USER_CREATE");
    }

    @Test void mixedPayloadIsRejectedBeforeAnyPersistenceAndAnUnknownKeyCannotBypassChecks() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "editor", "", AuthorityUtils.createAuthorityList("PAGE_SETTINGS", "EDIT_SETTINGS_DIALOGS")));
        PanelAccessPolicy.requirePayload(Map.of("dialog_category_templates", List.of()));
        assertThatThrownBy(() -> PanelAccessPolicy.requirePayload(Map.of("dialog_category_templates", List.of(), "reporting_config", Map.of())))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> PanelAccessPolicy.requirePayload(Map.of("unknown_configuration", true)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void malformedRoleJsonNeverGrantsRightsAndAdminIdentityExpandsKnownGrants() {
        assertThat(PanelAccessPolicy.roleAuthorities("{broken")).isEmpty();
        assertThat(PanelAccessPolicy.effective(Set.of("ROLE_ADMIN"), Set.of()))
                .contains("EDIT_SETTINGS_DIALOGS", "EDIT_SETTINGS_BACKUP").doesNotContain("PAGE_SETTINGS");
    }

    @Test void parameterTypesCannotUseTheGeneralDictionaryPermissionToEditAnotherBlock() {
        assertThat(PanelAccessPolicy.parameterBlock("city")).isEqualTo("parameters");
        assertThat(PanelAccessPolicy.parameterBlock("legal_entity")).isEqualTo("legal_entities");
        assertThat(PanelAccessPolicy.parameterBlock("partner_contact")).isEqualTo("legal_entities");
        assertThat(PanelAccessPolicy.parameterBlock("equipment_model")).isEqualTo("equipment");
        assertThat(PanelAccessPolicy.parameterBlock("it_connection")).isEqualTo("it_connections");
    }
}
