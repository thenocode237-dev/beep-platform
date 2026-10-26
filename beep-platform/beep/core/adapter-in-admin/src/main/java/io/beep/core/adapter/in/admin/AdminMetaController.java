package io.beep.core.adapter.in.admin;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Version du contrat admin implémentée par ce core. Le back office la vérifie au démarrage
 * (il ne doit jamais tourner contre un core plus ancien que le contrat dont il dépend).
 */
@RestController
@RequestMapping("/internal/admin/v1")
@Profile("!worker & !migrate")
public class AdminMetaController {
  /** Doit rester alignée sur `info.version` de contracts/beep-admin.openapi.yaml. */
  public static final String CONTRACT_VERSION = "0.9.0";

  record Meta(String contract) {}

  @GetMapping("/meta")
  Meta meta() {
    return new Meta(CONTRACT_VERSION);
  }
}
