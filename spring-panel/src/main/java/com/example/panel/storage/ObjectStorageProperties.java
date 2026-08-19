package com.example.panel.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage.object")
public class ObjectStorageProperties {

    private String mode = "local_fs";
    private boolean requiredForPostgresql = true;
    private String bucket;
    private String region = "us-east-1";
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private boolean pathStyleAccess = true;
    private String keyPrefix = "iguana";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isRequiredForPostgresql() {
        return requiredForPostgresql;
    }

    public void setRequiredForPostgresql(boolean requiredForPostgresql) {
        this.requiredForPostgresql = requiredForPostgresql;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public boolean isS3Mode() {
        return mode != null && "s3".equalsIgnoreCase(mode.trim());
    }
}
