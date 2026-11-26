package com.example.panel.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Correlation and end-to-end timings, including async completion; never logs query/body/cookies. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE+20)
public class HttpRequestTimingFilter extends OncePerRequestFilter {
    private static final Logger log=LoggerFactory.getLogger(HttpRequestTimingFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String incoming=request.getHeader("X-Request-ID");
        String id=incoming!=null&&incoming.matches("[A-Za-z0-9-]{16,64}")?incoming:UUID.randomUUID().toString();
        response.setHeader("X-Request-ID",id);
        long started=System.nanoTime();var completed=new AtomicBoolean();
        Runnable finish=()->{
            if(!completed.compareAndSet(false,true))return;
            long elapsed=(System.nanoTime()-started)/1_000_000;
            if(elapsed>=1000||response.getStatus()>=500)
                log.warn("[HTTP] request_id={} method={} path={} status={} duration_ms={}",
                    id,request.getMethod(),request.getRequestURI(),response.getStatus(),elapsed);
        };
        String previous=MDC.get("requestId");MDC.put("requestId",id);
        try {chain.doFilter(request,response);}
        finally {
            if(previous==null)MDC.remove("requestId");else MDC.put("requestId",previous);
            if(request.isAsyncStarted())try{
                request.getAsyncContext().addListener(new AsyncListener(){
                    public void onComplete(AsyncEvent e){finish.run();}
                    public void onError(AsyncEvent e){finish.run();}
                    public void onTimeout(AsyncEvent e){finish.run();}
                    public void onStartAsync(AsyncEvent e){e.getAsyncContext().addListener(this);}
                });
            }catch(IllegalStateException finished){finish.run();}
            else finish.run();
        }
    }
}
