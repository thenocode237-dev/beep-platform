package io.beep.core.domain.workspace;

import java.util.Locale;

public enum WorkspaceStatus {
  ACTIVE,
  /** Suspendu par le staff : création impossible, liens et pages affichent une page d'information. */
  SUSPENDED;

  public String db() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static WorkspaceStatus fromDb(String v) {
    return valueOf(v.toUpperCase(Locale.ROOT));
  }
}
