package io.beep.core.app.port;

/** Vérification DNS d'un domaine personnalisé : propriété (TXT) puis routage (CNAME ou A vers Beepclick). */
public interface DnsVerifierPort {
  /**
   * @param ownership   un TXT `_beep-verify.<hôte>` contient le jeton
   * @param routing     l'hôte pointe vers Beepclick (CNAME vers la cible, ou adresse A attendue) ; vrai si aucune cible n'est configurée (développement)
   * @param unreachable la résolution DNS elle-même a échoué (réseau, SERVFAIL) : ni échec ni succès
   */
  Result check(String hostname, String token);

  record Result(boolean ownership, boolean routing, boolean unreachable) {
    public boolean ok() {
      return ownership && routing;
    }

    /** Code d'erreur affiché au client et au staff. */
    public String error() {
      if (ok()) return null;
      if (unreachable) return "dns_unreachable";
      return !ownership ? "txt_missing" : "routing_missing";
    }
  }
}
