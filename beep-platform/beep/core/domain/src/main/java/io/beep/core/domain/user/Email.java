package io.beep.core.domain.user;

import io.beep.core.domain.DomainException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Adresse e-mail normalisée (trim + minuscules). */
public record Email(String value) {
  private static final Pattern SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");
  private static final int MAX_LENGTH = 254;

  public Email {
    if (value == null) throw new DomainException("invalid_email");
    value = value.trim().toLowerCase(Locale.ROOT);
    if (value.length() > MAX_LENGTH || !SHAPE.matcher(value).matches()) throw new DomainException("invalid_email");
  }

  @Override
  public String toString() {
    return value;
  }
}
