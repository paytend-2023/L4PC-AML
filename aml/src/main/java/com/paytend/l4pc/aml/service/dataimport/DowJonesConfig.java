package com.paytend.l4pc.aml.service.dataimport;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "aml.dowjones")
public class DowJonesConfig {

    private boolean enabled;
    private String baseUrl = "https://djrcfeed.dowjones.com/XML/";
    private String credentials;
    private String incrementDir = "/dowjones/increment/";
    private String fullDir = "/dowjones/full/";
    private List<String> incrementPaths = List.of();
    private List<String> fullPaths = List.of();
    private String downloadCron = "0 0 18 * * ?";
    private String incrementSyncCron = "0 0 20 * * ?";
    private String fullSyncCron = "0 0 0 ? * SUN";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getCredentials() { return credentials; }
    public void setCredentials(String credentials) { this.credentials = credentials; }
    public String getIncrementDir() { return incrementDir; }
    public void setIncrementDir(String incrementDir) { this.incrementDir = incrementDir; }
    public String getFullDir() { return fullDir; }
    public void setFullDir(String fullDir) { this.fullDir = fullDir; }
    public List<String> getIncrementPaths() { return incrementPaths; }
    public void setIncrementPaths(List<String> incrementPaths) { this.incrementPaths = incrementPaths; }
    public List<String> getFullPaths() { return fullPaths; }
    public void setFullPaths(List<String> fullPaths) { this.fullPaths = fullPaths; }
    public String getDownloadCron() { return downloadCron; }
    public void setDownloadCron(String downloadCron) { this.downloadCron = downloadCron; }
    public String getIncrementSyncCron() { return incrementSyncCron; }
    public void setIncrementSyncCron(String incrementSyncCron) { this.incrementSyncCron = incrementSyncCron; }
    public String getFullSyncCron() { return fullSyncCron; }
    public void setFullSyncCron(String fullSyncCron) { this.fullSyncCron = fullSyncCron; }
}
