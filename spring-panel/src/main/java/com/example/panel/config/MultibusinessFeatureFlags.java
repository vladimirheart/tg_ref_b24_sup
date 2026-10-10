package com.example.panel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Rollout switches for task 01-278. They are intentionally disabled by
 * default and are not read by existing runtime paths until their individual
 * acceptance gates are complete.
 */
@Component
@ConfigurationProperties(prefix = "multibusiness")
public class MultibusinessFeatureFlags {

    private Foundation foundation = new Foundation();
    private boolean ticketWriteRequiresBusiness;
    private boolean strictReadScope;
    private boolean sharedChannelResolution;
    private boolean uiSelector;

    public Foundation getFoundation() {
        return foundation;
    }

    public void setFoundation(Foundation foundation) {
        this.foundation = foundation != null ? foundation : new Foundation();
    }

    public boolean isFoundationEnabled() {
        return foundation.isEnabled();
    }

    public boolean isTicketWriteRequiresBusiness() {
        return ticketWriteRequiresBusiness;
    }

    public void setTicketWriteRequiresBusiness(boolean ticketWriteRequiresBusiness) {
        this.ticketWriteRequiresBusiness = ticketWriteRequiresBusiness;
    }

    public boolean isStrictReadScope() {
        return strictReadScope;
    }

    public void setStrictReadScope(boolean strictReadScope) {
        this.strictReadScope = strictReadScope;
    }

    public boolean isSharedChannelResolution() {
        return sharedChannelResolution;
    }

    public void setSharedChannelResolution(boolean sharedChannelResolution) {
        this.sharedChannelResolution = sharedChannelResolution;
    }

    public boolean isUiSelector() {
        return uiSelector;
    }

    public void setUiSelector(boolean uiSelector) {
        this.uiSelector = uiSelector;
    }

    public static class Foundation {

        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
