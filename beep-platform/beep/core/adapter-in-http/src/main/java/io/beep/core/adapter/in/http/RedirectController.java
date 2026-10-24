package io.beep.core.adapter.in.http;

import io.beep.core.app.analytics.Visit;
import io.beep.core.app.common.PublicSite;
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

  public RedirectController(ResolveSlugUseCase resolveSlug, ResolveQrUseCase resolveQr, BioBlockClickUseCase bioClick, PublicSite site, Clock clock) {
    this.resolveSlug = resolveSlug;
    this.resolveQr = resolveQr;
    this.bioClick = bioClick;
    this.pages = new PublicPages(site);
    this.clock = clock;
  }

  @GetMapping("/{slug:[A-Za-z0-9][A-Za-z0-9_-]{0,63}}")
  ResponseEntity<?> slug(@PathVariable String slug, HttpServletRequest req) {
    return respond(resolveSlug.execute(slug, visit(req)), req);
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
      case Outcome.ShowBio b -> html(HttpStatus.OK, pages.bio(b.page()), CacheControl.maxAge(java.time.Duration.ofSeconds(30)).cachePublic());
      case Outcome.Unavailable u -> html(HttpStatus.GONE, pages.unavailable(lang), CacheControl.noStore());
      case Outcome.NotFound n -> html(HttpStatus.NOT_FOUND, pages.notFound(lang), CacheControl.noStore());
    };
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
