package com.example.panel.service;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.example.panel.repository.PanelUserRepository;

class SharedWorkPanelTemplateTest {
    @Test void actualThymeleafFragmentsComposeForBothObjectKinds() {
        var inline=new StringTemplateResolver();inline.setOrder(1);inline.setResolvablePatterns(Set.of("<*"));
        var files=new FileTemplateResolver();files.setOrder(2);
        files.setPrefix(Path.of("src/main/resources/templates").toAbsolutePath()+"/");files.setSuffix(".html");
        files.setCharacterEncoding("UTF-8");files.setCheckExistence(true);
        var engine=new SpringTemplateEngine();engine.addTemplateResolver(inline);engine.addTemplateResolver(files);
        for(String kind:Set.of("TASK","PROJECT")){
            String html=engine.process("<html xmlns:th=\"http://www.thymeleaf.org\"><body>"+
                "<section th:replace=\"~{fragments/shared-work-access :: moduleAccess}\"></section>"+
                "<section th:replace=\"~{fragments/shared-work-access :: objectAccess('"+kind+"')}\"></section></body></html>",new Context());
            assertThat(html).contains("data-work-object=\""+kind+"\"","data-work-module","data-work-transfer",
                "Доступ и участники","Допуск к модулю").doesNotContain("th:replace","th:attr");
        }
    }
    @Test void adminWithoutPageCanDiscoverTasksAndProjectsInNavigation() {
        var permissions=mock(PermissionService.class);
        var authentication=mock(org.springframework.security.core.Authentication.class);
        when(permissions.hasAuthority(authentication,"ROLE_ADMIN")).thenReturn(true);
        var service=new NavigationService(permissions,mock(PanelUserRepository.class),
            mock(UnblockRequestService.class),mock(PanelUserPhotoService.class));
        var model=new ExtendedModelMap();service.enrich(model,authentication);
        assertThat(model.getAttribute("canManageTasks")).isEqualTo(true);
        when(permissions.hasAuthority(authentication,"ROLE_ADMIN")).thenReturn(false);
        service.enrich(model,authentication);assertThat(model.getAttribute("canManageTasks")).isEqualTo(false);
    }
}
