package io.beep.core.app.port;

import io.beep.core.domain.invitation.Invitation;
import io.beep.core.domain.user.Email;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Invitations à la bêta privée (une par adresse ; une nouvelle invitation renouvelle celle qui n'a pas servi). */
public interface InvitationRepository {
  Invitation upsert(Invitation invitation);

  Optional<Invitation> findByEmail(Email email);

  /** Consomme atomiquement l'invitation de cette adresse si elle est utilisable. */
  boolean consume(Email email, Instant now);

  Page page(int limit, String cursor);

  record Page(List<Invitation> items, String nextCursor) {}
}
