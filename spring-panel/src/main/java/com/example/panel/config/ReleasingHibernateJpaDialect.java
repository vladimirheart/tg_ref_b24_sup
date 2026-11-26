package com.example.panel.config;

import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.TransactionDefinition;

/** Preserve Spring's isolation/read-only cleanup, then return a request-bound EM's JDBC lease. */
public class ReleasingHibernateJpaDialect extends HibernateJpaDialect {
    private record Lease(SessionImplementor session,Object delegate) {}
    @Override public Object beginTransaction(EntityManager entityManager,TransactionDefinition definition) throws SQLException {
        Object delegate=super.beginTransaction(entityManager,definition);
        return new Lease(entityManager.unwrap(SessionImplementor.class),delegate);
    }
    @Override public void cleanupTransaction(Object transactionData) {
        if(!(transactionData instanceof Lease lease)){super.cleanupTransaction(transactionData);return;}
        try {super.cleanupTransaction(lease.delegate());}
        finally {
            // Managed logical connections acquire again if later lazy rendering needs JDBC.
            // Never disconnect before Spring resets isolation/read-only and completes the transaction.
            var logical=lease.session().getJdbcCoordinator().getLogicalConnection();
            if(logical.isPhysicallyConnected())logical.manualDisconnect();
        }
    }
}
