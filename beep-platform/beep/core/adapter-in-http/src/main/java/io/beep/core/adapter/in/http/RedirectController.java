package io.beep.core.adapter.in.http;

import io.beep.core.app.analytics.Visit;
import io.beep.core.app.common.PublicSite;
import io.beep.core.app.port.DomainRouterPort;
import io.beep.core.app.redirect.BioBlockClickUseCase;
import io.beep.core.app.redirect.Outcome;
import io.beep.core.app.redirect.ResolveQrUseCase;
import io.beep.core.app.redirect.ResolveSlugUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chemin critique : `/{slug}` (lien court ou page bio), `/q/{code}` (QR dynamique), `/_b/c/{page}/{bloc}` (clic de bloc bio).
 * Threads virtuels, résolution en cache mémoire, événement asynchrone : aucun appel réseau externe pendant la requête.
 * HEAD et préchargements navigateur répondent normalement mais ne sont pas comptés.
 */
@RestController
@Profile("!worker & !migrate")
public class RedirectController {
  private final ResolveSlugUseCase resolveSlug;
  private final ResolveQrUseCase resolveQr;
  private final BioBlockClickUseCase bioClick;
  private final PublicPages pages;
  private final Clock clock;
  private final DomainRouterPort router;

  public RedirectController(ResolveSlugUseCase resolveSlug, ResolveQrUseCase resolveQr, BioBlockClickUseCase bioClick, PublicSite site, Clock clock,
      DomainRouterPort router) {
    this.router = router;
    this.resolveSlug = resolveSlug;
    this.resolveQr = resolveQr;
    this.bioClick = bioClick;
    this.pages = new PublicPages(site);
    this.clock = clock;
  }

  @GetMapping("/{slug:[A-Za-z0-9][A-Za-z0-9_-]{0,63}}")
  ResponseEntity<?> slug(@PathVariable String slug, HttpServletRequest req) {
    DomainRouterPort.Route route = router.route(req.getServerName());
    return switch (route.kind()) {
      case MAIN -> respond(resolveSlug.execute(slug, visit(req)), req);
      case UNKNOWN -> respond(Outcome.NOT_FOUND, req);
      case CUSTOM -> {
        if (route.blocked()) yield respond(Outcome.UNAVAILABLE, req);
        if (!route.servable()) yield respond(Outcome.NOT_FOUND, req);
        Outcome outcome = resolveSlug.execute(slug, route.domainId(), visit(req));
        yield outcome instanceof Outcome.NotFound && route.notFoundUrl() != null ? redirect(route.notFoundUrl()) : respond(outcome, req);
      }
    };
  }

  /** Racine d'un domaine personnalisé : redirection choisie par le client, sinon la page 404 de Beepclick. (Sur le domaine de Beepclick, `/` appartient au site.) */
  @GetMapping("/")
  ResponseEntity<?> root(HttpServletRequest req) {
    DomainRouterPort.Route route = router.route(req.getServerName());
    if (route.kind() == DomainRouterPort.Route.Kind.CUSTOM && route.servable() && route.rootRedirectUrl() != null) return redirect(route.rootRedirectUrl());
    return respond(route.kind() == DomainRouterPort.Route.Kind.CUSTOM && route.blocked() ? Outcome.UNAVAILABLE : Outcome.NOT_FOUND, req);
  }

  private static ResponseEntity<?> redirect(String url) {
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex").build();
  }

  @GetMapping("/q/{code}")
  ResponseEntity<?> qr(@PathVariable String code, HttpServletRequest req) {
    return respond(resolveQr.execute(code, visit(req)), req);
  }

  @GetMapping("/_b/c/{pageId}/{blockId}")
  ResponseEntity<?> bioBlock(@PathVariable String pageId, @PathVariable String blockId, HttpServletRequest req) {
    UUID id;
    try {
      id = UUID.fromString(pageId);
    } catch (IllegalArgumentException e) {
      return respond(Outcome.NOT_FOUND, req);
    }
    return respond(bioClick.execute(id, blockId, visit(req)), req);
  }

