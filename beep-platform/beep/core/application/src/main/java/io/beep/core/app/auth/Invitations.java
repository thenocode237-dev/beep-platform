package io.beep.core.app.auth;

import io.beep.core.app.port.InvitationRepository;
import io.beep.core.domain.user.Email;
import java.time.Instant;

final class Invitations {
  private Invitations() {}

  static boolean usable(InvitationRepository invitations, Email email, Instant now) {
    return invitations.findByEmail(email).filter(i -> i.usable(now)).isPresent();
  }
}
