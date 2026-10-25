package io.beep.core.domain.customdomain;

import java.util.Locale;

public enum DnsStatus {
  PENDING,
  VERIFIED,
  FAILED;

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static DnsStatus parse(String raw) {
    return valueOf(raw.toUpperCase(Locale.ROOT));
  }
}
