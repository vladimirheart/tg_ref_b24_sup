package com.example.panel.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.FlushMode;
import org.springframework.session.SaveMode;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

@Configuration
@EnableJdbcHttpSession(
    maxInactiveIntervalInSeconds = 1800,
    flushMode = FlushMode.ON_SAVE,
    saveMode = SaveMode.ON_SET_ATTRIBUTE
)
public class SessionConfig {
}
