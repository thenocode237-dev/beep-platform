package io.beep.core.app.port;

import java.util.UUID;

/**
 * Chemin chaud : à quel espace de noms appartient la requête (domaine de Beepclick ou domaine personnalisé) ? Mis en cache en mémoire
 * et invalidé par LISTEN/NOTIFY, comme la résolution des slugs.
 */
public interface DomainRouterPort {
  Route route(String host);

  /**
   * @param kind        MAIN : domaine de Beepclick ; CUSTOM : domaine personnalisé enregistré ; UNKNOWN : hôte inconnu
   * @param servable    CUSTOM : DNS vérifié, non bloqué, workspace actif
   * @param blocked     CUSTOM : bloqué par le staff ou workspace suspendu (page « indisponible » plutôt que 404)
   */
  record Route(Kind kind, UUID domainId, boolean servable, boolean blocked, String rootRedirectUrl, String notFoundUrl) {
    public static final Route MAIN = new Route(Kind.MAIN, null, true, false, null, null);
    public static final Route UNKNOWN = new Route(Kind.UNKNOWN, null, false, false, null, null);

    public enum Kind {
      MAIN,
      CUSTOM,
      UNKNOWN
    }
  }
}
