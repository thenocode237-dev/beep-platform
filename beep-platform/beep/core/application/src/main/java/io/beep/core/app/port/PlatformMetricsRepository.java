package io.beep.core.app.port;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Indicateurs plateforme pour le back office (comptages, séries journalières, taille de la base). */
public interface PlatformMetricsRepository {
  UserCounts userCounts();

  List<DayCount> signupsPerDay(LocalDate from, LocalDate to);

  /** Inscriptions depuis {@code since} et, parmi elles, celles dont le premier lien est arrivé dans {@code window}. */
  Activation activation(Instant since, Duration window);

  List<DayCount> clicksPerDay(LocalDate from, LocalDate to);

  ResourceCounts resourceCounts();

  long databaseBytes();

  record UserCounts(long total, long active, long pending, long suspended) {}

  record DayCount(LocalDate day, long count) {}

  record Activation(long signups, long activated) {}

  record ResourceCounts(long links, long qrCodes, long bioPages) {}
}
