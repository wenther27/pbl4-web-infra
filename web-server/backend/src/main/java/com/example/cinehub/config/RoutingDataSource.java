package com.example.cinehub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transaction readOnly = true -> REPLICA, con lai -> PRIMARY.
 * Dat logging.level.com.example.cinehub.config=DEBUG de xem tung ket noi di dau.
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

    private static final Logger log = LoggerFactory.getLogger(RoutingDataSource.class);

    @Override
    protected Object determineCurrentLookupKey() {
        String target = TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? "REPLICA" : "PRIMARY";
        log.debug("RoutingDataSource -> {}", target);
        return target;
    }
}
