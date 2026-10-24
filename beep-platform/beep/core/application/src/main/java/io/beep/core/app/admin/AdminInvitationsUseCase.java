package io.beep.core.app.admin;

import io.beep.core.app.common.Paging;
import io.beep.core.app.port.InvitationRepository;
import io.beep.core.app.port.MailPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.invitation.Invitation;
import io.beep.core.domain.user.Email;
import java.time.Clock;
import java.util.UUID;

/** Bêta privée sur invitation : le staff invite une adresse, qui reçoit un e-mail et peut ensuite s'inscrire (30 jours). */
public class AdminInvitationsUseCase {
  private final InvitationRepository invitations;
  private final UserRepository users;
  private final MailPort mail;
  private final TransactionPort tx;
  private final Clock clock;

  public AdminInvitationsUseCase(InvitationRepository invitations, UserRepository users, MailPort mail, TransactionPort tx, Clock clock) {
    this.invitations = invitations;
    this.users = users;
    this.mail = mail;
    this.tx = tx;
    this.clock = clock;
  }

  public Invitation invite(String rawEmail, String locale, String staffId) {
    Email email = new Email(rawEmail);
    if (users.findByEmail(email).filter(u -> u.emailVerified()).isPresent()) throw new DomainException("already_registered");
    Invitation saved = tx.run(() -> invitations.upsert(Invitation.create(UUID.randomUUID(), email, staffId, clock.instant())));
    mail.sendInvitation(email, "en".equals(locale) ? "en" : "fr");
    return saved;
  }

  public InvitationRepository.Page list(int limit, String cursor) {
    return invitations.page(Paging.limit(limit), cursor);
  }
}
