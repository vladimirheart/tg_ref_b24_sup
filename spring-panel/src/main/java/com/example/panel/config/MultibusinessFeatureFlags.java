package com.example.panel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Rollout switches for task 01-278. They are intentionally disabled by
 * default. The ticket-write switch requires a verified single-business owner
 * or an explicitly selected GENERAL scope; it is not rollout approval by itself.
 */
@Component
@ConfigurationProperties(prefix = "multibusiness")
public class MultibusinessFeatureFlags {

    private Foundation foundation = new Foundation();
    private boolean ticketWriteRequiresBusiness;
    private boolean strictReadScope;
    private boolean sharedChannelResolution;
    private boolean generalChannelResolution;
    private boolean uiSelector;
    private boolean manualTicketCreation;

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

    public boolean isGeneralChannelResolution() {
        return generalChannelResolution;
    }

    public void setGeneralChannelResolution(boolean generalChannelResolution) {
        this.generalChannelResolution = generalChannelResolution;
    }

    public boolean isUiSelector() {
        return uiSelector;
    }

    public void setUiSelector(boolean uiSelector) {
        this.uiSelector = uiSelector;
    }

    public boolean isManualTicketCreation() {
        return manualTicketCreation;
    }

    public void setManualTicketCreation(boolean manualTicketCreation) {
        this.manualTicketCreation = manualTicketCreation;
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
