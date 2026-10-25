package io.beep.core.app.privacy;
import io.beep.core.app.port.*;
import io.beep.core.app.trust.TrustUseCase;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.User;
import io.beep.core.domain.user.Totp;
import java.time.*;
import java.util.*;

/** Fresh email proof and TOTP (when enabled) are required before self-service erasure. */
public class PrivacyUseCase {
  private final PrivacyRepository data;
  private final TrustRepository audit;
  private final OneTimeTokenRepository tokens;
  private final TokenPort crypto;
  private final SecretCipherPort cipher;
  private final MailPort mail;
  private final RateLimiterPort limiter;
  private final TransactionPort tx;
  private final Clock clock;
  public PrivacyUseCase(PrivacyRepository data,TrustRepository audit,OneTimeTokenRepository tokens,TokenPort crypto,SecretCipherPort cipher,MailPort mail,RateLimiterPort limiter,TransactionPort tx,Clock clock) {
    this.data=data;this.audit=audit;this.tokens=tokens;this.crypto=crypto;this.cipher=cipher;this.mail=mail;this.limiter=limiter;this.tx=tx;this.clock=clock;
  }
  public Map<String,Object> export(UUID id,String actor) {
    return tx.run(() -> { var out=data.export(id); audit.audit(actor,"privacy.export",id.toString(),null);return out; });
  }
  public void challenge(User actor) {
    if (!limiter.tryAcquire("erase-mail:"+actor.id(),3,Duration.ofHours(1))) throw new DomainException("rate_limited");
    String token=crypto.newToken();
    tokens.insert(actor.id(),OneTimeTokenRepository.Purpose.ERASE_ACCOUNT,crypto.hash(token),clock.instant().plus(Duration.ofMinutes(15)));
    mail.sendAccountDeletion(actor.email(),actor.locale(),token);
  }
  public void eraseSelf(User actor,String token,String totp) {
    if (!limiter.tryAcquire("erase:"+actor.id(),10,Duration.ofMinutes(10))) throw new DomainException("rate_limited");
    if (token == null || token.length()>200 || tokens.find(OneTimeTokenRepository.Purpose.ERASE_ACCOUNT,crypto.hash(token),clock.instant()).filter(actor.id()::equals).isEmpty()) throw new DomainException("invalid_token");
    if (actor.totpEnabled() && !Totp.verify(cipher.decrypt(actor.totpSecretCipher()),totp,clock.instant())) throw new DomainException("invalid_totp");
    tx.run(() -> {
      if (tokens.consume(OneTimeTokenRepository.Purpose.ERASE_ACCOUNT,crypto.hash(token),clock.instant()).filter(actor.id()::equals).isEmpty()) throw new DomainException("invalid_token");
      data.erase(actor.id());audit.audit(actor.id().toString(),"privacy.erase",actor.id().toString(),"self-service");
    });
  }
  public void eraseStaff(UUID id,String reason,String staff) {
    TrustUseCase.requireReason(reason);
    tx.run(() -> { data.erase(id);audit.audit(staff,"privacy.erase",id.toString(),reason.strip()); });
  }
}
