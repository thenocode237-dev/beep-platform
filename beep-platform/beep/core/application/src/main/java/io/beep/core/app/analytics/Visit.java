package io.beep.core.app.analytics;

import java.time.Instant;

/**
 * Signaux d'une requête entrante, tels que reçus. Ils ne sont jamais stockés bruts : {@link ClickRecorder} en dérive
 * un événement pseudonymisé. `countable` = faux pour HEAD et les préchargements du navigateur.
 */
public record Visit(String ip, String userAgent, String referer, String acceptLanguage, String query, boolean countable, Instant at) {}
