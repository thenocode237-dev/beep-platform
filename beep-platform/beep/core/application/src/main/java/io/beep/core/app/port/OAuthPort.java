package io.beep.core.app.port;

import java.util.Locale;

/** Fournisseurs d'identité OAuth 2.0 (code d'autorisation + PKCE). Un fournisseur sans identifiants configurés est désactivé. */
public interface OAuthPort {
  enum Provider {
    GOOGLE,
    GITHUB;

    public String id() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static Provider parse(String raw) {
      for (Provider p : values()) if (p.id().equals(raw)) return p;
      return null;
    }
  }

  boolean enabled(Provider provider);

  String authorizationUrl(Provider provider, String state, String codeChallenge, String redirectUri);

  /** Échange le code contre l'identité ; lève {@link OAuthFailedException} si le fournisseur refuse ou répond mal. */
  Identity exchange(Provider provider, String code, String codeVerifier, String redirectUri);

  record Identity(String email, boolean emailVerified, String subject) {}

  class OAuthFailedException extends RuntimeException {
    public OAuthFailedException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
