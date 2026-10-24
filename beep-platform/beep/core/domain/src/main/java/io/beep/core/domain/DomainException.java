package io.beep.core.domain;

/** Erreur métier. Le code est stable (contrat d'API) ; la traduction HTTP est faite par les adapters. */
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
