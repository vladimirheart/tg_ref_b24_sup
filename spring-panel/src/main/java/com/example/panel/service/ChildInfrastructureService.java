package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChildInfrastructureRepository;
import com.example.panel.security.ChildBotCredentials;
import com.example.panel.security.ChildBotCredentials.Principal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class ChildInfrastructureService {
    private final ChildInfrastructureRepository repository;
    private final ChildRabbitProvisioner broker;
    private final Environment environment;

    public ChildInfrastructureService(ChildInfrastructureRepository repository, ChildRabbitProvisioner broker, Environment environment) {
        this.repository = repository;
        this.broker = broker;
        this.environment = environment;
    }

    public Result prepare(Channel channel) {
        if (!"rabbitmq".equalsIgnoreCase(environment.getProperty("app.integration.transport.mode", "direct")))
            return new Result("not_required", "Подготовка отдельных доступов не требуется.");
        if (channel == null || channel.getId() == null || !Boolean.TRUE.equals(channel.getActive()))
            return new Result("failed", "Для подготовки нужен сохранённый активный канал.");
        String stage = "PostgreSQL";
        try {
            Principal child = new Principal(channel.getId(), channel.getPlatform());
            String master = environment.getProperty("app.bots.internal-api.token", "iguana-internal-bot-token");
            String signing = environment.getProperty("app.bots.internal-api.signature-secret");
            String sqlPassword = ChildBotCredentials.databasePassword(master, signing, child);
            String rabbitPassword = ChildBotCredentials.rabbitPassword(master, signing, child);
            var state = repository.prepare(child, sqlPassword, rabbitHash(rabbitPassword));
            if (!state.managed()) return new Result("existing", "Используются ранее подготовленные доступы.");
            stage = "RabbitMQ";
            repository.complete(child, hash -> broker.provision(child, hash));
            return new Result("ready", "Доступы и очереди бота подготовлены.");
        } catch (RuntimeException ex) {
            // JDBC/HTTP exceptions can contain passwords, statements or broker responses.
            // Neither the browser nor logs receive the exception text or cause.
            return new Result("failed", "Не удалось подготовить " + stage
                + ". Проверьте доступность сервиса, настройки подготовки и конфликты учётной записи. Повторите «Старт».");
        }
    }

    public void requireReady(Channel channel) {
        Result result = prepare(channel);
        if (!result.ready()) throw new IllegalStateException(result.message());
    }

    static String rabbitHash(String password) {
        try {
            byte[] salt = new byte[4];
            new SecureRandom().nextBytes(salt);
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[36];
            System.arraycopy(salt, 0, result, 0, 4);
            System.arraycopy(hashed, 0, result, 4, 32);
            return Base64.getEncoder().encodeToString(result);
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot hash child broker password");
        }
    }

    public record Result(String state, String message) {
        public boolean ready() { return !"failed".equals(state); }
    }
}
