package io.beep.core.adapter.in.http;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contrôle de vivacité exposé sur le port applicatif (8080). La santé détaillée vit sur le port de gestion. */
@RestController
@Profile("!worker & !migrate")
public class HealthController {
  @GetMapping("/healthz")
  String healthz() {
    return "ok";
  }
}
