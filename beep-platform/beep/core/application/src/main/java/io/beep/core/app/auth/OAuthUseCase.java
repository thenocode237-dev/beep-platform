package io.beep.core.app.auth;

import io.beep.core.app.common.PublicSite;
import io.beep.core.app.port.OAuthPort;
import io.beep.core.app.port.OAuthPort.Provider;
import io.beep.core.app.port.TokenPort;
import io.beep.core.app.port.TransactionPort;
import io.beep.core.app.port.UserRepository;
import io.beep.core.domain.DomainException;
import io.beep.core.domain.user.Email;
import io.beep.core.domain.user.User;
import io.beep.core.domain.user.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Connexion OAuth Google / GitHub (code d'autorisation + PKCE, état anti-CSRF).
 * Seule une adresse vérifiée par le fournisseur est acceptée ; un compte existant à cette adresse est rattaché.
 * Le TOTP éventuel reste exigé.
 */
public class OAuthUseCase {
  /** À conserver côté navigateur (cookie HttpOnly, 10 min) jusqu'au retour du fournisseur. */
  public record Start(String authorizationUrl, String state, String codeVerifier) {}

  private final OAuthPort oauth;
  private final UserRepository users;
  private final TokenPort tokenPort;
  private final TransactionPort tx;
  private final SessionIssuer issuer;
  private final PublicSite site;
  private final Clock clock;
  private final AuthSettings settings;

  public OAuthUseCase(OAuthPort oauth, UserRepository users, TokenPort tokenPort, TransactionPort tx, SessionIssuer issuer,
      PublicSite site, Clock clock, AuthSettings settings) {
    this.oauth = oauth;
    this.users = users;
    this.tokenPort = tokenPort;
    this.tx = tx;
    this.issuer = issuer;
    this.site = site;
    this.clock = clock;
    this.settings = settings;
  }

  public List<Provider> providers() {
    return java.util.Arrays.stream(Provider.values()).filter(oauth::enabled).toList();
  }

  public Start start(Provider provider) {
    if (provider == null || !oauth.enabled(provider)) throw new DomainException("not_found");
    String state = tokenPort.newToken();
    String verifier = tokenPort.newToken(); // 43 caractères base64url : longueur PKCE valide
    return new Start(oauth.authorizationUrl(provider, state, challenge(verifier), redirectUri(provider)), state, verifier);
  }

  public AuthOutcome complete(Provider provider, String code, String codeVerifier, String locale) {
    if (provider == null || !oauth.enabled(provider) || code == null || code.isBlank() || codeVerifier == null) throw new DomainException("oauth_failed");
    OAuthPort.Identity identity;
    try {
      identity = oauth.exchange(provider, code, codeVerifier, redirectUri(provider));
    } catch (OAuthPort.OAuthFailedException e) {
      throw new DomainException("oauth_failed");
    }
    if (identity.email() == null || !identity.emailVerified()) throw new DomainException("oauth_email_unverified");
    Email email = new Email(identity.email());
    Instant now = clock.instant();

    Optional<User> found = users.findByEmail(email);
    User user;
    if (found.isPresent()) {
      user = found.get();
      if (user.status() == UserStatus.SUSPENDED) throw new DomainException("invalid_credentials");
      if (!user.emailVerified()) {
        user = user.verifyEmail(now);
        users.update(user);
      }
    } else {
      User created = User.registerExternal(UUID.randomUUID(), email, Locales.normalize(locale), now).verifyEmail(now);
      try {
        tx.run(() -> users.insert(created));
        user = created;
      } catch (UserRepository.DuplicateEmailException race) {
        user = users.findByEmail(email).orElseThrow(() -> new DomainException("oauth_failed"));
      }
    }
    return issuer.afterFirstFactor(user);
  }

  public String redirectUri(Provider provider) {
    return site.url() + "/v1/auth/oauth/" + provider.id() + "/callback";
  }

  static String challenge(String verifier) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
