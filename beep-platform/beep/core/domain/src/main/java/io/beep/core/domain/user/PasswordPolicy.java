package io.beep.core.domain.user;

import io.beep.core.domain.DomainException;

/** Règle de robustesse : la longueur d'abord (NIST 800-63B), sans règles de composition arbitraires. */
public final class PasswordPolicy {
  public static final int MIN_LENGTH = 12;
  public static final int MAX_LENGTH = 128; // borne le coût du hachage

  private PasswordPolicy() {}

  public static void check(String raw) {
    if (raw == null || raw.length() < MIN_LENGTH || raw.length() > MAX_LENGTH) throw new DomainException("weak_password");
  }
}
