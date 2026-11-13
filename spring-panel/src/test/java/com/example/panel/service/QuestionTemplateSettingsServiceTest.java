package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuestionTemplateSettingsServiceTest {
    final ObjectMapper mapper=new ObjectMapper();
    SharedConfigService config;JdbcTemplate jdbc;PlatformTransactionManager manager;
    BusinessQuestionTemplateRegistryService business;GeneralChannelQuestionTemplateService general;
    SharedGeneralQuestionnaireService shared;QuestionTemplateSettingsService service;
    Map<String,Object> before,after,template;List<Map<String,Object>> rows;

    @BeforeEach void setup() {
        config=mock(SharedConfigService.class);jdbc=mock(JdbcTemplate.class);manager=mock(PlatformTransactionManager.class);
        business=mock(BusinessQuestionTemplateRegistryService.class);general=mock(GeneralChannelQuestionTemplateService.class);
        shared=mock(SharedGeneralQuestionnaireService.class);
        service=new QuestionTemplateSettingsService(config,new BotSettingsPayloadNormalizer(),mapper,jdbc,manager,business,general,shared);
        template=new LinkedHashMap<>(Map.of("id","t","question_flow",List.of(Map.of("id","q","type","custom"))));
        before=Map.of("bot_settings",Map.of("question_templates",List.of(Map.of("id","t","name","old"))),"unrelated","preserved");
        after=Map.of("bot_settings",Map.of("question_templates",List.of(template)),"unrelated","preserved");
        when(config.loadSettings()).thenReturn(before);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        rows=new ArrayList<>();
        when(jdbc.queryForList(startsWith("SELECT 'BUSINESS'"))).thenAnswer(invocation->List.copyOf(rows));
        when(business.bind(1,"t")).thenReturn(Optional.of(new BusinessQuestionTemplateRegistryService.Binding(1,"t","new",true)));
        when(general.bind(2,"t")).thenReturn(Optional.of(new GeneralChannelQuestionTemplateService.Binding(2,"t","new",true)));
        when(shared.bind(3,"t")).thenReturn(Optional.of(new SharedGeneralQuestionnaireService.Binding(3,"t","new",true)));
    }
    Map<String,Object> row(String kind,long owner,String sha) {
        return Map.of("kind",kind,"owner_id",owner,"template_id","t","template_sha256",sha);
    }
    String hash()throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(template)));
    }
    @Test void refreshesExistingScopesThroughTheirValidatorsAndKeepsSettings() {
        rows.addAll(List.of(row("BUSINESS",1,"old"),row("GENERAL",2,"old"),row("SHARED_GENERAL",3,"old")));
        service.saveSettings(before,after);
        var order=inOrder(config,business,general,shared,manager);
        order.verify(config).saveSettings(after);order.verify(business).bind(1,"t");
        order.verify(general).bind(2,"t");order.verify(shared).bind(3,"t");order.verify(manager).commit(any());
        verify(manager,never()).rollback(any());
    }
    @Test void unchangedTemplateDoesNotRebindOrActivateAnyScope()throws Exception {
        rows.add(row("BUSINESS",1,hash()));service.saveSettings(before,after);
        verifyNoInteractions(business,general,shared);verify(config).saveSettings(after);
    }
    @Test void deletionOfBoundTemplateFailsBeforeWriting() {
        rows.add(row("BUSINESS",1,"old"));
        assertThatThrownBy(()->service.saveSettings(before,Map.of("bot_settings",Map.of("question_templates",List.of()))))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("назначенный");
        verify(config,never()).saveSettings(any());verify(manager).rollback(any());
    }
    @Test void duplicateIdsFailBeforeWriting() {
        assertThatThrownBy(()->service.saveSettings(before,Map.of("bot_settings",Map.of("question_templates",List.of(template,template)))))
            .isInstanceOf(IllegalArgumentException.class);
        verify(config,never()).saveSettings(any());
    }
    @ParameterizedTest @ValueSource(strings={"BUSINESS","GENERAL","SHARED_GENERAL"})
    void failedScopeValidationRestoresFileAndRollsBackAllBindings(String kind) {
        long owner=kind.equals("BUSINESS")?1:kind.equals("GENERAL")?2:3;rows.add(row(kind,owner,"old"));
        if(owner==1)when(business.bind(1,"t")).thenThrow(new IllegalArgumentException("invalid preset"));
        if(owner==2)when(general.bind(2,"t")).thenThrow(new IllegalArgumentException("invalid preset"));
        if(owner==3)when(shared.bind(3,"t")).thenThrow(new IllegalArgumentException("invalid preset"));
        assertThatThrownBy(()->service.saveSettings(before,after)).hasMessage("invalid preset");
        var order=inOrder(config);order.verify(config).saveSettings(after);order.verify(config).saveSettings(before);
        verify(manager).rollback(any());verify(manager,never()).commit(any());
    }
    @Test void unavailableOwnerDoesNotLeaveEditedFile() {
        rows.add(row("BUSINESS",1,"old"));when(business.bind(1,"t")).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.saveSettings(before,after)).hasMessageContaining("недоступна");
        verify(config).saveSettings(before);verify(manager).rollback(any());
    }
    @Test void commitFailureAlsoRestoresFile() {
        doThrow(new TransactionSystemException("commit failed")).when(manager).commit(any());
        assertThatThrownBy(()->service.saveSettings(before,after)).hasMessage("commit failed");
        verify(config).saveSettings(before);
    }
    @Test void staleSettingsAreRejectedWithoutOverwritingAnotherEdit() {
        when(config.loadSettings()).thenReturn(Map.of("other","edit"));
        assertThatThrownBy(()->service.saveSettings(before,after)).hasMessageContaining("изменились");
        verify(config,never()).saveSettings(any());
    }
    @Test void concurrentBindingChangeFailsBeforeWriting() {
        when(jdbc.queryForList(startsWith("SELECT 'BUSINESS'"))).thenReturn(List.of(row("BUSINESS",1,"old")),List.of());
        assertThatThrownBy(()->service.saveSettings(before,after)).hasMessageContaining("Привязки");
        verify(config,never()).saveSettings(any());
    }
}
