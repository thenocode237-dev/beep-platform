package io.beep.core.adapter.in.http;

import io.beep.core.app.redirect.DeeplinkSimulator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Simulateur de comportement des deeplinks (espace client) : que verrait un visiteur selon son appareil ? */
@RestController
@RequestMapping("/v1/deeplinks")
@Profile("!worker & !migrate")
public class DeeplinksController {
  record SimulateRequest(String url, String profile) {}

  record Profiles(List<String> items) {}

  private final DeeplinkSimulator simulator;
  private final SessionCookies cookies;
  private final io.beep.core.app.trust.TrustUseCase trust;

  public DeeplinksController(DeeplinkSimulator simulator, SessionCookies cookies, io.beep.core.app.trust.TrustUseCase trust) {
    this.trust = trust;
    this.simulator = simulator;
    this.cookies = cookies;
  }

  @GetMapping("/profiles")
  Profiles profiles(HttpServletRequest req) {
    trust.requireFlag("deeplinks.simulator",cookies.requireUser(req).id());
    return new Profiles(List.copyOf(DeeplinkSimulator.PROFILES.keySet()));
  }

  @PostMapping(value = "/simulate", consumes = MediaType.APPLICATION_JSON_VALUE)
  DeeplinkSimulator.Result simulate(@RequestBody SimulateRequest r, HttpServletRequest req) {
    trust.requireFlag("deeplinks.simulator",cookies.requireUser(req).id());
    return simulator.simulate(r.url(), r.profile());
  }
}
