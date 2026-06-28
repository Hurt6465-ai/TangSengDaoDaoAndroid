package com.chat.partnerbrowse;

/**
 * Runtime switches for the fullscreen partner browser.
 * Keep fallback mock enabled during Android integration while the backend /v1/partners endpoint
 * is not deployed yet. Turn it off before production if you want API failures to be visible.
 */
public final class PartnerBrowseConfig {
    private PartnerBrowseConfig() {}

    public static final boolean DEBUG_MOCK = false;
    public static final boolean FALLBACK_MOCK_ON_ERROR = true;
}
