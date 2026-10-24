package io.beep.bo.domain;

/** Erreur métier : code stable (contrat d'API), traduit en HTTP par les adapters. */
public class DomainException extends RuntimeException {
  private final String code;

  public DomainException(String code) {
    super(code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
