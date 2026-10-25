package io.beep.core.adapter.in.http;

import io.beep.core.app.domains.TlsAllowUseCase;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Point `ask` de Caddy pour le TLS à la demande : 200 si un certificat peut être émis pour ce nom, 404 sinon.
 * Jamais exposé par Caddy (`/internal/*` répond 404 à l'extérieur) ; Caddy l'appelle directement sur le réseau Docker.
 */
@RestController
@RequestMapping("/internal/tls")
@Profile("!worker & !migrate")
public class TlsAllowController {
  private final TlsAllowUseCase allow;

  public TlsAllowController(TlsAllowUseCase allow) {
    this.allow = allow;
  }

  @GetMapping("/allow")
  ResponseEntity<Void> allow(@RequestParam(required = false) String domain) {
    return allow.allowed(domain) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
  }
}
