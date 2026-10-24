package io.beep.core.domain.analytics;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement de trafic (clic, scan, vue de page bio, clic de bloc). Pseudonymisé dès sa création :
 * l'IP n'est jamais conservée, seule une empreinte salée quotidienne (`visitorHash`) permet de compter les visiteurs uniques du jour.
 */
public record ClickEvent(
    UUID id,
    Instant ts,
    UUID workspaceId,
    EventKind kind,
    UUID linkId,
    UUID qrId,
    UUID bioPageId,
    String bioBlockId,
    String visitorHash,
    String country,
    DeviceType device,
    String os,
    String browser,
    String lang,
    String referrer,
    boolean bot) {}
