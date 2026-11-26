package com.example.panel.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import ch.qos.logback.classic.*;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class HttpRequestTimingFilterTest {
    @Test void correlatesAsyncCompletionOnceWithoutQueryOrBody() throws Exception {
        var logger=(Logger)LoggerFactory.getLogger(HttpRequestTimingFilter.class);
        var events=new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();events.start();logger.addAppender(events);
        try {
            var request=new MockHttpServletRequest("GET","/api/example");request.setAsyncSupported(true);
            request.addHeader("X-Request-ID","0123456789abcdef0123456789abcdef");
            request.setQueryString("private=fake-test-value");
            var response=new MockHttpServletResponse();MDC.put("requestId","previous");
            new HttpRequestTimingFilter().doFilter(request,response,(req,res)->{
                assertThat(MDC.get("requestId")).isEqualTo("0123456789abcdef0123456789abcdef");
                response.setStatus(503);request.startAsync();
            });
            assertThat(events.list).isEmpty();
            assertThat(MDC.get("requestId")).isEqualTo("previous");
            assertThat(response.getHeader("X-Request-ID")).isEqualTo("0123456789abcdef0123456789abcdef");
            var async=(MockAsyncContext)request.getAsyncContext();
            var event=new jakarta.servlet.AsyncEvent(async);
            for(var listener:async.getListeners()){listener.onError(event);listener.onComplete(event);}
            assertThat(events.list).hasSize(1);
            assertThat(events.list.get(0).getFormattedMessage()).contains("status=503","path=/api/example","duration_ms=")
                .doesNotContain("private","fake-test-value");
        } finally {MDC.remove("requestId");logger.detachAppender(events);events.stop();}
    }
}
