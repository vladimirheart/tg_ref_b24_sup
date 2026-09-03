package com.example.panel.service;

import java.util.List;

public final class BackendOpsCommandTypes {

    public static final String RMS_LICENSE_REFRESH = "rms.license.refresh";
    public static final String RMS_NETWORK_REFRESH = "rms.network.refresh";
    public static final String IIKO_API_REFRESH = "iiko.api.refresh";
    public static final String IIKO_LOCATIONS_SYNC = "iiko.locations.sync";
    public static final String NETBOX_PASSPORTS_SYNC = "netbox.passports.sync";

    private static final List<ExecutionLane> EXECUTION_LANES = List.of(
        new ExecutionLane(
            "rms-monitoring",
            List.of(RMS_LICENSE_REFRESH, RMS_NETWORK_REFRESH)
        ),
        new ExecutionLane(
            "iiko-api",
            List.of(IIKO_API_REFRESH)
        ),
        new ExecutionLane(
            "iiko-locations",
            List.of(IIKO_LOCATIONS_SYNC)
        ),
        new ExecutionLane(
            "netbox-passports",
            List.of(NETBOX_PASSPORTS_SYNC)
        )
    );

    private BackendOpsCommandTypes() {
    }

    public static List<ExecutionLane> executionLanes() {
        return EXECUTION_LANES;
    }

    public static ExecutionLane executionLane(String commandType) {
        String normalized = commandType == null ? "" : commandType.trim();
        for (ExecutionLane lane : EXECUTION_LANES) {
            if (lane.commandTypes().contains(normalized)) {
                return lane;
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Backend ops command type is required.");
        }
        return new ExecutionLane(
            "command:" + normalized,
            List.of(normalized)
        );
    }

    public record ExecutionLane(String key,
                                List<String> commandTypes) {

        public ExecutionLane {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Execution lane key is required.");
            }
            key = key.trim();
            commandTypes = List.copyOf(commandTypes);
            if (commandTypes.isEmpty()) {
                throw new IllegalArgumentException(
                    "Execution lane requires at least one command type."
                );
            }
        }
    }
}
