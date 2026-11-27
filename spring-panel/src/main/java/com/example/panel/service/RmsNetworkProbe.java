package com.example.panel.service;

import org.springframework.util.StringUtils;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Internal network boundary; no repositories, queues, scheduling or Spring lifecycle. */
final class RmsNetworkProbe {
    private final RmsTextCodec textCodec;

    RmsNetworkProbe(RmsTextCodec textCodec) { this.textCodec = textCodec; }

    String buildPingSuccessMessage(String host, String pingOutput) {
        String resolvedIp = extractPingResolvedAddress(pingOutput);
        if (StringUtils.hasText(resolvedIp)) {
            return "Ping успешен, IP: " + resolvedIp;
        }
        return "Ping успешен" + (StringUtils.hasText(host) ? ", host: " + host : "");
    }

    String extractPingResolvedAddress(String pingOutput) {
        if (!StringUtils.hasText(pingOutput)) {
            return null;
        }
        for (String rawLine : pingOutput.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }
            int openBracket = line.indexOf('[');
            int closeBracket = line.indexOf(']');
            if (openBracket >= 0 && closeBracket > openBracket) {
                String candidate = line.substring(openBracket + 1, closeBracket).trim();
                if (looksLikeIpAddress(candidate)) {
                    return candidate;
                }
            }
            String[] tokens = line.split("\\s+");
            for (String token : tokens) {
                String candidate = token.replace("(", "").replace(")", "").replace(",", "").trim();
                if (looksLikeIpAddress(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    boolean looksLikeIpAddress(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String candidate = value.trim();
        return candidate.matches("^(\\d{1,3}\\.){3}\\d{1,3}$")
            || candidate.matches("^[0-9a-fA-F:]+$");
    }

    CommandResult runCommand(List<String> command, long timeoutMs) throws Exception {
        Process process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .start();
        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Команда превысила лимит ожидания");
        }
        String output = textCodec.decodeProcessOutput(process.getInputStream().readAllBytes());
        return new CommandResult(process.exitValue() == 0, output);
    }

    List<String> buildPingCommand(String host) {
        if (textCodec.isWindows()) {
            return List.of("ping", "-n", "4", "-w", "2000", host);
        }
        return List.of("ping", "-c", "4", "-W", "2", host);
    }

    List<String> buildTracerouteCommand(String host) {
        if (textCodec.isWindows()) {
            return List.of("tracert", "-d", "-w", "2000", host);
        }
        return List.of("traceroute", "-n", "-w", "2", host);
    }

    String summarizeTraceroute(String report) {
        if (!StringUtils.hasText(report)) {
            return "Ping не прошёл, трассировка не вернула данные";
        }
        String[] lines = report.split("\\R");
        String lastHopLine = null;
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!line.matches("^\\d+\\s+.*")) {
                continue;
            }
            if (line.contains("* * *") || line.endsWith("*") || line.contains("Превышен интервал ожидания")) {
                if (lastHopLine != null) {
                    return "Маршрут обрывается после " + lastHopLine;
                }
                return "Нет ответов по трассировке";
            }
            lastHopLine = line;
        }
        return lastHopLine != null ? "Последний отвечающий хоп: " + lastHopLine : "Трассировка завершена без хопов";
    }

    record CommandResult(boolean success, String output) {
    }
}
