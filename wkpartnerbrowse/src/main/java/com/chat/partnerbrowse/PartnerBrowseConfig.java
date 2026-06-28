package com.chat.partnerbrowse;

/**
 * Runtime switches for the fullscreen partner browser.
 * Real data is the default. Turn FALLBACK_MOCK_ON_ERROR on only for local UI testing when /v1/partners is not deployed.
 */
public final class PartnerBrowseConfig {
    private PartnerBrowseConfig() {}

    public static final boolean DEBUG_MOCK = false;
    public static final boolean FALLBACK_MOCK_ON_ERROR = false;
}
