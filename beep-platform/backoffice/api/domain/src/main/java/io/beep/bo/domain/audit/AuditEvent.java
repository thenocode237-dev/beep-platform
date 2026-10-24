package io.beep.bo.domain.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Action staff à journaliser : qui, quoi, sur quoi, quand, depuis où.
 * `detail` décrit l'avant/après sous forme textuelle (identifiants, jamais de secrets ni de contenu personnel inutile).
 */
public record AuditEvent(Instant at, UUID actorId, String actorEmail, String action, String target, String detail, String ip) {}
