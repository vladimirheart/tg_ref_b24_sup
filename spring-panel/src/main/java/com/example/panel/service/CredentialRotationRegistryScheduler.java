package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CredentialRotationRegistryScheduler {

    private static final Logger log = LoggerFactory.getLogger(CredentialRotationRegistryScheduler.class);

    private final CredentialRotationRegistryService registryService;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public CredentialRotationRegistryScheduler(CredentialRotationRegistryService registryService,
                                               RuntimeCoordinationService runtimeCoordinationService) {
        this.registryService = registryService;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(
        initialDelayString = "${panel.credential-rotation.initial-delay-ms:30000}",
        fixedDelayString = "${panel.credential-rotation.refresh-interval-ms:900000}"
    )
    public void refreshRegistry() {
        runtimeCoordinationService.runWithLease("credential-rotation-registry", Duration.ofMinutes(20), () -> {
            try {
                CredentialRotationRegistryService.RegistrySnapshot snapshot = registryService.refreshAll();
                log.debug("Credential rotation registry refresh complete: entries={}", snapshot.items().size());
            } catch (Exception ex) {
                log.warn("Credential rotation registry refresh failed", ex);
            }
        });
    }
}
