package io.beep.bo.app.port;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Brouillons de plans, propres au back office (base `backoffice`) : seule la publication atteint le core. */
public interface PlanDraftRepository {
  List<Draft> list();

  Optional<Draft> find(String code);

  /** Crée ou remplace le brouillon du code. */
  void save(Draft draft);

  void delete(String code);

  /** Mémorise la version publiée par le core (le brouillon reste, comme base de la prochaine édition). */
  void markPublished(String code, int version, Instant at);

  /**
   * @param baseVersion      version publiée sur laquelle le brouillon a été commencé (0 = nouveau plan) : détecte une publication concurrente
   * @param publishedVersion dernière version publiée depuis ce brouillon (null = jamais)
   */
  record Draft(String code, String name, long priceXaf, Long priceQuarterlyXaf, Long priceAnnualXaf, boolean isPublic, Map<String, Long> entitlements, int baseVersion,
      String note, String updatedBy, Instant updatedAt, Integer publishedVersion, Instant publishedAt) {
    public Draft {
      entitlements = Map.copyOf(entitlements);
    }
  }
}
