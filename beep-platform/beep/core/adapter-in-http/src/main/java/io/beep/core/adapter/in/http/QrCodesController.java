package io.beep.core.adapter.in.http;

import io.beep.core.app.analytics.GetStatsUseCase;
import io.beep.core.app.common.PublicSite;
import io.beep.core.app.port.AnalyticsRepository.Resource;
import io.beep.core.app.qr.QrCodesUseCase;
import io.beep.core.domain.qr.QrCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** QR codes (/v1/qr-codes) : dynamiques (suivent un lien, scans mesurés) et statiques ; export SVG, PNG, PDF. */
@RestController
@RequestMapping("/v1/qr-codes")
@Profile("!worker & !migrate")
public class QrCodesController {
  record QrRequest(String kind, UUID linkId, String contentType, Map<String, String> fields, String foreground, String background, String errorCorrection,
      Integer margin, String title) {}

  record QrPage(List<Views.QrView> items, String nextCursor) {}

  private final QrCodesUseCase qr;
  private final GetStatsUseCase stats;
  private final SessionCookies cookies;
  private final PublicSite site;

  public QrCodesController(QrCodesUseCase qr, GetStatsUseCase stats, SessionCookies cookies, PublicSite site) {
    this.qr = qr;
    this.stats = stats;
    this.cookies = cookies;
    this.site = site;
  }

  @GetMapping
  QrPage list(@RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) String cursor, HttpServletRequest req) {
    var page = qr.list(cookies.requireUser(req), limit, cursor);
    return new QrPage(page.items().stream().map(i -> Views.qr(i.qr(), i.encoded(), i.scans30d())).toList(), page.nextCursor());
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Views.QrView> create(@RequestBody QrRequest r, HttpServletRequest req) {
    QrCode created = qr.create(cookies.requireUser(req), command(r));
    return ResponseEntity.status(HttpStatus.CREATED).body(Views.qr(created, created.encodedValue(site.url()), 0));
  }

  @GetMapping("/{id}")
  Views.QrView get(@PathVariable UUID id, HttpServletRequest req) {
    var item = qr.get(cookies.requireUser(req), id);
    return Views.qr(item.qr(), item.encoded(), item.scans30d());
  }

  @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
  Views.QrView update(@PathVariable UUID id, @RequestBody QrRequest r, HttpServletRequest req) {
    QrCode updated = qr.update(cookies.requireUser(req), id, command(r));
    return Views.qr(updated, updated.encodedValue(site.url()), 0);
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest req) {
    qr.delete(cookies.requireUser(req), id);
    return ResponseEntity.noContent().build();
  }

  /** `download=1` : en pièce jointe ; sinon affichable (aperçu dans l'espace client). */
  @GetMapping("/{id}/render")
  ResponseEntity<byte[]> render(@PathVariable UUID id, @RequestParam(defaultValue = "svg") String format, @RequestParam(required = false) Integer size,
      @RequestParam(defaultValue = "0") int download, HttpServletRequest req) {
    var r = qr.render(cookies.requireUser(req), id, format, size);
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(r.mediaType()))
        .header(HttpHeaders.CONTENT_DISPOSITION, (download == 1 ? "attachment" : "inline") + "; filename=\"" + r.filename() + "\"")
        .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
        .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
        .body(r.bytes());
  }

  @GetMapping("/{id}/stats")
  Views.StatsResponse stats(@PathVariable UUID id, @RequestParam(defaultValue = "30") int days, HttpServletRequest req) {
    return Views.stats(stats.execute(cookies.requireUser(req), new GetStatsUseCase.Query(Resource.QR, id, days)));
  }

  private static QrCodesUseCase.Command command(QrRequest r) {
    return new QrCodesUseCase.Command(r.kind(), r.linkId(), r.contentType(), r.fields(), r.foreground(), r.background(), r.errorCorrection(), r.margin(), r.title());
  }
}
