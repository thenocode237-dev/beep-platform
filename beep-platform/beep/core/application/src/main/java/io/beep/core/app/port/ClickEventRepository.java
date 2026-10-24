package io.beep.core.app.port;

import io.beep.core.domain.analytics.ClickEvent;
import java.util.List;

/** Stockage brut des événements (table partitionnée par mois). Port prêt pour un EventStore TimescaleDB/ClickHouse. */
public interface ClickEventRepository {
  void insertBatch(List<ClickEvent> events);
}
