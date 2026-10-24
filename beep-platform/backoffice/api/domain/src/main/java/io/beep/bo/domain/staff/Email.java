package io.beep.bo.domain.staff;

import io.beep.bo.domain.DomainException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Adresse e-mail professionnelle normalisée (trim + minuscules). */
public record Email(String value) {
  private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

  public Email {
    if (value == null) throw new DomainException("invalid_email");
    value = value.trim().toLowerCase(Locale.ROOT);
    if (value.length() > 254 || !SHAPE.matcher(value).matches()) throw new DomainException("invalid_email");
  }

  @Override
  public String toString() {
    return value;
  }
}
