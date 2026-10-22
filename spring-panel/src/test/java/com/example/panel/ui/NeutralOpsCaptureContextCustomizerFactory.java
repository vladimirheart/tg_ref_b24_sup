package com.example.panel.ui;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Опциональный экспорт настоящего MVC-render для визуального QA без рабочей БД. */
public class NeutralOpsCaptureContextCustomizerFactory implements ContextCustomizerFactory {
    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
            List<ContextConfigurationAttributes> configurationAttributes) {
        String destination = System.getProperty("iguana.ui.capture");
        if (destination == null || destination.isBlank()) return null;
        Path directory = Path.of(destination).toAbsolutePath().normalize();
        return (context, mergedConfig) -> context.getBeanFactory().registerSingleton(
                "neutralOpsRenderedScreenCapture", new CaptureConfigurer(directory));
    }

    private record CaptureConfigurer(Path directory) implements WebMvcConfigurer {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new HandlerInterceptor() {
                @Override
                public void postHandle(HttpServletRequest request, HttpServletResponse response,
                        Object handler, ModelAndView model) {
                    if (model != null) request.setAttribute("neutralOpsCaptureView", model.getViewName());
                }

                @Override
                public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                        Object handler, Exception exception) throws Exception {
                    ServletResponse original = response;
                    while (original instanceof ServletResponseWrapper wrapper) original = wrapper.getResponse();
                    if (exception != null || !(original instanceof MockHttpServletResponse mock)
                            || mock.getStatus() != 200) return;
                    Object view = request.getAttribute("neutralOpsCaptureView");
                    if (!(view instanceof String name) || !name.matches("[a-zA-Z0-9_/-]+")) return;
                    String html = mock.getContentAsString(StandardCharsets.UTF_8);
                    if (!html.contains("<body")) return;
                    Path output = directory.resolve(name + ".html").normalize();
                    if (!output.startsWith(directory)) throw new IllegalArgumentException("Invalid capture path");
                    Files.createDirectories(output.getParent());
                    Files.writeString(output, html, StandardCharsets.UTF_8);
                }
            });
        }
    }
}
