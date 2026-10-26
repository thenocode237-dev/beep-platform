package io.beep.bo.bootstrap;

import io.beep.bo.app.port.CoreGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test de compatibilité au démarrage : le back office ne doit jamais tourner contre un core plus ancien que le
 * contrat dont il dépend. Core injoignable = démarrage autorisé (mode dégradé) ; core trop ancien = démarrage refusé.
 */
@Component
@Profile("!migrate & !seed")
class CoreCompatibilityCheck implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(CoreCompatibilityCheck.class);

  /** Version minimale du contrat admin requise (majeure.mineure). */
  static final String REQUIRED = "0.9";

  private final CoreGatewayPort core;

  CoreCompatibilityCheck(CoreGatewayPort core) {
    this.core = core;
  }

  @Override
  public void run(ApplicationArguments args) {
    String actual;
    try {
      actual = core.contractVersion();
    } catch (CoreGatewayPort.CoreUnavailableException e) {
      log.warn("beep-core injoignable au démarrage : mode dégradé, compatibilité non vérifiée");
      return;
    }
    if (!compatible(REQUIRED, actual)) {
      throw new IllegalStateException("beep-core implémente le contrat admin " + actual + " ; ce back office exige au moins " + REQUIRED + ". Déployez d'abord le core.");
    }
    log.info("contrat admin du core : {} (requis : >= {})", actual, REQUIRED);
  }

  /** Même majeure, mineure >= requise. */
  static boolean compatible(String required, String actual) {
    int[] r = parse(required);
    int[] a = parse(actual);
    return a[0] == r[0] && a[1] >= r[1];
  }

  private static int[] parse(String v) {
    String[] p = v.split("\\.");
    return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1])};
  }
}