  private ResponseEntity<?> respond(Outcome outcome, HttpServletRequest req) {
    String lang = PublicPages.language(req.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
    return switch (outcome) {
      case Outcome.Redirect r -> ResponseEntity.status(HttpStatus.FOUND).location(URI.create(r.location()))
          .cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex").header("Referrer-Policy", "strict-origin-when-cross-origin").build();
      case Outcome.ShowBio b -> html(HttpStatus.OK, pages.bio(b.page(),b.showBadge()), CacheControl.maxAge(java.time.Duration.ofSeconds(30)).cachePublic());
      case Outcome.Unavailable u -> html(HttpStatus.GONE, pages.unavailable(lang), CacheControl.noStore());
      case Outcome.Expired e -> html(HttpStatus.GONE, pages.expired(lang), CacheControl.noStore());
      case Outcome.PasswordRequired p -> html(p.wrong() ? HttpStatus.UNAUTHORIZED : HttpStatus.OK, pages.password(lang, p.wrong()), CacheControl.noStore());
      case Outcome.Deeplink d -> deeplink(d, lang);
      case Outcome.NotFound n -> html(HttpStatus.NOT_FOUND, pages.notFound(lang), CacheControl.noStore());
    };
  }

  private ResponseEntity<?> deeplink(Outcome.Deeplink d, String lang) {
    if (d.mode() == io.beep.core.domain.link.DeeplinkDecision.Mode.ANDROID_INTENT) {
      return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(d.plan().androidUrl())).cacheControl(CacheControl.noStore()).header("X-Robots-Tag", "noindex").build();
    }
    byte[] nonceBytes = new byte[16];
    new java.security.SecureRandom().nextBytes(nonceBytes);
    String nonce = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
    return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8)).cacheControl(CacheControl.noStore())
        .header("Content-Security-Policy", PublicPages.openAppCsp(nonce)).header("X-Content-Type-Options", "nosniff").header("Referrer-Policy", "no-referrer")
        .body(pages.openApp(lang, d, nonce).getBytes(StandardCharsets.UTF_8));
  }

  /** Mot de passe d'un lien protégé : formulaire POST sur la même adresse. */
  @PostMapping(value = "/{slug:[A-Za-z0-9][A-Za-z0-9_-]{0,63}}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  ResponseEntity<?> unlock(@PathVariable String slug, @RequestParam(value = "password", required = false) String password, HttpServletRequest req) {
    DomainRouterPort.Route route = router.route(req.getServerName());
    java.util.UUID domainId = route.kind() == DomainRouterPort.Route.Kind.CUSTOM ? route.domainId() : null;
    if (route.kind() == DomainRouterPort.Route.Kind.UNKNOWN || (route.kind() == DomainRouterPort.Route.Kind.CUSTOM && !route.servable())) return respond(Outcome.NOT_FOUND, req);
    return respond(resolveSlug.unlock(slug, domainId, password, visit(req)), req);
  }

  @PostMapping(value = "/q/{code}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  ResponseEntity<?> unlockQr(@PathVariable String code, @RequestParam(value = "password", required = false) String password, HttpServletRequest req) {
    return respond(resolveQr.unlock(code, password, visit(req)), req);
  }

  private static ResponseEntity<byte[]> html(HttpStatus status, String body, CacheControl cache) {
    return ResponseEntity.status(status).contentType(new MediaType("text", "html", StandardCharsets.UTF_8)).cacheControl(cache)
        .header("Content-Security-Policy", PublicPages.CSP).header("X-Content-Type-Options", "nosniff")
        .body(body.getBytes(StandardCharsets.UTF_8));
  }

  private Visit visit(HttpServletRequest req) {
    boolean prefetch = "prefetch".equalsIgnoreCase(req.getHeader("Purpose")) || String.valueOf(req.getHeader("Sec-Purpose")).startsWith("prefetch")
        || "prefetch".equalsIgnoreCase(req.getHeader("X-Moz"));
    return new Visit(req.getRemoteAddr(), req.getHeader(HttpHeaders.USER_AGENT), req.getHeader(HttpHeaders.REFERER), req.getHeader(HttpHeaders.ACCEPT_LANGUAGE),
        req.getQueryString(), "GET".equals(req.getMethod()) && !prefetch, clock.instant());
  }
}
