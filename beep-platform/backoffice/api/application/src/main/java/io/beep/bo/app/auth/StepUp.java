package io.beep.bo.app.auth;

import io.beep.bo.app.port.RateLimiterPort;
import io.beep.bo.app.port.SecretCipherPort;
import io.beep.bo.domain.DomainException;
import io.beep.bo.domain.staff.StaffUser;
import io.beep.bo.domain.staff.Totp;
import java.time.Clock;

/**
 * Ré-authentification avant une action sensible (specs §8.1) : code TOTP courant du membre du staff, 5 essais / 15 min.
 * Une session volée ne suffit donc pas à suspendre un compte ou à modifier des droits.
 */
public class StepUp {
  private final SecretCipherPort cipher;
  private final RateLimiterPort limiter;
  private final Clock clock;
  private final StaffSettings settings;

  public StepUp(SecretCipherPort cipher, RateLimiterPort limiter, Clock clock, StaffSettings settings) {
    this.cipher = cipher;
    this.limiter = limiter;
    this.clock = clock;
    this.settings = settings;
  }

  public void require(StaffUser actor, String totp) {
    String key = "step-up:" + actor.id();
    if (!limiter.tryAcquire(key, settings.loginMaxAttempts(), settings.loginWindow())) throw new DomainException("rate_limited");
    if (totp == null || !Totp.verify(cipher.decrypt(actor.totpSecretCipher()), totp.trim(), clock.instant())) throw new DomainException("reauth_required");
    limiter.reset(key);
  }
}
