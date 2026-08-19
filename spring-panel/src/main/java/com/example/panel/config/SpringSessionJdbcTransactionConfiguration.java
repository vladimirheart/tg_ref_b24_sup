package com.example.panel.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.session.jdbc.config.annotation.SpringSessionTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * Keeps Spring Session JDBC transactions independent from JPA/OpenEntityManagerInView.
 *
 * <p>Spring Session performs its repository work in REQUIRES_NEW transactions and,
 * without an explicit qualifier, resolves the primary transaction manager. Iguana's
 * primary transaction manager is JpaTransactionManager. For long-lived SSE requests
 * this can leave the JDBC connection associated with the request-bound EntityManager
 * until the async response completes, eventually exhausting the Hikari pool.</p>
 *
 * <p>Spring Session JDBC should use a transaction manager that directly owns the same
 * DataSource used by the session JdbcTemplate. The dedicated DataSource transaction
 * manager commits and releases the connection as soon as each session operation ends,
 * while application JPA transactions continue to use the primary JpaTransactionManager.</p>
 */
@Configuration
public class SpringSessionJdbcTransactionConfiguration {

    @Bean(name = "springSessionJdbcTransactionManager")
    @SpringSessionTransactionManager
    public PlatformTransactionManager springSessionJdbcTransactionManager(
            @Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
