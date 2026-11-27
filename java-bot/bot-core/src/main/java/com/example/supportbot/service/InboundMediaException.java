package com.example.supportbot.service;

/** Keeps the original cause for retry/diagnosis while exposing an allowlisted human message. */
public final class InboundMediaException extends IllegalStateException {
    private final String source;
    private final InboundMediaFailure failure;
    public InboundMediaException(String source, Throwable cause) {
        super(InboundMediaFailure.describe(cause).message(), cause);
        this.source = switch (String.valueOf(source).toLowerCase(java.util.Locale.ROOT)) {
            case "telegram" -> "telegram"; case "vk" -> "vk"; case "max" -> "max"; default -> "bot";
        };
        this.failure = InboundMediaFailure.describe(cause);
    }
    public String source() { return source; }
    public InboundMediaFailure failure() { return failure; }
    public java.util.Map<String,Object> envelope() {
        return java.util.Map.of("ok", false, "success", false, "error", getMessage(), "errorCode", failure.code(),
            "errorSource", source, "operation", "receive");
    }
}
