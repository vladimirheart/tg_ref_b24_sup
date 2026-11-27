package com.example.supportbot.max;

import com.example.supportbot.service.InboundMediaException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class MaxInboundMediaErrorAdvice {
    @ExceptionHandler(InboundMediaException.class)
    public ResponseEntity<Map<String,Object>> media(InboundMediaException failure) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Receive failed: source={} code={} reason={}",failure.source(),failure.failure().code(),failure.getMessage());
        return ResponseEntity.status(503).header("X-Error-Source",failure.source()).body(failure.envelope());
    }
}
