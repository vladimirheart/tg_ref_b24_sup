package com.example.panel.service;

public final class BotRuntimeLifecycleProbeApp {

    private BotRuntimeLifecycleProbeApp() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Bootstrapping lifecycle probe");
        Thread.sleep(120);
        System.out.println("Started BotRuntimeLifecycleProbeApplication in 0.245 seconds");
        System.out.flush();
        Thread.sleep(30_000L);
    }
}
