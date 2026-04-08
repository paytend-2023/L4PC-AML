package com.paytend.l4pc.aml.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.google-sso")
public class GoogleSsoProperties {

    private boolean enabled;
    private String allowedDomain = "paytend.com";
    private String portalBaseUri = "";
    private String defaultSuccessPath = "/aml";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAllowedDomain() {
        return allowedDomain;
    }

    public void setAllowedDomain(String allowedDomain) {
        this.allowedDomain = allowedDomain;
    }

    public String getPortalBaseUri() {
        return portalBaseUri;
    }

    public void setPortalBaseUri(String portalBaseUri) {
        this.portalBaseUri = portalBaseUri;
    }

    public String getDefaultSuccessPath() {
        return defaultSuccessPath;
    }

    public void setDefaultSuccessPath(String defaultSuccessPath) {
        this.defaultSuccessPath = defaultSuccessPath;
    }
}
