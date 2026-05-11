package chit.tefca.directory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "tefca.directory")
public class DirectoryProperties {

    private long syncIntervalMs = 3600000;
    private int cacheTtlMinutes = 30;
    private String sourceUrl;
    private int syncTimeoutSeconds = 120;
    private boolean syncEnabled = true;

    public long getSyncIntervalMs() {
        return syncIntervalMs;
    }

    public void setSyncIntervalMs(long syncIntervalMs) {
        this.syncIntervalMs = syncIntervalMs;
    }

    public int getCacheTtlMinutes() {
        return cacheTtlMinutes;
    }

    public void setCacheTtlMinutes(int cacheTtlMinutes) {
        this.cacheTtlMinutes = cacheTtlMinutes;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public int getSyncTimeoutSeconds() {
        return syncTimeoutSeconds;
    }

    public void setSyncTimeoutSeconds(int syncTimeoutSeconds) {
        this.syncTimeoutSeconds = syncTimeoutSeconds;
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }
}
