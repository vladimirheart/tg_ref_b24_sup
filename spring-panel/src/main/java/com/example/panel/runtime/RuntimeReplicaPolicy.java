package com.example.panel.runtime;

public enum RuntimeReplicaPolicy {
    PROCESS_LOCAL,
    SINGLETON,
    LEASED,
    DATABASE_CLAIMED,
    BROKER_COMPETING_CONSUMER
}
