package io.beep.core.domain.analytics;

import java.util.Locale;

public enum DeviceType {
  MOBILE,
  TABLET,
  DESKTOP,
  BOT,
  OTHER;

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }
}
