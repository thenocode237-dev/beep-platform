package io.beep.core.app.auth;

import io.beep.core.app.port.OneTimeTokenRepository;
import io.beep.core.app.port.OneTimeTokenRepository.Purpose;
import io.beep.core.app.port.TokenPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.DomainException;
import java.time.Clock;
import java.util.UUID;

public class VerifyEmailUseCase {
  private final UserRepository users;
  private final OneTimeTokenRepository tokens;
  private final TokenPort tokenPort;
  private final TransactionPort tx;
  private final Clock clock;

  public VerifyEmailUseCase(UserRepository users, OneTimeTokenRepository tokens, TokenPort tokenPort, TransactionPort tx, Clock clock) {
    this.users = users;
    this.tokens = tokens;
    this.tokenPort = tokenPort;
    this.tx = tx;
    this.clock = clock;
  }

  public void execute(String token) {
    if (token == null || token.isBlank()) throw new DomainException("invalid_token");
    tx.run(() -> {
      UUID userId = tokens.consume(Purpose.VERIFY_EMAIL, tokenPort.hash(token), clock.instant()).orElseThrow(() -> new DomainException("invalid_token"));
      users.findById(userId).ifPresent(u -> users.update(u.verifyEmail(clock.instant())));
    });
  }
}
